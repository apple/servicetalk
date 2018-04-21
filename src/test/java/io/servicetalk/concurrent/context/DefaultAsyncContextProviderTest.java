/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.context;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.context.AsyncContextMap.Key;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.context.DefaultAsyncContextProvider.INSTANCE;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DefaultAsyncContextProviderTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static final Key<String> K1 = Key.newKeyWithDebugToString("k1");
    private static final Key<String> K2 = Key.newKeyWithDebugToString("k2");

    private static ScheduledExecutorService executor;

    @BeforeClass
    public static void beforeClass() {
        ConcurrentPlugins.install();
        executor = Executors.newScheduledThreadPool(4);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Before
    public void setUp() {
        AsyncContext.clear();
    }

    private static void completeOnExecutor(Completable.Subscriber subscriber) {
        executor.execute(() -> {
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onComplete();
        });
    }

    private static <T> void completeOnExecutor(Single.Subscriber<? super T> subscriber, T value) {
        executor.execute(() -> {
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onSuccess(value);
        });
    }

    @Test
    public void testContextInCompletableListener() throws Exception {
        Completable completable = new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                completeOnExecutor(completableSubscriber);
            }
        };

        AsyncContext.put(K1, "v1");
        new ContextCaptureCompletableSubscriber()
                .subscribeAndWait(completable)
                .verifyContext(map -> {
                    Assert.assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        // Each subscribe/subscribe gets its own context
        AsyncContext.put(K2, "v2");
        new ContextCaptureCompletableSubscriber()
                .subscribeAndWait(completable)
                .verifyContext(map -> {
                    Assert.assertEquals("v1", map.get(K1));
                    Assert.assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testContextInCompletableOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();

        Completable completable = new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                f1.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(completableSubscriber);
            }
        }.merge(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                f2.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(completableSubscriber);
            }
        }).doBeforeFinally(() -> f3.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        awaitIndefinitely(completable);

        assertEquals("v1", f1.get().get(K1));
        assertEquals("v1", f2.get().get(K1));
        assertEquals("v1", f3.get().get(K1));
        assertEquals("v1", AsyncContext.get(K1));

        assertNull(f1.get().get(K2));
        assertNull(f2.get().get(K2));
        assertNull(f3.get().get(K2));
        assertNull(AsyncContext.get(K2));
    }

    @Test
    public void testContextInSingleListener() throws Exception {
        Single<String> single = new Single<String>() {
            @Override
            protected void handleSubscribe(Subscriber<? super String> singleSubscriber) {
                completeOnExecutor(singleSubscriber, "a");
            }
        };

        AsyncContext.put(K1, "v1");
        new ContextCaptureSingleSubscriber<String>()
                .subscribeAndWait(single)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        // Each subscribe/subscribe gets its own context
        AsyncContext.put(K2, "v2");
        new ContextCaptureSingleSubscriber<String>()
                .subscribeAndWait(single)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testContextInSingleOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f4 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f5 = new CompletableFuture<>();

        Single<String> single = new Single<String>() {
            @Override
            protected void handleSubscribe(Subscriber<? super String> singleSubscriber) {
                f1.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(singleSubscriber, "a");
            }
        }.map(v -> {
            f2.complete(AsyncContext.current());
            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).flatmap(v -> {
            f3.complete(AsyncContext.current());
            AsyncContext.put(K2, "v2"); // this will apply to the Single(b) created here
            return new Single<String>() {
                @Override
                protected void handleSubscribe(Subscriber<? super String> singleSubscriber) {
                    f4.complete(AsyncContext.current());
                    completeOnExecutor(singleSubscriber, "b");
                }
            };
        }).doBeforeFinally(() -> f5.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        awaitIndefinitely(single);

        assertEquals("v1", f1.get().get(K1));
        assertEquals("v1", f2.get().get(K1));
        assertEquals("v1", f3.get().get(K1));
        assertEquals("v1", f4.get().get(K1));
        assertEquals("v1", f5.get().get(K1));
        assertEquals("v1", AsyncContext.get(K1));

        assertNull(f1.get().get(K2));
        assertNull(f2.get().get(K2));
        assertNull(f3.get().get(K2));
        assertEquals("v2", f4.get().get(K2));
        assertNull(f5.get().get(K2));
        assertNull(AsyncContext.get(K2));
    }

    @Test
    public void testContextInSingleConversions() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();

        Completable completable = new Single<String>() {
            @Override
            protected void handleSubscribe(Subscriber<? super String> singleSubscriber) {
                f1.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(singleSubscriber, "a");
            }
        }.ignoreResult().merge(new Completable() {
            @Override
            protected void handleSubscribe(Subscriber completableSubscriber) {
                f2.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(completableSubscriber);
            }
        }).doBeforeFinally(() -> f3.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        awaitIndefinitely(completable);

        assertEquals("v1", f1.get().get(K1));
        assertEquals("v1", f2.get().get(K1));
        assertEquals("v1", f3.get().get(K1));

        assertNull(f1.get().get(K2));
        assertNull(f2.get().get(K2));
        assertNull(f3.get().get(K2));
    }

    @Test
    public void testContextInSubscriber() throws Exception {
        ContextCaptureTestPublisher publisher1 = new ContextCaptureTestPublisher();
        AsyncContext.put(K1, "v1");
        new ContextCaptureSubscriber<String>()
                .subscribeAndWait(publisher1)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });
        publisher1.verifySubscriptionContext(map -> {
            assertEquals("v1", map.get(K1));
            assertNull(map.get(K2));
        });

        // Each subscribe/subscribe gets its own context
        ContextCaptureTestPublisher publisher2 = new ContextCaptureTestPublisher();
        AsyncContext.put(K2, "v2");
        new ContextCaptureSubscriber<String>()
                .subscribeAndWait(publisher2)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
        publisher2.verifySubscriptionContext(map -> {
            assertEquals("v1", map.get(K1));
            assertEquals("v2", map.get(K2));
        });
    }

    @Test
    public void testContextInPublisherOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f4 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f5 = new CompletableFuture<>();

        Single<StringBuilder> single = new ContextCaptureTestPublisher().map(v -> {
            f1.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).concatWith(new ContextCaptureTestPublisher().filter(v -> {
            f2.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return true;
        })).map(v -> {
            f3.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).doBeforeComplete(() -> {
            f4.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
        }).reduce(StringBuilder::new, StringBuilder::append).doBeforeFinally(() -> f5.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        awaitIndefinitely(single);

        assertEquals("v1", f1.get().get(K1));
        assertEquals("v1", f2.get().get(K1));
        assertEquals("v1", f3.get().get(K1));
        assertEquals("v1", f4.get().get(K1));
        assertEquals("v1", f5.get().get(K1));
        assertEquals("v1", AsyncContext.get(K1));

        assertNull(AsyncContext.get(K2));

        // TODO we don't have nested operators such as flatMap yet, to be tested when we implement them
    }

    @Test
    public void testWrapExecutor() throws Exception {
        AsyncContext.put(K1, "v1");
        Consumer<AsyncContextMap> verifier = map -> {
            assertEquals("v1", map.get(K1));
            assertNull(map.get(K2));
        };

        new ContextCaptureRunnable()
                .runAndWait(INSTANCE.wrap((Executor) executor))
                .verifyContext(verifier);

        new ContextCaptureRunnable()
                .runAndWait(INSTANCE.wrap((ExecutorService) executor))
                .verifyContext(verifier);

        new ContextCaptureCallable<String>()
                .runAndWait(INSTANCE.wrap((ExecutorService) executor))
                .verifyContext(verifier);

        new ContextCaptureCallable<String>()
                .scheduleAndWait(INSTANCE.wrap(executor))
                .verifyContext(verifier);
    }

    @Test
    public void testWrapFunctions() throws Exception {
        AsyncContext.put(K1, "v1");
        Consumer<AsyncContextMap> verifier = map -> {
            assertEquals("v1", map.get(K1));
            assertNull(map.get(K2));
        };

        new ContextCapturer()
                .runAndWait(collector -> {
                    Function<Void, Void> f = INSTANCE.wrap(v -> {
                        collector.complete(AsyncContext.current());
                        return v;
                    });
                    executor.execute(() -> f.apply(null));
                })
                .verifyContext(verifier);

        new ContextCapturer()
                .runAndWait(collector -> {
                    Consumer<Void> c = INSTANCE.wrap((Consumer<Void>) v -> collector.complete(AsyncContext.current()));
                    executor.execute(() -> c.accept(null));
                })
                .verifyContext(verifier);

        new ContextCapturer()
                .runAndWait(collector -> {
                    BiFunction<Void, Void, Void> bf = INSTANCE.wrap((v1, v2) -> {
                        collector.complete(AsyncContext.current());
                        return v1;
                    });
                    executor.execute(() -> bf.apply(null, null));
                })
                .verifyContext(verifier);

        new ContextCapturer()
                .runAndWait(collector -> {
                    BiConsumer<Void, Void> bc = INSTANCE.wrap((v1, v2) -> {
                        collector.complete(AsyncContext.current());
                    });
                    executor.execute(() -> bc.accept(null, null));
                })
                .verifyContext(verifier);
    }

    private static class ContextCaptureCompletableSubscriber implements Completable.Subscriber {
        final CountDownLatch latch = new CountDownLatch(2);

        @Nullable
        AsyncContextMap onTerminateContext;
        @Nullable
        AsyncContextMap onSubscribeContext;

        @Override
        public void onSubscribe(Cancellable cancellable) {
            onSubscribeContext = AsyncContext.current();
            latch.countDown();
        }

        @Override
        public void onComplete() {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }

        ContextCaptureCompletableSubscriber subscribeAndWait(Completable completable) throws InterruptedException {
            completable.subscribe(this);
            latch.await();
            return this;
        }

        ContextCaptureCompletableSubscriber verifyContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(onSubscribeContext);
            consumer.accept(onTerminateContext);
            return this;
        }
    }

    private static class ContextCaptureSingleSubscriber<T> implements Single.Subscriber<T> {
        final CountDownLatch latch = new CountDownLatch(2);

        @Nullable
        AsyncContextMap onTerminateContext;
        @Nullable
        AsyncContextMap onSubscribeContext;

        @Override
        public void onSubscribe(Cancellable cancellable) {
            onSubscribeContext = AsyncContext.current();
            latch.countDown();
        }

        @Override
        public void onSuccess(@Nullable T result) {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }

        ContextCaptureSingleSubscriber<T> subscribeAndWait(Single<T> single) throws InterruptedException {
            single.subscribe(this);
            latch.await();
            return this;
        }

        ContextCaptureSingleSubscriber<T> verifyContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(onSubscribeContext);
            consumer.accept(onTerminateContext);
            return this;
        }
    }

    private static class ContextCaptureTestPublisher extends Publisher<String> {
        final List<AsyncContextMap> requestNContexts = new ArrayList<>();
        @Nullable
        AsyncContextMap cancelContext;

        ContextCaptureTestPublisher() {
            super(immediate());
        }

        @Override
        protected void handleSubscribe(org.reactivestreams.Subscriber s) {
            // Introduce some asynchrony here and there
            executor.execute(() -> s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    assert n >= 2 : "This test requires request(n >= 2)";
                    requestNContexts.add(AsyncContext.current());

                    s.onNext("1");
                    executor.execute(() -> {
                        s.onNext("2");
                        executor.execute(s::onComplete);
                    });
                }

                @Override
                public void cancel() {
                    cancelContext = AsyncContext.current();
                }
            }));
        }

        ContextCaptureTestPublisher verifySubscriptionContext(Consumer<AsyncContextMap> consumer) {
            requestNContexts.forEach(consumer);
            if (cancelContext != null) {
                consumer.accept(cancelContext);
            }
            return this;
        }
    }

    private static class ContextCaptureSubscriber<T> implements org.reactivestreams.Subscriber<T> {
        final CountDownLatch latch = new CountDownLatch(1);

        @Nullable
        AsyncContextMap onSubscribeContext;
        final List<AsyncContextMap> onNextContexts = new ArrayList<>();
        @Nullable
        AsyncContextMap onTerminateContext;

        @Override
        public void onSubscribe(Subscription s) {
            onSubscribeContext = AsyncContext.current();
            s.request(Long.MAX_VALUE); // this is acceptable for the tests we have right now
        }

        @Override
        public void onNext(T t) {
            onNextContexts.add(AsyncContext.current());
        }

        @Override
        public void onError(Throwable t) {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }

        @Override
        public void onComplete() {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }

        ContextCaptureSubscriber<T> subscribeAndWait(Publisher<T> publisher) throws InterruptedException {
            publisher.subscribe(this);
            latch.await();
            return this;
        }

        ContextCaptureSubscriber<T> verifyContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(onSubscribeContext);
            onNextContexts.forEach(consumer);
            consumer.accept(onTerminateContext);
            return this;
        }
    }

    private static class ContextCaptureRunnable implements Runnable {
        final CompletableFuture<AsyncContextMap> mapFuture = new CompletableFuture<>();

        @Override
        public void run() {
            mapFuture.complete(AsyncContext.current());
        }

        ContextCaptureRunnable runAndWait(Executor executor) throws ExecutionException, InterruptedException {
            executor.execute(this);
            mapFuture.get();
            return this;
        }

        ContextCaptureRunnable runAndWait(ExecutorService executor) throws ExecutionException, InterruptedException {
            executor.execute(this);
            mapFuture.get();
            return this;
        }

        ContextCaptureRunnable verifyContext(Consumer<AsyncContextMap> consumer) throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture.get());
            return this;
        }
    }

    private static class ContextCaptureCallable<T> implements Callable<T> {
        final CompletableFuture<AsyncContextMap> mapFuture = new CompletableFuture<>();

        @Override
        public T call() {
            mapFuture.complete(AsyncContext.current());
            return null;
        }

        ContextCaptureCallable<T> runAndWait(ExecutorService executor) throws ExecutionException, InterruptedException {
            executor.submit(this);
            mapFuture.get();
            return this;
        }

        ContextCaptureCallable<T> scheduleAndWait(ScheduledExecutorService executor) throws ExecutionException, InterruptedException {
            executor.schedule(this, 10, TimeUnit.MILLISECONDS);
            mapFuture.get();
            return this;
        }

        ContextCaptureCallable<T> verifyContext(Consumer<AsyncContextMap> consumer) throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture.get());
            return this;
        }
    }

    private static class ContextCapturer {
        final CompletableFuture<AsyncContextMap> mapFuture = new CompletableFuture<>();

        ContextCapturer runAndWait(Consumer<CompletableFuture<AsyncContextMap>> consumer) throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture);
            mapFuture.get();
            return this;
        }

        ContextCapturer verifyContext(Consumer<AsyncContextMap> consumer) throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture.get());
            return this;
        }
    }
}
