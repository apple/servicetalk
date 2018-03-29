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
package io.servicetalk.concurrent.context.rxjava;

import io.servicetalk.concurrent.context.AsyncContext;
import io.servicetalk.concurrent.context.AsyncContextMap;
import io.servicetalk.concurrent.context.AsyncContextMap.Key;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.plugins.RxJavaPlugins;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RxJavaContextHooksTest {
    private static final Key<String> K1 = Key.newKeyWithDebugToString("k1");
    private static final Key<String> K2 = Key.newKeyWithDebugToString("k2");

    private static ScheduledExecutorService executor;
    private static Observable<String> sharedObservable;
    private static Flowable<String> sharedFlowable;

    @BeforeClass
    public static void beforeClass() throws Exception {
        executor = Executors.newScheduledThreadPool(4);
        RxJavaContextHooks.install();
        sharedObservable = Observable.create(e -> {
            executor.execute(() -> {
                e.onNext("1");
                executor.execute(() -> {
                    e.onNext("2");
                    executor.execute(e::onComplete);
                });
            });
        });
        sharedFlowable = Flowable.unsafeCreate(s -> {
            executor.execute(() -> {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(long l) {
                        executor.execute(() -> {
                            s.onNext("1");
                            executor.execute(() -> {
                                s.onNext("2");
                                executor.execute(s::onComplete);
                            });
                        });
                    }

                    @Override
                    public void cancel() {
                    }
                });
            });
        });
    }

    @AfterClass
    public static void afterClass() throws Exception {
        RxJavaPlugins.reset();
        executor.shutdown();
    }

    @Before
    public void setUp() throws Exception {
        AsyncContext.clear();
    }

    @Test
    public void testCompletable() throws Exception {
        Completable completable = Completable.create(e -> executor.execute(e::onComplete));

        AsyncContext.put(K1, "v1");
        new ScalarContextCapturer()
                .subscribeAndWait(completable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new ScalarContextCapturer()
                .subscribeAndWait(completable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testContextInCompletableOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();

        Completable completable = RxJavaPlugins.onAssembly(new Completable() {
            @Override
            protected void subscribeActual(CompletableObserver s) {
                f1.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(s);
            }
        }).mergeWith(RxJavaPlugins.onAssembly(new Completable() {
            @Override
            protected void subscribeActual(CompletableObserver s) {
                f2.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(s);
            }
        })).doFinally(() -> f3.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        completable.blockingAwait();

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
    public void testSingle() throws Exception {
        Single<String> single = Single.create(e -> executor.execute(() -> e.onSuccess("1")));

        AsyncContext.put(K1, "v1");
        new ScalarContextCapturer()
                .subscribeAndWait(single)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new ScalarContextCapturer()
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

        Single<String> single = RxJavaPlugins.onAssembly(new Single<String>() {
            @Override
            protected void subscribeActual(SingleObserver<? super String> observer) {
                f1.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(observer, "a");
            }
        }).map(v -> {
            f2.complete(AsyncContext.current());
            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).flatMap(v -> {
            f3.complete(AsyncContext.current());
            AsyncContext.put(K2, "v2"); // this will apply to the Single(b) created here
            return RxJavaPlugins.onAssembly(new Single<String>() {
                @Override
                protected void subscribeActual(SingleObserver<? super String> observer) {
                    f4.complete(AsyncContext.current());
                    completeOnExecutor(observer, "b");
                }
            });
        }).doFinally(() -> f5.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        single.blockingGet();

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
    public void testMaybe() throws Exception {
        Maybe<String> maybe = Maybe.create(e -> executor.execute(() -> e.onSuccess("1")));

        AsyncContext.put(K1, "v1");
        new ScalarContextCapturer()
                .subscribeAndWait(maybe)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new ScalarContextCapturer()
                .subscribeAndWait(maybe)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testContextInMaybeOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f4 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f5 = new CompletableFuture<>();

        Maybe<String> maybe = RxJavaPlugins.onAssembly(new Maybe<String>() {
            @Override
            protected void subscribeActual(MaybeObserver<? super String> observer) {
                f1.complete(AsyncContext.current());
                AsyncContext.put(K2, "v2"); // this won't affect the operators below
                completeOnExecutor(observer, "a");
            }
        }).map(v -> {
            f2.complete(AsyncContext.current());
            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).flatMap(v -> {
            f3.complete(AsyncContext.current());
            AsyncContext.put(K2, "v2"); // this will apply to the Single(b) created here
            return RxJavaPlugins.onAssembly(new Maybe<String>() {
                @Override
                protected void subscribeActual(MaybeObserver<? super String> observer) {
                    f4.complete(AsyncContext.current());
                    completeOnExecutor(observer, "b");
                }
            });
        }).doFinally(() -> f5.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        maybe.blockingGet();

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
    public void testObservable() throws Exception {
        AsyncContext.put(K1, "v1");
        new StreamContextCapturer()
                .subscribeAndWait(sharedObservable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new StreamContextCapturer()
                .subscribeAndWait(sharedObservable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testContextInObservableOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f4 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f5 = new CompletableFuture<>();

        Single<StringBuilder> single = RxJavaPlugins.onAssembly(new ContextCaptureTestObservable()).map(v -> {
            f1.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).concatWith(RxJavaPlugins.onAssembly(new ContextCaptureTestObservable()).filter(v -> {
            f2.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return true;
        })).map(v -> {
            f3.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).doOnComplete(() -> {
            f4.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
        }).reduce(new StringBuilder(), StringBuilder::append).doFinally(() -> {
            f5.complete(AsyncContext.current());
        });

        AsyncContext.put(K1, "v1");
        single.blockingGet();

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
    public void testConnectableObservable() throws Exception {
        AsyncContext.put(K1, "v1");
        new StreamContextCapturer()
                .subscribeAndWait(sharedObservable.publish())
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new StreamContextCapturer()
                .subscribeAndWait(sharedObservable.publish())
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testFlowable() throws Exception {
        AsyncContext.put(K1, "v1");
        new StreamContextCapturer()
                .subscribeAndWait(sharedFlowable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new StreamContextCapturer()
                .subscribeAndWait(sharedFlowable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    @Test
    public void testContextInFlowableOperators() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f4 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f5 = new CompletableFuture<>();

        Single<StringBuilder> single = RxJavaPlugins.onAssembly(new ContextCaptureTestFlowable()).map(v -> {
            f1.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).concatWith(RxJavaPlugins.onAssembly(new ContextCaptureTestFlowable()).filter(v -> {
            f2.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return true;
        })).map(v -> {
            f3.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).doOnComplete(() -> {
            f4.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
        }).reduce(new StringBuilder(), StringBuilder::append).doFinally(() -> {
            f5.complete(AsyncContext.current());
        });

        AsyncContext.put(K1, "v1");
        single.blockingGet();

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
    public void testConnectableFlowable() throws Exception {
        AsyncContext.put(K1, "v1");
        new StreamContextCapturer()
                .subscribeAndWait(sharedFlowable.publish())
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        AsyncContext.put(K2, "v2");
        new StreamContextCapturer()
                .subscribeAndWait(sharedFlowable.publish())
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertEquals("v2", map.get(K2));
                });
    }

    private static void completeOnExecutor(CompletableObserver s) {
        executor.execute(() -> {
            s.onSubscribe(EmptyDisposable.INSTANCE);
            s.onComplete();
        });
    }

    private static <T> void completeOnExecutor(SingleObserver<? super T> observer, T value) {
        executor.execute(() -> {
            observer.onSubscribe(EmptyDisposable.INSTANCE);
            observer.onSuccess(value);
        });
    }

    private static <T> void completeOnExecutor(MaybeObserver<? super T> observer, T value) {
        executor.execute(() -> {
            observer.onSubscribe(EmptyDisposable.INSTANCE);
            observer.onSuccess(value);
        });
    }

    private static class ContextCaptureTestObservable extends Observable<String> {
        @Nullable
        AsyncContextMap beforeSubscribeContext;
        @Nullable
        AsyncContextMap afterSubscribeContext;
        @Nullable
        AsyncContextMap cancelContext;

        @Override
        protected void subscribeActual(Observer<? super String> s) {
            // Introduce some asynchrony here and there
            executor.execute(() -> {
                beforeSubscribeContext = AsyncContext.current();
                s.onSubscribe(new Disposable() {
                    private boolean isDisposed;

                    @Override
                    public void dispose() {
                        isDisposed = true;
                        cancelContext = AsyncContext.current();
                    }

                    @Override
                    public boolean isDisposed() {
                        return isDisposed;
                    }
                });

                afterSubscribeContext = AsyncContext.current();

                s.onNext("1");
                executor.execute(() -> {
                    s.onNext("2");
                    executor.execute(s::onComplete);
                });
            });
        }

        ContextCaptureTestObservable verifySubscriptionContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(beforeSubscribeContext);
            consumer.accept(afterSubscribeContext);
            if (cancelContext != null) {
                consumer.accept(cancelContext);
            }
            return this;
        }
    }

    private static class ContextCaptureTestFlowable extends Flowable<String> {
        final List<AsyncContextMap> requestNContexts = new ArrayList<>();
        @Nullable
        AsyncContextMap cancelContext;

        @Override
        protected void subscribeActual(Subscriber<? super String> s) {
            // Introduce some asynchrony here and there
            executor.execute(() -> {
                s.onSubscribe(new Subscription() {
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
                });
            });
        }

        ContextCaptureTestFlowable verifySubscriptionContext(Consumer<AsyncContextMap> consumer) {
            requestNContexts.forEach(consumer);
            if (cancelContext != null) {
                consumer.accept(cancelContext);
            }
            return this;
        }
    }

    private static final class ScalarContextCapturer {
        final CountDownLatch latch = new CountDownLatch(1);

        @Nullable
        AsyncContextMap onSubscribeContext;
        @Nullable
        AsyncContextMap onTerminateContext;

        ScalarContextCapturer subscribeAndWait(Completable completable) throws InterruptedException {
            completable.subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(Disposable d) {
                    onSubscribeContext = AsyncContext.current();
                }

                @Override
                public void onComplete() {
                    finish();
                }

                @Override
                public void onError(Throwable e) {
                    finish();
                }
            });
            latch.await();
            return this;
        }

        ScalarContextCapturer subscribeAndWait(Single<?> single) throws InterruptedException {
            single.subscribe(new SingleObserver<Object>() {
                @Override
                public void onSubscribe(Disposable d) {
                    onSubscribeContext = AsyncContext.current();
                }

                @Override
                public void onSuccess(Object o) {
                    finish();
                }

                @Override
                public void onError(Throwable e) {
                    finish();
                }
            });
            latch.await();
            return this;
        }

        ScalarContextCapturer subscribeAndWait(Maybe<?> maybe) throws InterruptedException {
            maybe.subscribe(new MaybeObserver<Object>() {
                @Override
                public void onSubscribe(Disposable d) {
                    onSubscribeContext = AsyncContext.current();
                }

                @Override
                public void onSuccess(Object o) {
                    finish();
                }

                @Override
                public void onError(Throwable e) {
                    finish();
                }

                @Override
                public void onComplete() {
                    finish();
                }
            });
            latch.await();
            return this;
        }

        ScalarContextCapturer verifyContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(onSubscribeContext);
            consumer.accept(onTerminateContext);
            return this;
        }

        private void finish() {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }
    }

    private static final class StreamContextCapturer {
        final CountDownLatch latch = new CountDownLatch(1);

        @Nullable
        AsyncContextMap onSubscribeContext;
        final List<AsyncContextMap> onNextContexts = new ArrayList<>();
        @Nullable
        AsyncContextMap onTerminateContext;

        StreamContextCapturer subscribeAndWait(Observable<?> observable) throws InterruptedException {
            observable.subscribe(new HelperObserver());
            latch.await();
            return this;
        }

        StreamContextCapturer subscribeAndWait(ConnectableObservable<?> observable) throws InterruptedException {
            observable.subscribe(new HelperObserver());
            observable.connect();
            latch.await();
            return this;
        }

        StreamContextCapturer subscribeAndWait(Flowable<?> flowable) throws InterruptedException {
            flowable.subscribe(new HelperSubscriber());
            latch.await();
            return this;
        }

        StreamContextCapturer subscribeAndWait(ConnectableFlowable<?> flowable) throws InterruptedException {
            flowable.subscribe(new HelperSubscriber());
            flowable.connect();
            latch.await();
            return this;
        }

        StreamContextCapturer verifyContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(onSubscribeContext);
            onNextContexts.forEach(consumer);
            consumer.accept(onTerminateContext);
            return this;
        }

        private class HelperObserver implements Observer<Object> {
            @Override
            public void onSubscribe(Disposable d) {
                onSubscribeContext = AsyncContext.current();
            }

            @Override
            public void onNext(Object o) {
                onNextContexts.add(AsyncContext.current());
            }

            @Override
            public void onError(Throwable e) {
                finish();
            }

            @Override
            public void onComplete() {
                finish();
            }
        }

        private class HelperSubscriber implements Subscriber<Object> {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
                onSubscribeContext = AsyncContext.current();
            }

            @Override
            public void onNext(Object o) {
                onNextContexts.add(AsyncContext.current());
            }

            @Override
            public void onError(Throwable e) {
                finish();
            }

            @Override
            public void onComplete() {
                finish();
            }
        }

        private void finish() {
            onTerminateContext = AsyncContext.current();
            latch.countDown();
        }
    }
}
