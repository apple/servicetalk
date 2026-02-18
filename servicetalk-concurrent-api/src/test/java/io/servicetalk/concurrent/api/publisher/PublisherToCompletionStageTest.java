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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.TestPublisher;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublisherToCompletionStageTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private static ExecutorService jdkExecutor;

    @BeforeAll
    static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool();
    }

    @AfterAll
    static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @Test
    void listenBeforeComplete() throws InterruptedException {
        verifyComplete(false, false);
        verifyComplete(false, true);
    }

    @Test
    void completeBeforeListen() throws InterruptedException {
        verifyComplete(true, false);
        verifyComplete(true, true);
    }

    private void verifyComplete(boolean completeBeforeListen, boolean sendData) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Collection<String>> resultRef = new AtomicReference<>();
        CompletionStage<? extends Collection<String>> stage = publisher.toCompletionStage();
        if (completeBeforeListen) {
            if (sendData) {
                publisher.onNext("Hello", "World");
            }
            publisher.onComplete();
            stage.thenAccept(result -> {
                resultRef.compareAndSet(null, result);
                latch.countDown();
            });
        } else {
            stage.thenAccept(result -> {
                resultRef.compareAndSet(null, result);
                latch.countDown();
            });
            if (sendData) {
                publisher.onNext("Hello", "World");
            }
            publisher.onComplete();
        }
        latch.await();
        if (sendData) {
            assertThat(resultRef.get(), contains("Hello", "World"));
        } else {
            assertThat(resultRef.get(), is(empty()));
        }
    }

    @Test
    void listenBeforeError() throws InterruptedException {
        verifyError(false, true);
        verifyError(false, false);
    }

    @Test
    void errorBeforeListen() throws InterruptedException {
        verifyError(true, true);
        verifyError(true, false);
    }

    private void verifyError(boolean completeBeforeListen, boolean sendData) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> resultRef = new AtomicReference<>();
        CompletionStage<? extends Collection<String>> stage = publisher.toCompletionStage();
        if (completeBeforeListen) {
            if (sendData) {
                publisher.onNext("Hello", "World");
            }
            publisher.onError(DELIBERATE_EXCEPTION);
            stage.exceptionally(cause -> {
                resultRef.compareAndSet(null, cause);
                latch.countDown();
                return null;
            });
        } else {
            stage.exceptionally(cause -> {
                resultRef.compareAndSet(null, cause);
                latch.countDown();
                return null;
            });
            if (sendData) {
                publisher.onNext("Hello", "World");
            }
            publisher.onError(DELIBERATE_EXCEPTION);
        }
        latch.await();
        assertThat(resultRef.get(), is(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}] viaCompletionStage={0}")
    @ValueSource(booleans = {false, true})
    void futureEmptyComplete(boolean viaCompletionStage) throws Exception {
        Future<? extends Collection<String>> f = viaCompletionStage ?
                publisher.toCompletionStage().toCompletableFuture() : publisher.toFuture();
        jdkExecutor.execute(publisher::onComplete);
        assertThat(f.get(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] viaCompletionStage={0}")
    @ValueSource(booleans = {false, true})
    void futureComplete(boolean viaCompletionStage) throws Exception {
        Future<? extends Collection<String>> f = viaCompletionStage ?
                publisher.toCompletionStage().toCompletableFuture() : publisher.toFuture();
        jdkExecutor.execute(() -> {
            publisher.onNext("Hello", "World");
            publisher.onComplete();
        });
        assertThat(f.get(), contains("Hello", "World"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] viaCompletionStage={0}")
    @ValueSource(booleans = {false, true})
    void futureReduceComplete(boolean viaCompletionStage) throws Exception {
        BiFunction<StringBuilder, String, StringBuilder> reducer = (sb, next) -> {
            sb.append(next);
            return sb;
        };
        Future<StringBuilder> f = viaCompletionStage ?
                publisher.toCompletionStage(StringBuilder::new, reducer).toCompletableFuture() :
                publisher.toFuture(StringBuilder::new, reducer);
        jdkExecutor.execute(() -> {
            publisher.onNext("Hello", "World");
            publisher.onComplete();
        });
        assertThat(f.get().toString(), is("HelloWorld"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] viaCompletionStage={0}")
    @ValueSource(booleans = {false, true})
    void futureFail(boolean viaCompletionStage) {
        Future<? extends Collection<String>> f = viaCompletionStage ?
                publisher.toCompletionStage().toCompletableFuture() : publisher.toFuture();
        jdkExecutor.execute(() -> {
            publisher.onNext("Hello", "World");
            publisher.onError(DELIBERATE_EXCEPTION);
        });
        Exception e = assertThrows(ExecutionException.class, f::get);
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }
}
