/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension.DEFAULT_TIMEOUT_SECONDS;
import static java.lang.Integer.bitCount;
import static java.lang.Integer.numberOfTrailingZeros;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(TimeoutTracingInfoExtension.class)
public class DefaultAsyncContextProviderTest {
    private static final Key<String> K1 = Key.newKey("k1");
    private static final Key<String> K2 = Key.newKey("k2");
    private static final Key<String> K3 = Key.newKey("k3");
    private static final Key<String> K4 = Key.newKey("k4");
    private static final Key<String> K5 = Key.newKey("k5");
    private static final Key<String> K6 = Key.newKey("k6");
    private static final Key<String> K7 = Key.newKey("k7");
    private static final Key<String> K8 = Key.newKey("k8");

    private static ScheduledExecutorService executor;

    @BeforeAll
    public static void beforeClass() {
        AsyncContext.autoEnable();
        executor = Executors.newScheduledThreadPool(4);
    }

    @AfterAll
    public static void afterClass() throws Exception {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @BeforeEach
    public void setUp() {
        AsyncContext.clear();
    }

    private static void completeOnExecutor(CompletableSource.Subscriber subscriber) {
        executor.execute(() -> {
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onComplete();
        });
    }

    private static <T> void completeOnExecutor(SingleSource.Subscriber<? super T> subscriber, T value) {
        executor.execute(() -> {
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onSuccess(value);
        });
    }

    @Test
    public void testContextInCompletableListener() throws Exception {
        Completable completable = new Completable() {
            @Override
            protected void handleSubscribe(CompletableSource.Subscriber completableSubscriber) {
                completeOnExecutor(completableSubscriber);
            }
        };

        AsyncContext.put(K1, "v1");
        new ContextCaptureCompletableSubscriber()
                .subscribeAndWait(completable)
                .verifyContext(map -> {
                    assertEquals("v1", map.get(K1));
                    assertNull(map.get(K2));
                });

        // Each subscribe/subscribe gets its own context
        AsyncContext.put(K2, "v2");
        new ContextCaptureCompletableSubscriber()
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

        Completable completable = new Completable() {
            @Override
            protected void handleSubscribe(CompletableSource.Subscriber completableSubscriber) {
                AsyncContext.put(K1, "v1.2");
                AsyncContext.put(K2, "v2.1");
                f1.complete(AsyncContext.current().copy());
                completeOnExecutor(completableSubscriber);
            }
        }.merge(new Completable() {
            @Override
            protected void handleSubscribe(CompletableSource.Subscriber completableSubscriber) {
                AsyncContext.put(K2, "v2.2");
                // We are in another async source, this shouldn't be visible to the outer Subscriber chain.
                f2.complete(AsyncContext.current().copy());
                completeOnExecutor(completableSubscriber);
            }
        }).beforeFinally(() -> f3.complete(AsyncContext.current().copy()));

        AsyncContext.put(K1, "v1.1");
        completable.toFuture().get();

        assertEquals("v1.2", f1.get().get(K1));
        assertEquals("v1.2", f2.get().get(K1));
        assertEquals("v1.2", f3.get().get(K1));
        assertEquals("v1.1", AsyncContext.get(K1));

        assertEquals("v2.1", f1.get().get(K2));
        assertEquals("v2.2", f2.get().get(K2));
        assertEquals("v2.1", f3.get().get(K2));
        assertNull(AsyncContext.get(K2));
    }

    @Test
    public void testContextInSingleListener() throws Exception {
        Single<String> single = new Single<String>() {
            @Override
            protected void handleSubscribe(SingleSource.Subscriber<? super String> singleSubscriber) {
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
            protected void handleSubscribe(SingleSource.Subscriber<? super String> singleSubscriber) {
                AsyncContext.put(K1, "v1.2");
                AsyncContext.put(K2, "v2.1");
                f1.complete(AsyncContext.current().copy());
                completeOnExecutor(singleSubscriber, "a");
            }
        }.map(v -> {
            AsyncContext.put(K2, "v2.2");
            f2.complete(AsyncContext.current().copy());
            return v;
        }).flatMap(v -> {
            AsyncContext.put(K2, "v2.3"); // this will apply to the Single(b) created here
            f3.complete(AsyncContext.current().copy());
            return new Single<String>() {
                @Override
                protected void handleSubscribe(SingleSource.Subscriber<? super String> singleSubscriber) {
                    // We are in another async source, this shouldn't be visible to the outer Subscriber chain.
                    f4.complete(AsyncContext.current().copy());
                    completeOnExecutor(singleSubscriber, "b");
                }
            };
        }).beforeFinally(() -> f5.complete(AsyncContext.current().copy()));

        AsyncContext.put(K1, "v1.1");
        single.toFuture().get();

        assertEquals("v1.2", f1.get().get(K1));
        assertEquals("v1.2", f2.get().get(K1));
        assertEquals("v1.2", f3.get().get(K1));
        assertEquals("v1.2", f4.get().get(K1));
        assertEquals("v1.2", f5.get().get(K1));
        assertEquals("v1.1", AsyncContext.get(K1));

        assertEquals("v2.1", f1.get().get(K2));
        assertEquals("v2.2", f2.get().get(K2));
        assertEquals("v2.3", f3.get().get(K2));
        assertEquals("v2.3", f4.get().get(K2));
        assertEquals("v2.3", f5.get().get(K2));
        assertNull(AsyncContext.get(K2));
    }

    @Test
    public void testContextInSingleConversions() throws Exception {
        CompletableFuture<AsyncContextMap> f1 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f2 = new CompletableFuture<>();
        CompletableFuture<AsyncContextMap> f3 = new CompletableFuture<>();

        Completable completable = new Single<String>() {
            @Override
            protected void handleSubscribe(SingleSource.Subscriber<? super String> singleSubscriber) {
                AsyncContext.put(K1, "v1.2");
                AsyncContext.put(K2, "v2.1");
                f1.complete(AsyncContext.current().copy());
                completeOnExecutor(singleSubscriber, "a");
            }
        }.ignoreElement().merge(new Completable() {
            @Override
            protected void handleSubscribe(CompletableSource.Subscriber completableSubscriber) {
                // We are in another async source, this shouldn't be visible to the outer Subscriber chain.
                AsyncContext.put(K2, "v2.2");
                f2.complete(AsyncContext.current().copy());
                completeOnExecutor(completableSubscriber);
            }
        }).beforeFinally(() -> f3.complete(AsyncContext.current().copy()));

        AsyncContext.put(K1, "v1.1");
        completable.toFuture().get();

        assertEquals("v1.2", f1.get().get(K1));
        assertEquals("v1.2", f2.get().get(K1));
        assertEquals("v1.2", f3.get().get(K1));

        assertEquals("v2.1", f1.get().get(K2));
        assertEquals("v2.2", f2.get().get(K2));
        assertEquals("v2.1", f3.get().get(K2));
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
        }).concat(new ContextCaptureTestPublisher().filter(v -> {
            f2.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return true;
        })).map(v -> {
            f3.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
            return v;
        }).beforeOnComplete(() -> {
            f4.complete(AsyncContext.current());

            AsyncContext.put(K2, "v2"); // this won't affect the operators below
        }).collect(StringBuilder::new, StringBuilder::append)
                .beforeFinally(() -> f5.complete(AsyncContext.current()));

        AsyncContext.put(K1, "v1");
        single.toFuture().get();

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
                .runAndWait(INSTANCE.wrapJdkExecutor(executor))
                .verifyContext(verifier);

        new ContextCaptureRunnable()
                .runAndWait(INSTANCE.wrapJdkExecutorService(executor))
                .verifyContext(verifier);

        new ContextCaptureCallable<String>()
                .runAndWait(INSTANCE.wrapJdkExecutorService(executor))
                .verifyContext(verifier);

        new ContextCaptureCallable<String>()
                .scheduleAndWait(INSTANCE.wrapJdkScheduledExecutorService(executor))
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
                    Function<Void, Void> f = INSTANCE.wrapFunction(v -> {
                        collector.complete(AsyncContext.current());
                        return v;
                    }, AsyncContext.current());
                    executor.execute(() -> f.apply(null));
                })
                .verifyContext(verifier);

        new ContextCapturer()
                .runAndWait(collector -> {
                    Consumer<Void> c = INSTANCE.wrapConsumer(v -> collector.complete(AsyncContext.current()),
                            AsyncContext.current());
                    executor.execute(() -> c.accept(null));
                })
                .verifyContext(verifier);

        new ContextCapturer()
                .runAndWait(collector -> {
                    BiFunction<Void, Void, Void> bf = INSTANCE.wrapBiFunction((v1, v2) -> {
                        collector.complete(AsyncContext.current());
                        return v1;
                    }, AsyncContext.current());
                    executor.execute(() -> bf.apply(null, null));
                })
                .verifyContext(verifier);

        new ContextCapturer()
                .runAndWait(collector -> {
                    BiConsumer<Void, Void> bc = INSTANCE.wrapBiConsumer((v1, v2) -> {
                        collector.complete(AsyncContext.current());
                    }, AsyncContext.current());
                    executor.execute(() -> bc.accept(null, null));
                })
                .verifyContext(verifier);
    }

    @Test
    public void testSinglePutAndRemove() {
        assertContextSize(0);

        AsyncContext.put(K1, "v1");
        assertContains(K1, "v1");
        assertContextSize(1);

        // Duplicate put
        AsyncContext.put(K1, "v1-1");

        AsyncContext.put(K2, "v2");
        assertContains(K1, "v1-1");
        assertContains(K2, "v2");
        assertContextSize(2);

        // Duplicate put
        AsyncContext.put(K1, "v1-2");
        AsyncContext.put(K2, "v2-1");

        AsyncContext.put(K3, "v3");
        assertContains(K1, "v1-2");
        assertContains(K2, "v2-1");
        assertContains(K3, "v3");
        assertContextSize(3);

        // Duplicate put
        AsyncContext.put(K1, "v1-3");
        AsyncContext.put(K2, "v2-2");
        AsyncContext.put(K3, "v3-1");

        AsyncContext.put(K4, "v4");
        assertContains(K1, "v1-3");
        assertContains(K2, "v2-2");
        assertContains(K3, "v3-1");
        assertContains(K4, "v4");
        assertContextSize(4);

        // Duplicate put
        AsyncContext.put(K1, "v1-4");
        AsyncContext.put(K2, "v2-3");
        AsyncContext.put(K3, "v3-2");
        AsyncContext.put(K4, "v4-1");

        AsyncContext.put(K5, "v5");
        assertContains(K1, "v1-4");
        assertContains(K2, "v2-3");
        assertContains(K3, "v3-2");
        assertContains(K4, "v4-1");
        assertContains(K5, "v5");
        assertContextSize(5);

        // Duplicate put
        AsyncContext.put(K1, "v1-5");
        AsyncContext.put(K2, "v2-4");
        AsyncContext.put(K3, "v3-3");
        AsyncContext.put(K4, "v4-2");
        AsyncContext.put(K5, "v5-1");

        AsyncContext.put(K6, "v6");
        assertContains(K1, "v1-5");
        assertContains(K2, "v2-4");
        assertContains(K3, "v3-3");
        assertContains(K4, "v4-2");
        assertContains(K5, "v5-1");
        assertContains(K6, "v6");
        assertContextSize(6);

        // Duplicate put
        AsyncContext.put(K1, "v1-6");
        AsyncContext.put(K2, "v2-5");
        AsyncContext.put(K3, "v3-4");
        AsyncContext.put(K4, "v4-3");
        AsyncContext.put(K5, "v5-2");
        AsyncContext.put(K6, "v6-1");

        AsyncContext.put(K7, "v7");
        assertContains(K1, "v1-6");
        assertContains(K2, "v2-5");
        assertContains(K3, "v3-4");
        assertContains(K4, "v4-3");
        assertContains(K5, "v5-2");
        assertContains(K6, "v6-1");
        assertContains(K7, "v7");
        assertContextSize(7);

        // Duplicate put
        AsyncContext.put(K1, "v1-7");
        AsyncContext.put(K2, "v2-6");
        AsyncContext.put(K3, "v3-5");
        AsyncContext.put(K4, "v4-4");
        AsyncContext.put(K5, "v5-3");
        AsyncContext.put(K6, "v6-2");
        AsyncContext.put(K7, "v7-1");

        AsyncContext.put(K8, "v8");
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContains(K3, "v3-5");
        assertContains(K4, "v4-4");
        assertContains(K5, "v5-3");
        assertContains(K6, "v6-2");
        assertContains(K7, "v7-1");
        assertContains(K8, "v8");
        assertContextSize(8);

        // Now do removal
        AsyncContext.remove(K8);
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContains(K3, "v3-5");
        assertContains(K4, "v4-4");
        assertContains(K5, "v5-3");
        assertContains(K6, "v6-2");
        assertContains(K7, "v7-1");
        assertContextSize(7);

        AsyncContext.remove(K7);
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContains(K3, "v3-5");
        assertContains(K4, "v4-4");
        assertContains(K5, "v5-3");
        assertContains(K6, "v6-2");
        assertContextSize(6);

        AsyncContext.remove(K6);
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContains(K3, "v3-5");
        assertContains(K4, "v4-4");
        assertContains(K5, "v5-3");
        assertContextSize(5);

        AsyncContext.remove(K5);
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContains(K3, "v3-5");
        assertContains(K4, "v4-4");
        assertContextSize(4);

        AsyncContext.remove(K4);
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContains(K3, "v3-5");
        assertContextSize(3);

        AsyncContext.remove(K3);
        assertContains(K1, "v1-7");
        assertContains(K2, "v2-6");
        assertContextSize(2);

        AsyncContext.remove(K2);
        assertContains(K1, "v1-7");
        assertContextSize(1);

        AsyncContext.remove(K1);
        assertContextSize(0);
    }

    @Test
    public void testMultiPutAndRemove() {
        AsyncContext.putAll(newMap(K1, "v1"));
        assertContains(K1, "v1");
        assertContextSize(1);

        AsyncContext.putAll(newMap(K1, "v1-2", K2, "v2"));
        assertContains(K1, "v1-2");
        assertContains(K2, "v2");
        assertContextSize(2);

        AsyncContext.putAll(newMap(K1, "v1-3", K3, "v3"));
        assertContains(K1, "v1-3");
        assertContains(K2, "v2");
        assertContains(K3, "v3");
        assertContextSize(3);

        AsyncContext.putAll(newMap(K1, "v1-4", K2, "v2-2", K3, "v3-1"));
        assertContains(K1, "v1-4");
        assertContains(K2, "v2-2");
        assertContains(K3, "v3-1");
        assertContextSize(3);

        AsyncContext.putAll(newMap(K1, "v1-4", K2, "v2-2", K3, "v3-1"));
        assertContains(K1, "v1-4");
        assertContains(K2, "v2-2");
        assertContains(K3, "v3-1");
        assertContextSize(3);

        AsyncContext.putAll(newMap(K4, "v4", K5, "v5", K6, "v6"));
        assertContains(K1, "v1-4");
        assertContains(K2, "v2-2");
        assertContains(K3, "v3-1");
        assertContains(K4, "v4");
        assertContains(K5, "v5");
        assertContains(K6, "v6");
        assertContextSize(6);

        AsyncContext.putAll(newMap(K1, "v1-5", K7, "v7", K8, "v8"));
        assertContains(K1, "v1-5");
        assertContains(K2, "v2-2");
        assertContains(K3, "v3-1");
        assertContains(K4, "v4");
        assertContains(K5, "v5");
        assertContains(K6, "v6");
        assertContains(K7, "v7");
        assertContains(K8, "v8");
        assertContextSize(8);

        // Start removal
        AsyncContext.removeAll(asList(K1));
        assertContains(K2, "v2-2");
        assertContains(K3, "v3-1");
        assertContains(K4, "v4");
        assertContains(K5, "v5");
        assertContains(K6, "v6");
        assertContains(K7, "v7");
        assertContains(K8, "v8");
        assertContextSize(7);

        AsyncContext.removeAll(asList(K1, K8, K3));
        assertContains(K2, "v2-2");
        assertContains(K4, "v4");
        assertContains(K5, "v5");
        assertContains(K6, "v6");
        assertContains(K7, "v7");
        assertContextSize(5);

        AsyncContext.removeAll(asList(K7));
        assertContains(K2, "v2-2");
        assertContains(K4, "v4");
        assertContains(K5, "v5");
        assertContains(K6, "v6");
        assertContextSize(4);

        AsyncContext.removeAll(asList(K6, K4, K2, K5));
        assertContextSize(0);
    }

    @Test
    public void oneRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1));
    }

    @Test
    public void twoRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2));
    }

    @Test
    public void threeRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2, K3));
    }

    @Test
    public void fourRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2, K3, K4));
    }

    @Test
    public void fiveRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2, K3, K4, K5));
    }

    @Test
    public void sixRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2, K3, K4, K5, K6));
    }

    @Test
    public void sevenRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2, K3, K4, K5, K6, K7));
    }

    @Test
    public void eightRemoveMultiplePermutations() {
        testRemoveMultiplePermutations(asList(K1, K2, K3, K4, K5, K6, K7, K8));
    }

    private static void testRemoveMultiplePermutations(List<Key<String>> keys) {
        for (int i = 0; i < keys.size(); ++i) {
            AsyncContext.put(keys.get(i), "v" + (i + 1));
        }
        for (int i = 0; i < keys.size(); ++i) {
            assertContains(keys.get(i), "v" + (i + 1));
        }

        final int numCombinations = 1 << keys.size();
        for (int i = 1; i < numCombinations; ++i) {
            int remainingBits = i;
            Key<?>[] permutation = new Key<?>[bitCount(i)];
            int x = 0;
            do {
                int keysIndex = numberOfTrailingZeros(remainingBits);
                permutation[x++] = keys.get(keysIndex);
                remainingBits &= ~(1 << keysIndex);
            } while (remainingBits != 0);

            AsyncContext.removeAll(asList(permutation));

            // Verify all the remove elements are not present in the context, and the size is as expected.
            assertContextSize(keys.size() - permutation.length);
            for (x = 0; x < permutation.length; ++x) {
                assertNotContains(permutation[x]);
            }

            // Verify all the values that should be in the context, are contained in the context.
            containsLoop:
            for (int j = 0; j < keys.size(); ++j) {
                final Key<?> key = keys.get(j);
                for (x = 0; x < permutation.length; ++x) {
                    if (key == permutation[x]) {
                        continue containsLoop;
                    }
                }
                assertContains(key, "v" + (j + 1));
            }

            // Rest the map to the initial state, and verify starting condition
            AsyncContext.clear();
            for (int j = 0; j < keys.size(); ++j) {
                AsyncContext.put(keys.get(j), "v" + (j + 1));
            }
            for (int j = 0; j < keys.size(); ++j) {
                assertContains(keys.get(j), "v" + (j + 1));
            }
        }
    }

    @Test
    public void emptyPutMultiplePermutations() {
        testPutMultiplePermutations(emptyList());
    }

    @Test
    public void onePutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1));
    }

    @Test
    public void twoPutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2));
    }

    @Test
    public void threePutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2, K3));
    }

    @Test
    public void fourPutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2, K3, K4));
    }

    @Test
    public void fivePutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2, K3, K4, K5));
    }

    @Test
    public void sixPutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2, K3, K4, K5, K6));
    }

    @Test
    public void sevenPutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2, K3, K4, K5, K6, K7));
    }

    @Test
    public void eightPutMultiplePermutations() {
        testPutMultiplePermutations(asList(K1, K2, K3, K4, K5, K6, K7, K8));
    }

    private static void testPutMultiplePermutations(List<Key<String>> initialKeys) {
        final Key<?>[] putKeys = new Key<?>[] {K1, K2, K3, K4, K5, K6, K7, K8};
        for (int i = 0; i < initialKeys.size(); ++i) {
            AsyncContext.put(initialKeys.get(i), "v" + (i + 1));
        }
        for (int i = 0; i < initialKeys.size(); ++i) {
            assertContains(initialKeys.get(i), "v" + (i + 1));
        }

        final int numCombinations = 1 << putKeys.length;
        for (int i = 1; i < numCombinations; ++i) {
            int remainingBits = i;
            Key<?>[] permutation = new Key<?>[bitCount(i)];
            int x = 0;
            do {
                int putKeysIndex = numberOfTrailingZeros(remainingBits);
                permutation[x++] = putKeys[putKeysIndex];
                remainingBits &= ~(1 << putKeysIndex);
            } while (remainingBits != 0);

            Map<Key<?>, Object> insertionMap = newMap(permutation);
            AsyncContext.putAll(insertionMap);

            // Verify all the put values are present and the expected size is as expected.
            int expectedSize = initialKeys.size();
            for (x = 0; x < permutation.length; ++x) {
                Key<?> key = permutation[x];
                if (!initialKeys.contains(key)) {
                    ++expectedSize;
                }
                assertContains(key, "permutation" + (x + 1));
            }
            assertContextSize(expectedSize);

            // Make sure all the initial keys, whose value has not been modified, still exist.
            for (int j = 0; j < initialKeys.size(); ++j) {
                Key<?> key = initialKeys.get(j);
                if (!insertionMap.containsKey(key)) {
                    assertContains(key, "v" + (j + 1));
                }
            }

            // Make sure elements that are not expected to be in the context, are not there.
            for (x = 0; x < putKeys.length; ++x) {
                Key<?> key = putKeys[x];
                if (!insertionMap.containsKey(key) && !initialKeys.contains(key)) {
                    assertNotContains(key);
                }
            }

            // Rest the map to the initial state, and verify starting condition
            AsyncContext.clear();
            for (int j = 0; j < initialKeys.size(); ++j) {
                AsyncContext.put(initialKeys.get(j), "v" + (j + 1));
            }
            for (int j = 0; j < initialKeys.size(); ++j) {
                assertContains(initialKeys.get(j), "v" + (j + 1));
            }
        }
    }

    public static Map<Key<?>, Object> newMap(Key<?>... keys) {
        HashMap<Key<?>, Object> map = new HashMap<>();
        for (int i = 0; i < keys.length; ++i) {
            map.put(keys[i], "permutation" + (i + 1));
        }
        return map;
    }

    public static Map<Key<?>, Object> newMap(Key<?> k1, Object v1) {
        HashMap<Key<?>, Object> map = new HashMap<>();
        map.put(k1, v1);
        return map;
    }

    public static Map<Key<?>, Object> newMap(Key<?> k1, Object v1, Key<?> k2, Object v2) {
        HashMap<Key<?>, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static Map<Key<?>, Object> newMap(Key<?> k1, Object v1, Key<?> k2, Object v2, Key<?> k3, Object v3) {
        HashMap<Key<?>, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    private static <T> void assertContains(Key<T> key, Object value) {
        assertEquals(value, AsyncContext.get(key));
        assertTrue(AsyncContext.containsKey(key));
        assertFalse(AsyncContext.current().isEmpty());
    }

    private static void assertNotContains(Key<?> key) {
        assertNull(AsyncContext.get(key));
        assertFalse(AsyncContext.containsKey(key));
    }

    private static void assertContextSize(int size) {
        assertEquals(size, AsyncContext.current().size());
        assertEquals(size == 0, AsyncContext.current().isEmpty());
    }

    private static class ContextCaptureCompletableSubscriber implements CompletableSource.Subscriber {
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
            toSource(completable).subscribe(this);
            latch.await();
            return this;
        }

        ContextCaptureCompletableSubscriber verifyContext(Consumer<AsyncContextMap> consumer) {
            consumer.accept(onSubscribeContext);
            consumer.accept(onTerminateContext);
            return this;
        }
    }

    private static class ContextCaptureSingleSubscriber<T> implements SingleSource.Subscriber<T> {
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
            toSource(single).subscribe(this);
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

        @Override
        protected void handleSubscribe(PublisherSource.Subscriber s) {
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

    private static class ContextCaptureSubscriber<T> implements Subscriber<T> {
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
            toSource(publisher).subscribe(this);
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

        ContextCaptureRunnable verifyContext(Consumer<AsyncContextMap> consumer)
                throws ExecutionException, InterruptedException {
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

        ContextCaptureCallable<T> scheduleAndWait(ScheduledExecutorService executor)
                throws ExecutionException, InterruptedException {
            executor.schedule(this, 10, TimeUnit.MILLISECONDS);
            mapFuture.get();
            return this;
        }

        ContextCaptureCallable<T> verifyContext(Consumer<AsyncContextMap> consumer)
                throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture.get());
            return this;
        }
    }

    private static class ContextCapturer {
        final CompletableFuture<AsyncContextMap> mapFuture = new CompletableFuture<>();

        ContextCapturer runAndWait(Consumer<CompletableFuture<AsyncContextMap>> consumer)
                throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture);
            mapFuture.get();
            return this;
        }

        ContextCapturer verifyContext(Consumer<AsyncContextMap> consumer)
                throws ExecutionException, InterruptedException {
            consumer.accept(mapFuture.get());
            return this;
        }
    }
}
