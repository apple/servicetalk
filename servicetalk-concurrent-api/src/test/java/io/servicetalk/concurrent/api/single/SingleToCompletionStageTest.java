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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.NORM_PRIORITY;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SingleToCompletionStageTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory(ST_THREAD_PREFIX_NAME, true, NORM_PRIORITY)));
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private TestSingle<String> source;
    private static ExecutorService jdkExecutor;
    private static final AtomicInteger threadCount = new AtomicInteger();
    private static final String ST_THREAD_PREFIX_NAME = "st-exec-thread-";
    private static final String JDK_THREAD_NAME_PREFIX = "jdk-thread-";

    @BeforeClass
    public static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool(
                r -> new Thread(r, JDK_THREAD_NAME_PREFIX + threadCount.incrementAndGet()));
    }

    @AfterClass
    public static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @Before
    public void beforeTest() {
        source = new TestSingle<>(executorRule.executor(), true, true);
    }

    @Test
    public void completableFutureFromSingleToCompletionStageToCompletableFutureFailure() throws Exception {
        CompletableFuture<Long> input = new CompletableFuture<>();
        CompletableFuture<Long> output = Single.fromStage(input).toCompletionStage().toCompletableFuture()
                .whenComplete((v, c) -> { })
                .thenApply(l -> l + 1)
                .whenComplete((v, c) -> { });
        input.completeExceptionally(DELIBERATE_EXCEPTION);
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        output.get();
    }

    @Test
    public void completableFutureFromSingleToCompletionStageToCompletableFutureSuccess() throws Exception {
        CompletableFuture<Long> input = new CompletableFuture<>();
        CompletableFuture<Long> output = Single.fromStage(input).toCompletionStage().toCompletableFuture()
                .exceptionally(cause -> 2L)
                .whenComplete((v, c) -> { })
                .thenApply(l -> l + 1)
                .whenComplete((v, c) -> { });
        input.complete(123L);
        assertEquals(124L, output.get().longValue());
    }

    @Test
    public void nestedInADifferentFuture() throws Exception {
        String result = CompletableFuture.completedFuture("foo")
                .thenCompose(s -> Single.success(s + "bar").toCompletionStage())
                .get();
        assertThat("Unexpected result.", result, is("foobar"));
    }

    @Test
    public void thenApply() throws Exception {
        thenApply(source.toCompletionStage().thenApply(SingleToCompletionStageTest::strLenStThread), "thenApply");
    }

    @Test
    public void thenApplyNull() throws Exception {
        thenApply(source.toCompletionStage().thenApplyAsync(SingleToCompletionStageTest::strLenStThread), null);
    }

    @Test
    public void thenApplyAsync() throws Exception {
        thenApply(source.toCompletionStage().thenApplyAsync(SingleToCompletionStageTest::strLenStThread),
                "thenApplyAsync");
    }

    @Test
    public void thenApplyAsyncExecutor() throws Exception {
        thenApply(source.toCompletionStage().thenApplyAsync(SingleToCompletionStageTest::strLenJdkThread, jdkExecutor),
                "thenApplyAsyncExecutor");
    }

    private void thenApply(CompletionStage<Integer> stage, @Nullable String expected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));
        assertEquals(strLen(expected), stage.toCompletableFuture().get().intValue());
    }

    @Test
    public void thenApplyListenAfterComplete() throws Exception {
        String expected1 = "one";
        CompletionStage<String> stage1 = source.toCompletionStage();

        jdkExecutor.execute(() -> source.onSuccess(expected1));
        assertEquals(expected1, stage1.toCompletableFuture().get());

        String expected2 = "one two";
        CompletionStage<String> stage2 = stage1.thenApply(in -> {
            verifyInStExecutorThread();
            return in + " two";
        });
        assertEquals(expected2, stage2.toCompletableFuture().get());
    }

    @Test
    public void thenApplyTransformData() throws Exception {
        thenApplyTransformData(stage1 -> stage1.thenApply(SingleToCompletionStageTest::strLenStThread));
    }

    @Test
    public void thenApplyAsyncListenAfterComplete() throws Exception {
        String expected1 = "one";
        CompletionStage<String> stage1 = source.toCompletionStage();

        jdkExecutor.execute(() -> source.onSuccess(expected1));
        assertEquals(expected1, stage1.toCompletableFuture().get());

        String expected2 = "one two";
        CompletionStage<String> stage2 = stage1.thenApplyAsync(in -> {
            verifyInJdkExecutorThread();
            return in + " two";
        }, jdkExecutor);
        assertEquals(expected2, stage2.toCompletableFuture().get());

        verifyListenerInvokedInJdkThread(stage2);
    }

    @Test
    public void thenApplyAsyncTransformData() throws Exception {
        thenApplyTransformData(stage1 -> stage1.thenApplyAsync(SingleToCompletionStageTest::strLenJdkThread,
                jdkExecutor));
    }

    private void thenApplyTransformData(Function<CompletionStage<String>, CompletionStage<Integer>> fn)
            throws ExecutionException, InterruptedException {
        String expected1 = "foo";
        CompletionStage<String> stage1 = source.toCompletionStage();

        jdkExecutor.execute(() -> source.onSuccess(expected1));
        assertEquals(expected1, stage1.toCompletableFuture().get());

        CompletionStage<Integer> stage2 = fn.apply(stage1);
        assertEquals(strLen(expected1), stage2.toCompletableFuture().get().intValue());
    }

    @Test
    public void thenApplyMultipleTimesOnlySubscribesOnce() throws Exception {
        String expected1 = "one";
        CompletionStage<String> stage1 = source.toCompletionStage();

        // Both listeners have to be applied on stage1.
        CompletionStage<Integer> stage2 = stage1.thenApply(SingleToCompletionStageTest::strLenStThread);
        CompletionStage<Integer> stage3 = stage1.thenApply(SingleToCompletionStageTest::strLenStThread);

        jdkExecutor.execute(() -> source.onSuccess(expected1));

        Integer stage2Result = stage2.toCompletableFuture().get();
        assertNotNull(stage2Result);
        Integer stage3Result = stage3.toCompletableFuture().get();
        assertNotNull(stage3Result);
        assertEquals(expected1.length() * 2, stage2Result + stage3Result);
        source.verifyListenCalled(1);
    }

    @Test
    public void thenAccept() throws Exception {
        AtomicReference<String> stringRef = new AtomicReference<>();
        thenAccept(source.toCompletionStage().thenAccept(stThread(stringRef)), stringRef, "thenAccept");
    }

    @Test
    public void thenAcceptNull() throws Exception {
        AtomicReference<String> stringRef = new AtomicReference<>();
        thenAccept(source.toCompletionStage().thenAccept(stThread(stringRef)), stringRef, null);
    }

    @Test
    public void thenAcceptAsync() throws Exception {
        AtomicReference<String> stringRef = new AtomicReference<>();
        thenAccept(source.toCompletionStage().thenAcceptAsync(stThread(stringRef)), stringRef, "thenAcceptAsync");
    }

    @Test
    public void thenAcceptAsyncExecutor() throws Exception {
        AtomicReference<String> stringRef = new AtomicReference<>();
        thenAccept(source.toCompletionStage().thenAcceptAsync(jdkThread(stringRef), jdkExecutor), stringRef,
                "thenAcceptAsyncExecutor");
    }

    private void thenAccept(CompletionStage<Void> stage, AtomicReference<String> stringRef, @Nullable String expected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertNull(stage.toCompletableFuture().get());
        assertEquals(expected, stringRef.get());
    }

    @Test
    public void thenAcceptConsumeData() throws Exception {
        thenAcceptConsumeData((stage1, ref) -> stage1.thenAccept(stThread(ref)));
    }

    @Test
    public void thenAcceptAsyncConsumeData() throws Exception {
        thenAcceptConsumeData((stage1, ref) -> stage1.thenAcceptAsync(jdkThread(ref), jdkExecutor));
    }

    private void thenAcceptConsumeData(
            BiFunction<CompletionStage<String>, AtomicReference<String>, CompletionStage<Void>> fn)
            throws ExecutionException, InterruptedException {
        AtomicReference<String> consumeRef = new AtomicReference<>();
        String expected1 = "foo";
        CompletionStage<String> stage1 = source.toCompletionStage();

        jdkExecutor.execute(() -> source.onSuccess(expected1));
        assertEquals(expected1, stage1.toCompletableFuture().get());

        CompletionStage<Void> stage2 = fn.apply(stage1, consumeRef);
        stage2.toCompletableFuture().get();
        assertEquals(expected1, consumeRef.get());
    }

    @Test
    public void thenRun() throws Exception {
        AtomicBoolean aBoolean = new AtomicBoolean();
        thenRun(source.toCompletionStage().thenRun(trueStThread(aBoolean)), aBoolean, "thenRun");
    }

    @Test
    public void thenRunAsync() throws Exception {
        AtomicBoolean aBoolean = new AtomicBoolean();
        thenRun(source.toCompletionStage().thenRunAsync(trueStThread(aBoolean)), aBoolean, "thenRunAsync");
    }

    @Test
    public void thenRunAsyncExecutor() throws Exception {
        AtomicBoolean aBoolean = new AtomicBoolean();
        thenRun(source.toCompletionStage().thenRunAsync(trueJdkThread(aBoolean), jdkExecutor),
                aBoolean, "thenRunAsyncExecutor");
    }

    private void thenRun(CompletionStage<Void> stage, AtomicBoolean aBoolean, @Nullable String expected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertNull(stage.toCompletableFuture().get());
        assertTrue(aBoolean.get());
    }

    @Test
    public void thenRunAfterData() throws Exception {
        thenRunAfterData((stage1, ref) -> stage1.thenRun(trueStThread(ref)));
    }

    @Test
    public void thenRunAsyncAfterData() throws Exception {
        thenRunAfterData((stage1, ref) -> stage1.thenRunAsync(trueJdkThread(ref), jdkExecutor));
    }

    private void thenRunAfterData(
            BiFunction<CompletionStage<String>, AtomicBoolean, CompletionStage<Void>> fn)
            throws ExecutionException, InterruptedException {
        AtomicBoolean consumeRef = new AtomicBoolean();
        String expected1 = "foo";
        CompletionStage<String> stage1 = source.toCompletionStage();

        jdkExecutor.execute(() -> source.onSuccess(expected1));
        assertEquals(expected1, stage1.toCompletableFuture().get());

        CompletionStage<Void> stage2 = fn.apply(stage1, consumeRef);
        stage2.toCompletableFuture().get();
        assertTrue(consumeRef.get());
    }

    @Test
    public void thenCombine() throws Exception {
        CompletableFuture<Double> other = new CompletableFuture<>();
        thenCombine(other, source.toCompletionStage().thenCombine(other, strLenDoubleStThread()), "foo", 123);
    }

    @Test
    public void thenCombineNull() throws Exception {
        CompletableFuture<Double> other = new CompletableFuture<>();
        thenCombine(other, source.toCompletionStage().thenCombine(other, strLenDoubleStThread()), null, 1236);
    }

    @Test
    public void thenCombineAsync() throws Exception {
        CompletableFuture<Double> other = new CompletableFuture<>();
        thenCombine(other, source.toCompletionStage().thenCombineAsync(other, strLenDoubleStThread()), "foo", 123);
    }

    @Test
    public void thenCombineAsyncExecutor() throws Exception {
        CompletableFuture<Double> other = new CompletableFuture<>();
        thenCombine(other, source.toCompletionStage().thenCombineAsync(other, strLenDoubleJdkThread(), jdkExecutor),
                "foo", 123);
    }

    private void thenCombine(CompletableFuture<Double> other, CompletionStage<Integer> result,
                             @Nullable String expectedS, double expectedD)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(expectedD));
        jdkExecutor.execute(() -> source.onSuccess(expectedS));
        assertEquals((int) (strLen(expectedS) + expectedD), result.toCompletableFuture().get().intValue());
    }

    @Test
    public void thenAcceptBoth() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        AtomicReference<Long> lngRef = new AtomicReference<>();
        CompletableFuture<Long> other = new CompletableFuture<>();
        thenAcceptBoth(other, source.toCompletionStage().thenAcceptBoth(other, (str, lng) -> {
            verifyInStExecutorThread();
            strRef.set(str);
            lngRef.set(lng);
        }), "foo", 134L, strRef, lngRef);
    }

    @Test
    public void thenAcceptBothNull() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        AtomicReference<Long> lngRef = new AtomicReference<>();
        CompletableFuture<Long> other = new CompletableFuture<>();
        thenAcceptBoth(other, source.toCompletionStage().thenAcceptBoth(other, (str, lng) -> {
            verifyInStExecutorThread();
            strRef.set(str);
            lngRef.set(lng);
        }), null, 134L, strRef, lngRef);
    }

    @Test
    public void thenAcceptBothAsync() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        AtomicReference<Long> lngRef = new AtomicReference<>();
        CompletableFuture<Long> other = new CompletableFuture<>();
        thenAcceptBoth(other, source.toCompletionStage().thenAcceptBothAsync(other, (str, lng) -> {
            verifyInStExecutorThread();
            strRef.set(str);
            lngRef.set(lng);
        }), "foo", 13434L, strRef, lngRef);
    }

    @Test
    public void thenAcceptBothAsyncExecutor() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        AtomicReference<Long> lngRef = new AtomicReference<>();
        CompletableFuture<Long> other = new CompletableFuture<>();
        thenAcceptBoth(other, source.toCompletionStage().thenAcceptBothAsync(other, (str, lng) -> {
            verifyInJdkExecutorThread();
            strRef.set(str);
            lngRef.set(lng);
        }, jdkExecutor), "foo", 13434L, strRef, lngRef);
    }

    private void thenAcceptBoth(CompletableFuture<Long> other, CompletionStage<Void> result,
                                @Nullable String expectedS, long expectedL,
                                AtomicReference<String> strRef, AtomicReference<Long> lngRef)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(expectedL));
        jdkExecutor.execute(() -> source.onSuccess(expectedS));

        assertNull(result.toCompletableFuture().get());
        assertEquals(expectedS, strRef.get());
        assertEquals(expectedL, lngRef.get().longValue());
    }

    @Test
    public void runAfterBoth() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBoth(other, countDownInStThread(latch)), "foo", 123L,
                latch);
    }

    @Test
    public void runAfterBothNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBoth(other, countDownInStThread(latch)), null, 1223L,
                latch);
    }

    @Test
    public void runAfterBothAsync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBothAsync(other, countDownInStThread(latch)), "bar",
                123L, latch);
    }

    @Test
    public void runAfterBothAsyncExecutor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBothAsync(other, countDownInJdkThread(latch),
                jdkExecutor), "bar", 123L, latch);
    }

    private void runAfterBoth(CompletableFuture<Long> other, CompletionStage<Void> result,
                              @Nullable String expectedS, long expectedL, CountDownLatch latch)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(expectedL));
        jdkExecutor.execute(() -> source.onSuccess(expectedS));

        latch.await();
        assertNull(result.toCompletableFuture().get());
    }

    @Test
    public void applyToEither() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEither(other,
                SingleToCompletionStageTest::strLenStThread), "foo", "bar");
    }

    @Test
    public void applyToEitherNull() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEither(other,
                SingleToCompletionStageTest::strLenStThread), null, null);
    }

    @Test
    public void applyToEitherAsync() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEitherAsync(other,
                SingleToCompletionStageTest::strLenStThread), "foo", "bar");
    }

    @Test
    public void applyToEitherAsyncExecutor() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEitherAsync(other,
                SingleToCompletionStageTest::strLenJdkThread, jdkExecutor), "foo", "bar");
    }

    private void applyToEither(CompletableFuture<String> other, CompletionStage<Integer> result,
                               @Nullable String expected, @Nullable String otherExpected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(otherExpected));
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertThat(result.toCompletableFuture().get(), isOneOf(strLen(otherExpected), strLen(expected)));
    }

    @Test
    public void acceptEither() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        acceptEither(other, source.toCompletionStage().acceptEither(other, stThread(strRef)), strRef, "foo", "bar");
    }

    @Test
    public void acceptEitherNull() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        acceptEither(other, source.toCompletionStage().acceptEither(other, stThread(strRef)), strRef, null, null);
    }

    @Test
    public void acceptEitherAsync() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        acceptEither(other, source.toCompletionStage().acceptEitherAsync(other, stThread(strRef)), strRef, "what",
                "the");
    }

    @Test
    public void acceptEitherAsyncExecutor() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        acceptEither(other, source.toCompletionStage().acceptEitherAsync(other, jdkThread(strRef), jdkExecutor),
                strRef, "what", "the");
    }

    private void acceptEither(CompletableFuture<String> other, CompletionStage<Void> result,
                              AtomicReference<String> strRef,
                              @Nullable String expected, @Nullable String otherExpected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(otherExpected));
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertNull(result.toCompletableFuture().get());
        assertThat(strRef.get(), isOneOf(otherExpected, expected));
    }

    @Test
    public void runAfterEither() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);
        runAfterEither(other, source.toCompletionStage().runAfterEither(other, countDownInStThread(latch)), latch,
                "foo", "bar");
    }

    @Test
    public void runAfterEitherNull() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);
        runAfterEither(other, source.toCompletionStage().runAfterEither(other, countDownInStThread(latch)), latch,
                null, null);
    }

    @Test
    public void runAfterEitherAsync() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);
        runAfterEither(other, source.toCompletionStage().runAfterEitherAsync(other, countDownInStThread(latch)), latch,
                "foo", null);
    }

    @Test
    public void runAfterEitherAsyncExecutor() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);
        runAfterEither(other, source.toCompletionStage().runAfterEitherAsync(other, countDownInJdkThread(latch),
                jdkExecutor), latch, "foo", null);
    }

    private void runAfterEither(CompletableFuture<String> other, CompletionStage<Void> result, CountDownLatch latch,
                                @Nullable String expected, @Nullable String otherExpected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(otherExpected));
        jdkExecutor.execute(() -> source.onSuccess(expected));

        latch.await();
        assertNull(result.toCompletableFuture().get());
    }

    @Test
    public void thenCompose() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenCompose(str -> {
            verifyInStExecutorThread();
            strRef.set(str);
            return other;
        }), strRef, "foo", 10);
    }

    @Test
    public void thenComposeNull() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenCompose(str -> {
            verifyInStExecutorThread();
            strRef.set(str);
            return other;
        }), strRef, null, null);
    }

    @Test
    public void thenComposeAsync() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenComposeAsync(str -> {
            verifyInStExecutorThread();
            strRef.set(str);
            return other;
        }), strRef, "bar", 1234);
    }

    @Test
    public void thenComposeAsyncExecutor() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenComposeAsync(str -> {
            verifyInJdkExecutorThread();
            strRef.set(str);
            return other;
        }, jdkExecutor), strRef, "bar", 1234);
    }

    private void thenCompose(CompletableFuture<Integer> other, CompletionStage<Integer> result,
                             AtomicReference<String> strRef,
                             @Nullable String expected, @Nullable Integer otherExpected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(otherExpected));
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertEquals(otherExpected, result.toCompletableFuture().get());
        assertEquals(expected, strRef.get());
    }

    @Test
    public void exceptionally() throws Exception {
        String expected = "foo";
        exceptionally(source.toCompletionStage().exceptionally(t -> expected), expected);
    }

    @Test
    public void exceptionallyNull() throws Exception {
        exceptionally(source.toCompletionStage().exceptionally(t -> null), null);
    }

    private void exceptionally(CompletionStage<String> result, @Nullable String expected)
            throws ExecutionException, InterruptedException {
        source.onError(DELIBERATE_EXCEPTION);
        assertEquals(expected, result.toCompletableFuture().get());
    }

    @Test
    public void whenComplete() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        whenComplete(source.toCompletionStage().whenComplete((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            latch.countDown();
        }), "foo", strRef, latch);
    }

    @Test
    public void whenCompleteNull() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        whenComplete(source.toCompletionStage().whenComplete((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            latch.countDown();
        }), null, strRef, latch);
    }

    @Test
    public void whenCompleteAsync() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        whenComplete(source.toCompletionStage().whenCompleteAsync((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            latch.countDown();
        }), "foo", strRef, latch);
    }

    @Test
    public void whenCompleteAsyncExecutor() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        whenComplete(source.toCompletionStage().whenCompleteAsync((s, t) -> {
            verifyInJdkExecutorThread();
            strRef.set(s);
            latch.countDown();
        }, jdkExecutor), "foo", strRef, latch);
    }

    private void whenComplete(CompletionStage<String> result, @Nullable String expected,
                              AtomicReference<String> strRef, CountDownLatch latch)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));

        latch.await();
        assertEquals(expected, strRef.get());
        assertEquals(expected, result.toCompletableFuture().get());
    }

    @Test
    public void handle() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        handle(source.toCompletionStage().handle((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            latch.countDown();
            return strLen(s);
        }), "foo", strRef, latch);
    }

    @Test
    public void handleNull() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        handle(source.toCompletionStage().handle((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            latch.countDown();
            return strLen(s);
        }), null, strRef, latch);
    }

    @Test
    public void handleAsync() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        handle(source.toCompletionStage().handleAsync((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            latch.countDown();
            return strLen(s);
        }), null, strRef, latch);
    }

    @Test
    public void handleAsyncExecutor() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        handle(source.toCompletionStage().handleAsync((s, t) -> {
            verifyInJdkExecutorThread();
            strRef.set(s);
            latch.countDown();
            return strLen(s);
        }, jdkExecutor), null, strRef, latch);
    }

    private void handle(CompletionStage<Integer> result, @Nullable String expected,
                        AtomicReference<String> strRef, CountDownLatch latch)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));

        latch.await();
        assertEquals(expected, strRef.get());
        assertEquals(strLen(expected), result.toCompletableFuture().get().intValue());
    }

    @Test
    public void cancellationBeforeListen() throws InterruptedException {
        CompletionStage<String> stage = source.toCompletionStage();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        stage.toCompletableFuture().cancel(true);
        stage.whenComplete((s, t) -> {
            causeRef.set(t);
            latch.countDown();
        });
        assertTrue(latch.await(100, MILLISECONDS));
        source.verifyListenCalled(); // We eagerly subscribe
    }

    @Test
    public void blockingCancellationBeforeListen() throws Exception {
        CompletionStage<String> stage = source.toCompletionStage();
        CompletableFuture<String> future = stage.toCompletableFuture();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        future.cancel(true);
        stage.whenComplete((s, t) -> {
            causeRef.set(t);
            latch.countDown();
        });
        assertTrue(latch.await(100, MILLISECONDS));
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
        source.verifyListenCalled(); // We eagerly subscribe
        thrown.expect(CancellationException.class);
        future.get();
    }

    @Test
    public void cancellationAfterListen() throws InterruptedException {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CompletionStage<String> stage = source.doAfterCancel(cancelLatch::countDown).toCompletionStage();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        stage.whenComplete((s, t) -> {
            causeRef.set(t);
            latch.countDown();
        });
        stage.toCompletableFuture().cancel(true);
        latch.await();
        assertThat(causeRef.get(), is(instanceOf(CancellationException.class)));
        cancelLatch.await();
    }

    @Test
    public void blockingCancellationAfterListen() throws Exception {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CompletionStage<String> stage = source.doAfterCancel(cancelLatch::countDown).toCompletionStage();
        CompletableFuture<String> future = stage.toCompletableFuture();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        stage.whenComplete((s, t) -> {
            causeRef.set(t);
            latch.countDown();
        });
        future.cancel(true);
        latch.await();
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
        assertThat(causeRef.get(), is(instanceOf(CancellationException.class)));
        cancelLatch.await();
        thrown.expect(CancellationException.class);
        future.get();
    }

    @Test
    public void cancellationOnDependentCancelsSource() throws InterruptedException {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CompletionStage<String> stage = source.doAfterCancel(cancelLatch::countDown).toCompletionStage();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        stage = stage.whenComplete((s, t) -> causeRef.compareAndSet(null, t))
                     .whenComplete((s, t) -> causeRef.compareAndSet(null, t));

        stage.whenComplete((s, t) -> {
            causeRef.compareAndSet(null, t);
            latch.countDown();
        });

        stage.toCompletableFuture().cancel(true);
        latch.await();
        assertThat(causeRef.get(), is(instanceOf(CancellationException.class)));
        cancelLatch.await();
    }

    @Test
    public void blockingGetAsync() throws Exception {
        blockingGetAsync(source.toCompletionStage().toCompletableFuture(), "foo");
    }

    @Test
    public void blockingGetAsyncNull() throws Exception {
        blockingGetAsync(source.toCompletionStage().toCompletableFuture(), null);
    }

    @Test
    public void futureGetAsync() throws Exception {
        blockingGetAsync(source.toFuture(), "foo");
    }

    @Test
    public void futureGetAsyncNull() throws Exception {
        blockingGetAsync(source.toFuture(), null);
    }

    private void blockingGetAsync(Future<String> stage, @Nullable String expected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));
        assertEquals(expected, stage.get());
    }

    @Test
    public void blockingGetSync() throws Exception {
        blockingGetSync(source.toCompletionStage().toCompletableFuture(), "foo");
    }

    @Test
    public void blockingGetSyncNull() throws Exception {
        blockingGetSync(source.toCompletionStage().toCompletableFuture(), null);
    }

    @Test
    public void futureGetSync() throws Exception {
        blockingGetSync(source.toFuture(), "foo");
    }

    @Test
    public void futureGetSyncNull() throws Exception {
        blockingGetSync(source.toFuture(), null);
    }

    private void blockingGetSync(Future<String> stage, @Nullable String expected)
            throws ExecutionException, InterruptedException {
        source.onSuccess(expected);
        assertEquals(expected, stage.get());
    }

    @Test
    public void blockingGetAsyncError() throws Exception {
        blockingGetAsyncError(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetAsyncError() throws Exception {
        blockingGetAsyncError(source.toFuture());
    }

    private void blockingGetAsyncError(Future<String> stage) throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onError(DELIBERATE_EXCEPTION));
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        stage.get();
    }

    @Test
    public void blockingGetSyncError() throws Exception {
        blockingGetSyncError(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetSyncError() throws Exception {
        blockingGetSyncError(source.toFuture());
    }

    private void blockingGetSyncError(Future<String> stage) throws ExecutionException, InterruptedException {
        source.onError(DELIBERATE_EXCEPTION);
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        stage.get();
    }

    @Test
    public void blockingGetTimeoutExpire() throws Exception {
        blockingGetTimeoutExpire(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetTimeoutExpire() throws Exception {
        blockingGetTimeoutExpire(source.toFuture());
    }

    private void blockingGetTimeoutExpire(Future<String> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        thrown.expect(TimeoutException.class);
        stage.get(10, MILLISECONDS);
    }

    @Test
    public void blockingGetTimeoutSuccess() throws Exception {
        blockingGetTimeoutSuccess(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetTimeoutSuccess() throws Exception {
        blockingGetTimeoutSuccess(source.toFuture());
    }

    private void blockingGetTimeoutSuccess(Future<String> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        jdkExecutor.execute(() -> source.onSuccess("foo"));
        assertEquals("foo", stage.get(1, MINUTES));
    }

    @Test
    public void blockingGetTimeoutError() throws Exception {
        blockingGetTimeoutError(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetTimeoutError() throws Exception {
        blockingGetTimeoutError(source.toFuture());
    }

    private void blockingGetTimeoutError(Future<String> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        jdkExecutor.execute(() -> source.onError(DELIBERATE_EXCEPTION));
        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        stage.get(1, MINUTES);
    }

    private static <X> void verifyListenerInvokedInJdkThread(CompletionStage<X> stage)
            throws ExecutionException, InterruptedException {
        // Derived stages from thenApplyAsync with a jdkExecutor should invoke listeners on the same jdkExecutor too!
        stage.thenCompose(v -> {
            CompletableFuture<Void> result = new CompletableFuture<>();
            if (currentThread().getName().startsWith(JDK_THREAD_NAME_PREFIX)) {
                result.complete(null);
            } else {
                result.completeExceptionally(
                        new IllegalStateException("unexpected thread: " + currentThread()));
            }
            return result;
        }).toCompletableFuture().get();
    }

    private static void verifyInJdkExecutorThread() {
        if (!currentThread().getName().startsWith(JDK_THREAD_NAME_PREFIX)) {
            throw new IllegalStateException("unexpected thread: " + currentThread());
        }
    }

    private static void verifyInStExecutorThread() {
        if (!currentThread().getName().startsWith(ST_THREAD_PREFIX_NAME)) {
            throw new IllegalStateException("unexpected thread: " + currentThread());
        }
    }

    private static int strLen(@Nullable String str) {
        return str == null ? -1 : str.length();
    }

    private static int strLenStThread(@Nullable String str) {
        verifyInStExecutorThread();
        return strLen(str);
    }

    private static int strLenJdkThread(@Nullable String str) {
        verifyInJdkExecutorThread();
        return strLen(str);
    }

    private static Consumer<? super String> stThread(AtomicReference<String> ref) {
        return val -> {
            verifyInStExecutorThread();
            ref.set(val);
        };
    }

    private static Consumer<? super String> jdkThread(AtomicReference<String> ref) {
        return val -> {
            verifyInJdkExecutorThread();
            ref.set(val);
        };
    }

    private static Runnable trueStThread(AtomicBoolean ref) {
        return () -> {
            verifyInStExecutorThread();
            ref.set(true);
        };
    }

    private static Runnable trueJdkThread(AtomicBoolean ref) {
        return () -> {
            verifyInJdkExecutorThread();
            ref.set(true);
        };
    }

    private static BiFunction<? super String, ? super Double, ? extends Integer> strLenDoubleStThread() {
        return (str, dbl) -> {
            verifyInStExecutorThread();
            return (int) (strLen(str) + dbl);
        };
    }

    private static BiFunction<? super String, ? super Double, ? extends Integer> strLenDoubleJdkThread() {
        return (str, dbl) -> {
            verifyInJdkExecutorThread();
            return (int) (strLen(str) + dbl);
        };
    }

    private static Runnable countDownInStThread(CountDownLatch latch) {
        return () -> {
            verifyInStExecutorThread();
            latch.countDown();
        };
    }

    private static Runnable countDownInJdkThread(CountDownLatch latch) {
        return () -> {
            verifyInJdkExecutorThread();
            latch.countDown();
        };
    }
}
