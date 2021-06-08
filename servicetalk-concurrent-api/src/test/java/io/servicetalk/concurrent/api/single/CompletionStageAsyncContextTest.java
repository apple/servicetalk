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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.LegacyTestSingle;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.concurrent.api.Single.fromStage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionStageAsyncContextTest {
    private static final Key<Integer> K1 = newKey("k1");
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor(ST_THREAD_PREFIX_NAME);
    private static final String ST_THREAD_PREFIX_NAME = "st-exec-thread";
    private static final String JDK_THREAD_NAME_PREFIX = "jdk-thread";
    private static final AtomicInteger threadCount = new AtomicInteger();
    private static ExecutorService jdkExecutor;
    private LegacyTestSingle<String> source;

    @BeforeAll
    static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool(
                r -> new Thread(r, JDK_THREAD_NAME_PREFIX + '-' + threadCount.incrementAndGet()));
    }

    @AfterAll
    static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @BeforeEach
    void beforeTest() {
        AsyncContext.clear();
        source = new LegacyTestSingle<>(true, true);
    }

    @Test
    void fromStagePreservesContext() throws InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        int expectedK1Value = ThreadLocalRandom.current().nextInt();
        jdkExecutor.execute(() -> future.complete("foo"));
        AtomicReference<Integer> actualK1Value = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Set the AsyncContext right before subscribe.
        AsyncContext.put(K1, expectedK1Value);
        fromStage(future).subscribe(val -> {
            actualK1Value.compareAndSet(null, AsyncContext.get(K1));
            latch.countDown();
        });
        latch.await();
        assertEquals(expectedK1Value, actualK1Value.get().intValue());
    }

    @Test
    void singleToCompletionStageHandle() throws InterruptedException {
        int expectedK1Value = ThreadLocalRandom.current().nextInt();
        AsyncContext.put(K1, expectedK1Value);
        AtomicReference<Integer> actualK1Value = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        source.toCompletionStage().handle((s, t) -> {
            actualK1Value.compareAndSet(null, AsyncContext.get(K1));
            latch.countDown();
            return 1;
        });
        jdkExecutor.execute(() -> source.onSuccess("foo"));
        latch.await();
        assertEquals(expectedK1Value, actualK1Value.get().intValue());
    }

    @Test
    void singleToCompletionToCompletableFuture() throws InterruptedException {
        int expectedK1Value = ThreadLocalRandom.current().nextInt();
        AtomicReference<Integer> actualK1Value = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        AsyncContext.put(K1, expectedK1Value);
        source.toCompletionStage().toCompletableFuture().handle((s, t) -> {
            actualK1Value.compareAndSet(null, AsyncContext.get(K1));
            latch.countDown();
            return 1;
        });
        jdkExecutor.execute(() -> source.onSuccess("foo"));
        latch.await();
        assertEquals(expectedK1Value, actualK1Value.get().intValue());
    }

    @Test
    void singleOperatorAndMultipleCompletionListeners() throws InterruptedException {
        int expectedK1Value = ThreadLocalRandom.current().nextInt();
        AsyncContext.put(K1, expectedK1Value);
        AtomicReference<Integer> actualK1Value1 = new AtomicReference<>();
        AtomicReference<Integer> actualK1Value2 = new AtomicReference<>();
        AtomicReference<Integer> actualK1Value3 = new AtomicReference<>();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        source.whenOnSuccess(v -> {
            actualK1Value1.compareAndSet(null, AsyncContext.get(K1));
            latch1.countDown();
        }).toCompletionStage().handle((s, t) -> {
            actualK1Value2.compareAndSet(null, AsyncContext.get(K1));
            latch2.countDown();
            return 1;
        }).thenAccept(v -> {
            actualK1Value3.compareAndSet(null, AsyncContext.get(K1));
            latch3.countDown();
        });
        jdkExecutor.execute(() -> source.onSuccess("foo"));
        latch1.await();
        latch2.await();
        latch3.await();
        assertEquals(expectedK1Value, actualK1Value1.get().intValue());
        assertEquals(expectedK1Value, actualK1Value2.get().intValue());
        assertEquals(expectedK1Value, actualK1Value3.get().intValue());
    }

    @Test
    void directToCompletableFuture() throws InterruptedException {
        int expectedK1Value = ThreadLocalRandom.current().nextInt();
        AtomicReference<Integer> actualK1Value = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        AsyncContext.put(K1, expectedK1Value);
        CompletableFuture<String> future = source.toCompletionStage().toCompletableFuture();

        future.thenAccept(v -> {
            actualK1Value.compareAndSet(null, AsyncContext.get(K1));
            latch.countDown();
        });
        AsyncContext.clear();
        assertTrue(AsyncContext.isEmpty());

        jdkExecutor.execute(() -> future.complete("foo"));
        latch.await();
        assertEquals(expectedK1Value, actualK1Value.get().intValue());
    }
}
