/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractFutureToSingleTest {
    protected ExecutorService jdkExecutor;

    @BeforeAll
    void beforeClass() {
        jdkExecutor = Executors.newCachedThreadPool();
    }

    @AfterAll
    void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    abstract Single<String> from(CompletableFuture<String> future);

    @Test
    void completion() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        jdkExecutor.execute(() -> future.complete("foo"));
        assertEquals("foo", single.toFuture().get());
    }

    @Test
    void timeout() {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future).timeout(1, MILLISECONDS);
        Exception e = assertThrows(ExecutionException.class, () -> single.toFuture().get());
        assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        assertTrue(future.isCancelled());
    }

    @Test
    void cancellation() throws InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        toSource(single).subscribe(new SingleSource.Subscriber<String>() {
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
