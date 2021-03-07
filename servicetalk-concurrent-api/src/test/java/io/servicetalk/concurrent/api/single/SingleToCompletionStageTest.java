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

import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

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

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(TimeoutTracingInfoExtension.class)
public class SingleToCompletionStageTest {
    @RegisterExtension
    public final ExecutorExtension executorExtension = ExecutorExtension.withNamePrefix(ST_THREAD_PREFIX_NAME);

    private LegacyTestSingle<String> source;
    private static ExecutorService jdkExecutor;
    private static final AtomicInteger threadCount = new AtomicInteger();
    private static final String ST_THREAD_PREFIX_NAME = "st-exec-thread";
    private static final String JDK_THREAD_NAME_PREFIX = "jdk-thread";
    private static final String JDK_FORK_JOIN_THREAD_NAME_PREFIX = "ForkJoinPool";
    private static final String COMPLETABLE_FUTURE_THREAD_PER_TASK_NAME_PREFIX = "Thread-";
    private static final String JUNIT_THREAD_PREFIX = "Test worker";

    @BeforeAll
    public static void beforeClass() {
        jdkExecutor = java.util.concurrent.Executors.newCachedThreadPool(
                r -> new Thread(r, JDK_THREAD_NAME_PREFIX + '-' + threadCount.incrementAndGet()));
    }

    @AfterAll
    public static void afterClass() {
        if (jdkExecutor != null) {
            jdkExecutor.shutdown();
        }
    }

    @BeforeEach
    public void beforeTest() {
        source = new LegacyTestSingle<>(executorExtension.executor(), true, true);
    }

    @Test
    public void completableFutureFromSingleToCompletionStageToCompletableFutureFailure() {
        CompletableFuture<Long> input = new CompletableFuture<>();
        CompletableFuture<Long> output = Single.fromStage(input).toCompletionStage().toCompletableFuture()
                .whenComplete((v, c) -> { })
                .thenApply(l -> l + 1)
                .whenComplete((v, c) -> { });
        input.completeExceptionally(DELIBERATE_EXCEPTION);
        ExecutionException e = assertThrows(ExecutionException.class, () -> output.get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
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
                .thenCompose(s -> succeeded(s + "bar").toCompletionStage())
                .get();
        assertThat("Unexpected result.", result, is("foobar"));
    }

    @Test
    public void thenApply() throws Exception {
        thenApply(source.toCompletionStage().thenApply(SingleToCompletionStageTest::strLenStThread), "thenApply");
    }

    @Test
    public void thenApplyNull() throws Exception {
        thenApply(source.toCompletionStage().thenApplyAsync(SingleToCompletionStageTest::strLenJdkForkJoinThread),
                null);
    }

    @Test
    public void thenApplyAsync() throws Exception {
        thenApply(source.toCompletionStage().thenApplyAsync(SingleToCompletionStageTest::strLenJdkForkJoinThread),
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
            verifyInStOrJUnitThread();
            return in + " two";
        });
        assertEquals(expected2, stage2.toCompletableFuture().get());
    }

    @Test
    public void thenApplyTransformData() throws Exception {
        thenApplyTransformData(stage1 -> stage1.thenApply(SingleToCompletionStageTest::strLenJUnitThread));
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
        thenAccept(source.toCompletionStage().thenAcceptAsync(jdkForkJoinThread(stringRef)), stringRef,
                "thenAcceptAsync");
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
        thenAcceptConsumeData((stage1, ref) -> stage1.thenAccept(junitThread(ref)));
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
        thenRun(source.toCompletionStage().thenRunAsync(trueJdkForkJoinThread(aBoolean)), aBoolean, "thenRunAsync");
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
        thenRunAfterData((stage1, ref) -> stage1.thenRun(trueJUnitThread(ref)));
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
        thenCombine(other, source.toCompletionStage().thenCombine(other, strLenDoubleStOrJdkThread()), "foo", 123);
    }

    @Test
    public void thenCombineNull() throws Exception {
        CompletableFuture<Double> other = new CompletableFuture<>();
        thenCombine(other, source.toCompletionStage().thenCombine(other, strLenDoubleStOrJdkThread()), null, 1236);
    }

    @Test
    public void thenCombineAsync() throws Exception {
        CompletableFuture<Double> other = new CompletableFuture<>();
        thenCombine(other, source.toCompletionStage().thenCombineAsync(other, strLenDoubleJdkForkJoinThread()), "foo",
                123);
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
            verifyInStOrJdkThread();
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
            verifyInStOrJdkThread();
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
            verifyInJdkForkJoinThread();
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
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBoth(other,
                SingleToCompletionStageTest::verifyInStOrJdkThread), "foo", 123L);
    }

    @Test
    public void runAfterBothNull() throws Exception {
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBoth(other,
                SingleToCompletionStageTest::verifyInStOrJdkThread), null, 1223L);
    }

    @Test
    public void runAfterBothAsync() throws Exception {
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBothAsync(other,
                SingleToCompletionStageTest::verifyInJdkForkJoinThread), "bar", 123L);
    }

    @Test
    public void runAfterBothAsyncExecutor() throws Exception {
        CompletableFuture<Long> other = new CompletableFuture<>();
        runAfterBoth(other, source.toCompletionStage().runAfterBothAsync(other,
                SingleToCompletionStageTest::verifyInJdkExecutorThread, jdkExecutor), "bar", 123L);
    }

    private void runAfterBoth(CompletableFuture<Long> other, CompletionStage<Void> result,
                              @Nullable String expectedS, long expectedL)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(expectedL));
        jdkExecutor.execute(() -> source.onSuccess(expectedS));

        assertNull(result.toCompletableFuture().get());
    }

    @Test
    public void applyToEither() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEither(other,
                SingleToCompletionStageTest::strLenStOrJdkThread), "foo", "bar");
    }

    @Test
    public void applyToEitherNull() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEither(other,
                SingleToCompletionStageTest::strLenStOrJdkThread), null, null);
    }

    @Test
    public void applyToEitherAsync() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        applyToEither(other, source.toCompletionStage().applyToEitherAsync(other,
                SingleToCompletionStageTest::strLenJdkForkJoinThread), "foo", "bar");
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
        acceptEither(other, source.toCompletionStage().acceptEither(other, stOrJdkThread(strRef)), strRef, "foo",
                "bar");
    }

    @Test
    public void acceptEitherNull() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        acceptEither(other, source.toCompletionStage().acceptEither(other, stOrJdkThread(strRef)), strRef, null, null);
    }

    @Test
    public void acceptEitherAsync() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        acceptEither(other, source.toCompletionStage().acceptEitherAsync(other, jdkForkJoinThread(strRef)), strRef,
                "what", "the");
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
        runAfterEither(other, source.toCompletionStage().runAfterEither(other,
                SingleToCompletionStageTest::verifyInStOrJdkThread), "foo", "bar");
    }

    @Test
    public void runAfterEitherNull() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        runAfterEither(other, source.toCompletionStage().runAfterEither(other,
                SingleToCompletionStageTest::verifyInStOrJdkThread), null, null);
    }

    @Test
    public void runAfterEitherAsync() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        runAfterEither(other, source.toCompletionStage().runAfterEitherAsync(other,
                SingleToCompletionStageTest::verifyInJdkForkJoinThread), "foo", null);
    }

    @Test
    public void runAfterEitherAsyncExecutor() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        runAfterEither(other, source.toCompletionStage().runAfterEitherAsync(other,
                SingleToCompletionStageTest::verifyInJdkExecutorThread, jdkExecutor), "foo", null);
    }

    private void runAfterEither(CompletableFuture<String> other, CompletionStage<Void> result,
                                @Nullable String expected, @Nullable String otherExpected)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> other.complete(otherExpected));
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertNull(result.toCompletableFuture().get());
    }

    @Test
    public void thenCompose() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenCompose(str -> {
            verifyInStOrJdkThread();
            strRef.set(str);
            return other;
        }), strRef, "foo", 10);
    }

    @Test
    public void thenComposeNull() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenCompose(str -> {
            verifyInStOrJdkThread();
            strRef.set(str);
            return other;
        }), strRef, null, null);
    }

    @Test
    public void thenComposeAsync() throws Exception {
        CompletableFuture<Integer> other = new CompletableFuture<>();
        AtomicReference<String> strRef = new AtomicReference<>();
        thenCompose(other, source.toCompletionStage().thenComposeAsync(str -> {
            verifyInJdkForkJoinThread();
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
        whenComplete(source.toCompletionStage().whenComplete((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
        }), "foo", strRef);
    }

    @Test
    public void whenCompleteNull() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        whenComplete(source.toCompletionStage().whenComplete((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
        }), null, strRef);
    }

    @Test
    public void whenCompleteAsync() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        whenComplete(source.toCompletionStage().whenCompleteAsync((s, t) -> {
            verifyInJdkForkJoinThread();
            strRef.set(s);
        }), "foo", strRef);
    }

    @Test
    public void whenCompleteAsyncExecutor() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        whenComplete(source.toCompletionStage().whenCompleteAsync((s, t) -> {
            verifyInJdkExecutorThread();
            strRef.set(s);
        }, jdkExecutor), "foo", strRef);
    }

    private void whenComplete(CompletionStage<String> result, @Nullable String expected,
                              AtomicReference<String> strRef)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertEquals(expected, result.toCompletableFuture().get());
        assertEquals(expected, strRef.get());
    }

    @Test
    public void handle() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        handle(source.toCompletionStage().handle((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            return strLen(s);
        }), "foo", strRef);
    }

    @Test
    public void handleNull() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        handle(source.toCompletionStage().handle((s, t) -> {
            verifyInStExecutorThread();
            strRef.set(s);
            return strLen(s);
        }), null, strRef);
    }

    @Test
    public void handleAsync() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        handle(source.toCompletionStage().handleAsync((s, t) -> {
            verifyInJdkForkJoinThread();
            strRef.set(s);
            return strLen(s);
        }), null, strRef);
    }

    @Test
    public void handleAsyncExecutor() throws Exception {
        AtomicReference<String> strRef = new AtomicReference<>();
        handle(source.toCompletionStage().handleAsync((s, t) -> {
            verifyInJdkExecutorThread();
            strRef.set(s);
            return strLen(s);
        }, jdkExecutor), null, strRef);
    }

    private void handle(CompletionStage<Integer> result, @Nullable String expected,
                        AtomicReference<String> strRef)
            throws ExecutionException, InterruptedException {
        jdkExecutor.execute(() -> source.onSuccess(expected));

        assertEquals(strLen(expected), result.toCompletableFuture().get().intValue());
        assertEquals(expected, strRef.get());
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
    }

    @Test
    public void blockingCancellationBeforeListen() {
        assertThrows(CancellationException.class,
                () -> {

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
                    future.get();
                });
    }

    @Test
    public void cancellationAfterListen() throws InterruptedException {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CompletionStage<String> stage = source.afterCancel(cancelLatch::countDown).toCompletionStage();
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
    public void blockingCancellationAfterListen() {
        assertThrows(CancellationException.class, () -> {
            CountDownLatch cancelLatch = new CountDownLatch(1);
            CompletionStage<String> stage = source.afterCancel(cancelLatch::countDown).toCompletionStage();
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
            future.get();
        });
    }

    @Test
    public void cancellationOnDependentCancelsSource() throws InterruptedException {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CompletionStage<String> stage = source.afterCancel(cancelLatch::countDown).toCompletionStage();
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
    public void synchronousNormalTerminationDoesNotCancel() {
        AtomicInteger cancelCount = new AtomicInteger();
        String expected = "done";
        Single<String> single = succeeded(expected).whenCancel(cancelCount::incrementAndGet);
        assertThat(single.toCompletionStage().toCompletableFuture().join(), is(expected));
        assertThat(cancelCount.get(), is(0));
    }

    @Test
    public void asynchronousNormalTerminationDoesNotCancel() {
        AtomicInteger cancelCount = new AtomicInteger();
        String expected = "done";
        CompletableFuture<String> future = source.whenCancel(cancelCount::incrementAndGet)
                .toCompletionStage().toCompletableFuture();
        jdkExecutor.execute(() -> source.onSuccess(expected));
        assertThat(future.join(), is(expected));
        assertThat(cancelCount.get(), is(0));
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
    public void blockingGetAsyncError() {
        blockingGetAsyncError(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetAsyncError() {
        blockingGetAsyncError(source.toFuture());
    }

    private void blockingGetAsyncError(Future<String> stage) {
        Exception e = assertThrows(ExecutionException.class, () -> {
            jdkExecutor.execute(() -> source.onError(DELIBERATE_EXCEPTION));
            stage.get();
        });
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void blockingGetSyncError() {
        blockingGetSyncError(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetSyncError() {
        blockingGetSyncError(source.toFuture());
    }

    private void blockingGetSyncError(Future<String> stage) {
        Exception e = assertThrows(ExecutionException.class, () -> {
            source.onError(DELIBERATE_EXCEPTION);
            stage.get();
        });
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void blockingGetTimeoutExpire() {
        blockingGetTimeoutExpire(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetTimeoutExpire() {
        blockingGetTimeoutExpire(source.toFuture());
    }

    private void blockingGetTimeoutExpire(Future<String> stage) {
        assertThrows(TimeoutException.class, () -> {
            stage.get(10, MILLISECONDS);
        });
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
    public void blockingGetTimeoutError() {
        blockingGetTimeoutError(source.toCompletionStage().toCompletableFuture());
    }

    @Test
    public void futureGetTimeoutError() {
        blockingGetTimeoutError(source.toFuture());
    }

    private void blockingGetTimeoutError(Future<String> stage) {
        Exception e = assertThrows(ExecutionException.class, () -> {
            jdkExecutor.execute(() -> source.onError(DELIBERATE_EXCEPTION));
            stage.get(1, MINUTES);
        });
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    private static <X> void verifyListenerInvokedInJdkThread(CompletionStage<X> stage)
            throws ExecutionException, InterruptedException {
        // Derived stages from thenApplyAsync with a jdkExecutor should invoke listeners on the same jdkExecutor too!
        stage.thenCompose(v -> {
            CompletableFuture<Void> result = new CompletableFuture<>();
            if (currentThread().getName().startsWith(JUNIT_THREAD_PREFIX)) {
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

    private static void verifyInJdkForkJoinThread() {
        Thread currentThread = currentThread();
        if (!currentThread.getName().startsWith(JDK_FORK_JOIN_THREAD_NAME_PREFIX) &&
            // CompletableFuture may use a ThreadPerTaskExecutor executor if available processors is low (e.g. 1).
            !currentThread.getName().startsWith(COMPLETABLE_FUTURE_THREAD_PER_TASK_NAME_PREFIX)) {
            throw new IllegalStateException("unexpected thread: " + currentThread());
        }
    }

    private static void verifyInJUnitThread() {
        if (!currentThread().getName().startsWith(JUNIT_THREAD_PREFIX)) {
            throw new IllegalStateException("unexpected thread: " + currentThread());
        }
    }

    private static void verifyInStOrJdkThread() {
        if (!currentThread().getName().startsWith(ST_THREAD_PREFIX_NAME) &&
                !currentThread().getName().startsWith(JDK_THREAD_NAME_PREFIX)) {
            throw new IllegalStateException("unexpected thread: " + currentThread());
        }
    }

    private static void verifyInStOrJUnitThread() {
        if (!currentThread().getName().startsWith(ST_THREAD_PREFIX_NAME) &&
                !currentThread().getName().startsWith(JUNIT_THREAD_PREFIX)) {
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

    private static int strLenJdkForkJoinThread(@Nullable String str) {
        verifyInJdkForkJoinThread();
        return strLen(str);
    }

    private static int strLenJUnitThread(@Nullable String str) {
        verifyInJUnitThread();
        return strLen(str);
    }

    private static int strLenStOrJdkThread(@Nullable String str) {
        verifyInStOrJdkThread();
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

    private static Consumer<? super String> jdkForkJoinThread(AtomicReference<String> ref) {
        return val -> {
            verifyInJdkForkJoinThread();
            ref.set(val);
        };
    }

    private static Consumer<? super String> junitThread(AtomicReference<String> ref) {
        return val -> {
            verifyInJUnitThread();
            ref.set(val);
        };
    }

    private static Consumer<? super String> stOrJdkThread(AtomicReference<String> ref) {
        return val -> {
            verifyInStOrJdkThread();
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

    private static Runnable trueJdkForkJoinThread(AtomicBoolean ref) {
        return () -> {
            verifyInJdkForkJoinThread();
            ref.set(true);
        };
    }

    private static Runnable trueJUnitThread(AtomicBoolean ref) {
        return () -> {
            verifyInJUnitThread();
            ref.set(true);
        };
    }

    private static BiFunction<? super String, ? super Double, ? extends Integer> strLenDoubleStOrJdkThread() {
        return (str, dbl) -> {
            verifyInStOrJdkThread();
            return (int) (strLen(str) + dbl);
        };
    }

    private static BiFunction<? super String, ? super Double, ? extends Integer> strLenDoubleJdkThread() {
        return (str, dbl) -> {
            verifyInJdkExecutorThread();
            return (int) (strLen(str) + dbl);
        };
    }

    private static BiFunction<? super String, ? super Double, ? extends Integer> strLenDoubleJdkForkJoinThread() {
        return (str, dbl) -> {
            verifyInJdkForkJoinThread();
            return (int) (strLen(str) + dbl);
        };
    }
}
