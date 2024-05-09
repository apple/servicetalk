package io.servicetalk.dns.discovery.netty;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.lang.Math.max;
import static java.lang.Math.min;

final class HedgingDnsNameResolver implements Closeable {

    private final DnsNameResolver delegate;
    private final EventLoop eventLoop;
    private final PercentileTracker percentile;
    private final Budget budget;

    HedgingDnsNameResolver(DnsNameResolver delegate, EventLoop eventLoop) {
        this(delegate, eventLoop, defaultTracker(), defaultBudget());
    }

    HedgingDnsNameResolver(DnsNameResolver delegate, EventLoop eventLoop, PercentileTracker percentile,
                   Budget budget) {
        this.delegate = delegate;
        this.eventLoop = eventLoop;
        this.percentile = percentile;
        this.budget = budget;
    }

    public Future<List<DnsRecord>> resolveAll(DnsQuestion t) {
        return applyHedge(delegate::resolveAll, t);
    }

    public Future<List<InetAddress>> resolveAll(String t) {
        return applyHedge(delegate::resolveAll, t);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private <T, R> Future<R> applyHedge(Function<T, Future<R>> computation, T t) {
        // Only add tokens for organic requests and not retries.
        budget.deposit();
        Future<R> underlyingResult = computation.apply(t);
        final long startTime = System.currentTimeMillis();
        final long deadline = addWithOverflowProtection(startTime, percentile.getValue());
        if (deadline == Long.MAX_VALUE) {
            // no need to attempt a hedge that will wait that long: just return the value.
            return underlyingResult;
        } else {
            Promise<R> promise = eventLoop.newPromise();
            Future<?> hedgeTimer = eventLoop.schedule(() -> maybeApplyHedge(computation, t, underlyingResult, promise),
                    deadline, TimeUnit.MILLISECONDS);
            underlyingResult.addListener(completedFuture -> {
                measureRequest(System.currentTimeMillis() - startTime, completedFuture.isSuccess());
                if (complete(underlyingResult, promise)) {
                    hedgeTimer.cancel(true);
                }
            });
            return promise;
        }
    }

    private <T, R> void maybeApplyHedge(
            Function<T, Future<R>> computation, T t, Future<R> original, Promise<R> promise) {
        if (budget.withdraw() && !original.isDone()) {
            Future<R> backupResult = computation.apply(t);
            backupResult.addListener(done -> {
                if (complete(backupResult, promise)) {
                    original.cancel(true);
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

    private interface PercentileTracker {
        void addSample(long sample);

        long getValue();
    }

    private interface Budget {
        void deposit();

        boolean withdraw();
    }

    // TODO: both these implementations rely on access being serialized by the netty event loop.
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

        public DefaultPercentileTracker(int buckets, double percentile, int sampleThreshold) {
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
            long targetCount = (long)(sampleCount * percentile);
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
}
