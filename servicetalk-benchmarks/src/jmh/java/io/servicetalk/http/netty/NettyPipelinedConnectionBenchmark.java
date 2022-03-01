/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.FlushStrategies;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.GlobalExecutionContext;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.WriteDemandEstimator;

import io.netty.channel.Channel;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class NettyPipelinedConnectionBenchmark {
    static {
        AsyncContext.disable(); // reduce noise in benchmarks.
    }

    private static final int EXECUTOR_STACK_PROTECT_MASK = 255;
    private ExecutorService executorService;
    private NettyPipelinedConnection<Object, Object> pipelinedConnection;

    @Setup(Level.Trial)
    public void setup() throws ExecutionException, InterruptedException {
        executorService = Executors.newCachedThreadPool();
        pipelinedConnection = new NettyPipelinedConnection<>(newNettyConnection(), 32);
        prewarmExecutorThreads(executorService, 5);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        executorService.shutdown();
    }

    @Benchmark
    public void writeAndRead1() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        subscribeCountDownOnTerminate(toSource(pipelinedConnection.write(Publisher.empty())), latch);
        latch.await();
    }

    @Benchmark
    public void writeAndRead10() throws InterruptedException {
        final int totalRequests = 10;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        for (int i = 0; i < totalRequests; ++i) {
            subscribeCountDownOnTerminate(toSource(pipelinedConnection.write(Publisher.empty())), latch);
        }
        latch.await();
    }

    private static void prewarmExecutorThreads(ExecutorService executor, int executorPrewarmSize)
            throws ExecutionException, InterruptedException {
        List<Future<?>> futures = new ArrayList<>(executorPrewarmSize);
        CyclicBarrier cyclicBarrier = new CyclicBarrier(executorPrewarmSize);
        for (int i = 0; i < executorPrewarmSize; ++i) {
            futures.add(executor.submit(() -> {
                try {
                    cyclicBarrier.await();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
    }

    // Avoid using operators to keep benchmark as focused as possible on the unit under test.
    private static void subscribeCountDownOnTerminate(PublisherSource<Object> publisherSource, CountDownLatch latch) {
        publisherSource.subscribe(new PublisherSource.Subscriber<Object>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final Object o) {
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
    }

    private NettyConnection<Object, Object> newNettyConnection() {
        return new NettyConnection<Object, Object>() {
            private final AtomicInteger offloadCount = new AtomicInteger();
            @Override
            public Publisher<Object> read() {
                return Publisher.empty();
            }

            @Override
            public Completable write(final Publisher<Object> write) {
                return new Completable() {
                    @Override
                    protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                        // Avoid using operators to keep benchmark as focused as possible on the unit under test.
                        toSource(write).subscribe(new PublisherSource.Subscriber<Object>() {
                            @Override
                            public void onSubscribe(final PublisherSource.Subscription subscription) {
                                subscription.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(@Nullable final Object o) {
                            }

                            @Override
                            public void onError(final Throwable t) {
                                subscriber.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                // Avoid stack-overflow by periodically offloading the completion notification.
                                if ((offloadCount.incrementAndGet() & EXECUTOR_STACK_PROTECT_MASK) == 0) {
                                    executorService.execute(subscriber::onComplete);
                                } else {
                                    subscriber.onComplete();
                                }
                            }
                        });
                    }
                };
            }

            @Override
            public Completable write(final Publisher<Object> write,
                                     final Supplier<FlushStrategy> flushStrategySupplier,
                                     final Supplier<WriteDemandEstimator> demandEstimatorSupplier) {
                return write(write);
            }

            @Override
            public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
                return IGNORE_CANCEL;
            }

            @Override
            public FlushStrategy defaultFlushStrategy() {
                return FlushStrategies.defaultFlushStrategy();
            }

            @Override
            public Single<Throwable> transportError() {
                return Single.never();
            }

            @Override
            public Completable onClosing() {
                return Completable.never();
            }

            @Override
            public Channel nettyChannel() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SocketAddress localAddress() {
                return new InetSocketAddress(0);
            }

            @Override
            public SocketAddress remoteAddress() {
                return new InetSocketAddress(0);
            }

            @Nullable
            @Override
            public SSLSession sslSession() {
                return null;
            }

            @Override
            public ExecutionContext<?> executionContext() {
                return GlobalExecutionContext.globalExecutionContext();
            }

            @Nullable
            @Override
            public <T> T socketOption(final SocketOption<T> option) {
                return null;
            }

            @Override
            public Protocol protocol() {
                return HttpProtocolVersion.HTTP_1_1;
            }

            @Override
            public Completable onClose() {
                return Completable.never();
            }

            @Override
            public Completable closeAsync() {
                return Completable.never();
            }
        };
    }
}
