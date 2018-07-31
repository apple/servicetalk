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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public abstract class AbstractFutureToSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    private static ExecutorService jdkExecutor;

    @BeforeClass
    public static void beforeClass() {
        jdkExecutor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    abstract Single<String> from(CompletableFuture<String> future);

    @Test
    public void completion() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        jdkExecutor.execute(() -> future.complete("foo"));
        assertEquals("foo", awaitIndefinitelyNonNull(single));
    }

    @Test
    public void failure() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        jdkExecutor.execute(() -> future.completeExceptionally(DELIBERATE_EXCEPTION));
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        awaitIndefinitely(single);
    }

    @Test
    public void timeout() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future).timeout(1, MILLISECONDS);
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(instanceOf(TimeoutException.class)));
        awaitIndefinitely(single);
        assertTrue(future.isCancelled());
    }

    @Test
    public void cancellation() throws InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        single.subscribe(new io.servicetalk.concurrent.Single.Subscriber<String>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                cancellable.cancel();
            }

            @Override
            public void onSuccess(@Nullable final String result) {
            }

            @Override
            public void onError(final Throwable t) {
            }
        });

        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((value, cause) -> {
            causeRef.compareAndSet(null, cause);
            latch.countDown();
        });

        latch.await();
        assertThat(causeRef.get(), is(instanceOf(CancellationException.class)));
    }
}
