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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class PublisherToCompletionStageTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private static ExecutorService jdkExecutor;

    @BeforeClass
    public static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @Test
    public void listenBeforeComplete() throws InterruptedException {
        verifyComplete(false, false);
        verifyComplete(false, true);
    }

    @Test
    public void completeBeforeListen() throws InterruptedException {
        verifyComplete(true, false);
        verifyComplete(true, true);
    }

    private void verifyComplete(boolean completeBeforeListen, boolean sendData) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Collection<String>> resultRef = new AtomicReference<>();
        CompletionStage<? extends Collection<String>> stage = publisher.getPublisher().toCompletionStage();
        if (completeBeforeListen) {
            if (sendData) {
                publisher.sendItems("Hello", "World");
            }
            publisher.complete();
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
                publisher.sendItems("Hello", "World");
            }
            publisher.complete();
        }
        latch.await();
        if (sendData) {
            assertThat(resultRef.get(), contains("Hello", "World"));
        } else {
            assertThat(resultRef.get(), is(empty()));
        }
    }

    @Test
    public void listenBeforeError() throws InterruptedException {
        verifyError(false, true);
        verifyError(false, false);
    }

    @Test
    public void errorBeforeListen() throws InterruptedException {
        verifyError(true, true);
        verifyError(true, false);
    }

    private void verifyError(boolean completeBeforeListen, boolean sendData) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> resultRef = new AtomicReference<>();
        CompletionStage<? extends Collection<String>> stage = publisher.getPublisher().toCompletionStage();
        if (completeBeforeListen) {
            if (sendData) {
                publisher.sendItems("Hello", "World");
            }
            publisher.fail();
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
                publisher.sendItems("Hello", "World");
            }
            publisher.fail();
        }
        latch.await();
        assertThat(resultRef.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void futureEmptyComplete() throws ExecutionException, InterruptedException {
        Future<? extends Collection<String>> f = publisher.getPublisher().toFuture();
        jdkExecutor.execute(publisher::complete);
        assertThat(f.get(), is(empty()));
    }

    @Test
    public void futureComplete() throws ExecutionException, InterruptedException {
        Future<? extends Collection<String>> f = publisher.getPublisher().toFuture();
        jdkExecutor.execute(() -> {
            publisher.sendItems("Hello", "World");
            publisher.complete();
        });
        assertThat(f.get(), contains("Hello", "World"));
    }

    @Test
    public void futureReduceComplete() throws ExecutionException, InterruptedException {
        Future<StringBuilder> f = publisher.getPublisher().toFuture(StringBuilder::new, (sb, next) -> {
            sb.append(next);
            return sb;
        });
        jdkExecutor.execute(() -> {
            publisher.sendItems("Hello", "World");
            publisher.complete();
        });
        assertThat(f.get().toString(), is("HelloWorld"));
    }

    @Test
    public void futureFail() throws ExecutionException, InterruptedException {
        Future<? extends Collection<String>> f = publisher.getPublisher().toFuture();
        jdkExecutor.execute(() -> {
            publisher.sendItems("Hello", "World");
            publisher.fail();
        });
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        f.get();
    }
}
