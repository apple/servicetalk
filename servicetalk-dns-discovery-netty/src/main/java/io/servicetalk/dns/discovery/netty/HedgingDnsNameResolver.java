/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.Executor;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import java.io.Closeable;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class HedgingDnsNameResolver implements Closeable {

    private final DnsResolverIface delegate;
    private final EventLoopAwareNettyIoExecutor executor;

//    private final EventLoop eventLoop;
    private final PercentileTracker percentile;
    private final Budget budget;

    HedgingDnsNameResolver(DnsNameResolver delegate, IoExecutor executor) {
        this(new NettyDnsNameResolver(delegate), executor);
    }

    HedgingDnsNameResolver(DnsResolverIface delegate, IoExecutor executor) {
        this(delegate, executor, defaultTracker(), defaultBudget());
    }

    HedgingDnsNameResolver(DnsResolverIface delegate, IoExecutor executor,
                           PercentileTracker percentile, Budget budget) {
        this.delegate = delegate;
        this.executor = toEventLoopAwareNettyIoExecutor(executor).next();
        this.percentile = percentile;
        this.budget = budget;
    }

    public Future<List<DnsRecord>> resolveAll(DnsQuestion t) {
        return setupHedge(delegate::resolveAllQuestion, t);
    }

    public Future<List<InetAddress>> resolveAll(String t) {
        return setupHedge(delegate::resolveAll, t);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private long currentTimeMillis() {
        return executor.currentTime(TimeUnit.MILLISECONDS);
    }

    private <T, R> Future<R> setupHedge(Function<T, Future<R>> computation, T t) {
        // Only add tokens for organic requests and not retries.
        budget.deposit();
        Future<R> underlyingResult = computation.apply(t);
        final long delay = percentile.getValue();
        if (delay == Long.MAX_VALUE) {
            // basically forever: just return the value.
            return underlyingResult;
        } else {
            final long startTimeMs = currentTimeMillis();
            Promise<R> promise = executor.eventLoopGroup().next().newPromise();
            Cancellable hedgeTimer = executor.schedule(() -> tryHedge(computation, t, underlyingResult, promise),
                    delay, TimeUnit.MILLISECONDS);
            underlyingResult.addListener(completedFuture -> {
                measureRequest(currentTimeMillis() - startTimeMs, completedFuture.isSuccess());
                if (complete(underlyingResult, promise)) {
                    hedgeTimer.cancel();
                }
            });
            return promise;
        }
    }

    private <T, R> void tryHedge(
            Function<T, Future<R>> computation, T t, Future<R> original, Promise<R> promise) {
        if (!original.isDone() && budget.withdraw()) {
            Future<R> backupResult = computation.apply(t);
            final long startTime = currentTimeMillis();
            backupResult.addListener(done -> {
                if (complete(backupResult, promise)) {
                    original.cancel(true);
                    measureRequest(currentTimeMillis() - startTime, done.isSuccess());
                }
            });
            promise.addListener(complete -> backupResult.cancel(true));
        }
    }

    private void measureRequest(long durationMs, boolean succeeded) {
        if (succeeded) {
            percentile.addSample(durationMs);
        }
    }

    private <T, R> boolean complete(Future<R> f, Promise<R> p) {
        assert f.isDone();
        if (f.isSuccess()) {
            return p.trySuccess(f.getNow());
        } else {
            return p.tryFailure(f.cause());
        }
    }

    interface PercentileTracker {
        void addSample(long sample);

        long getValue();
    }

    interface Budget {
        void deposit();

        boolean withdraw();
    }

    // TODO: both these implementations are un-synchronized and rely on netty using only a single event loop.
    private static final class DefaultBudgetImpl implements Budget {

        private final int depositAmount;
        private final int withDrawAmount;
        private final int maxTokens;
        private int tokens;

        DefaultBudgetImpl(int depositAmount, int withDrawAmount, int maxTokens) {
            this(depositAmount, withDrawAmount, maxTokens, 0);
        }

        DefaultBudgetImpl(int depositAmount, int withDrawAmount, int maxTokens, int initialTokens) {
            this.depositAmount = depositAmount;
            this.withDrawAmount = withDrawAmount;
            this.maxTokens = maxTokens;
            this.tokens = initialTokens;
        }

        @Override
        public void deposit() {
            tokens = max(maxTokens, tokens + depositAmount);
        }

        @Override
        public boolean withdraw() {
            if (tokens < withDrawAmount) {
                return false;
            } else {
                tokens -= withDrawAmount;
                return true;
            }
        }
    }

    // TODO: we shouldn't need to worry about concurrency if this is all happening in the same netty channel.
    private static final class DefaultPercentileTracker implements PercentileTracker {

        // TODO: we need to make the buckets grow exponentially to save space.
        private final int[] buckets;
        private final double percentile;
        private final int sampleThreshold;
        private long lastValue;
        private int sampleCount;

        DefaultPercentileTracker(int buckets, double percentile, int sampleThreshold) {
            if (percentile < 0 || percentile > 1) {
                throw new IllegalArgumentException("Unexpected percentile value: " + percentile);
            }
            this.buckets = new int[ensurePositive(buckets, "buckets")];
            this.percentile = percentile;
            this.sampleThreshold = ensurePositive(sampleThreshold, "sampleThreshold");
            lastValue = Long.MAX_VALUE;
        }

        @Override
        public void addSample(long value) {
            maybeSwap();
            int bucket = valueToBucket(value);
            buckets[bucket]++;
            sampleCount++;
        }

        @Override
        public long getValue() {
            maybeSwap();
            return lastValue;
        }

        private void maybeSwap() {
            if (shouldSwap()) {
                lastValue = compute();
            }
        }

        private boolean shouldSwap() {
            return sampleCount >= sampleThreshold;
        }

        private long compute() {
            long targetCount = (long) (sampleCount * percentile);
            sampleCount = 0;
            long result = -1;
            for (int i = 0; i < buckets.length; i++) {
                if (result != -1) {
                    targetCount -= buckets[i];
                    if (targetCount <= 0) {
                        result = bucketToValue(i);
                    }
                }
                buckets[i] = 0;
            }
            assert result != -1; // we should have found a bucket.
            return max(1, result);
        }

        private long bucketToValue(int bucket) {
            return bucket;
        }

        private int valueToBucket(long value) {
            return (int) max(0, min(buckets.length, value));
        }
    }

    private static PercentileTracker defaultTracker() {
        return new DefaultPercentileTracker(128, 0.98, 200);
    }

    private static Budget defaultBudget() {
        // 5% extra load and a max burst of 5 hedges.
        return new DefaultBudgetImpl(1, 20, 100);
    }

    // TODO: copied from servicetalk-loadbalancer.
    private static final class NormalizedTimeSourceExecutor extends DelegatingExecutor {

        private final long offsetNanos;

        NormalizedTimeSourceExecutor(final Executor delegate) {
            super(delegate);
            offsetNanos = delegate.currentTime(NANOSECONDS);
        }

        @Override
        public long currentTime(final TimeUnit unit) {
            final long elapsedNanos = delegate().currentTime(NANOSECONDS) - offsetNanos;
            return unit.convert(elapsedNanos, NANOSECONDS);
        }
    }

    interface DnsResolverIface extends Closeable {
        Future<List<DnsRecord>> resolveAllQuestion(DnsQuestion t);

        Future<List<InetAddress>> resolveAll(String t);

        @Override
        void close();
    }

    private static final class NettyDnsNameResolver implements DnsResolverIface {
        private final DnsNameResolver resolver;

        NettyDnsNameResolver(final DnsNameResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public Future<List<DnsRecord>> resolveAllQuestion(DnsQuestion t) {
            return resolver.resolveAll(t);
        }

        @Override
        public Future<List<InetAddress>> resolveAll(String t) {
            return resolver.resolveAll(t);
        }

        @Override
        public void close() {
            resolver.close();
        }
    }
}
