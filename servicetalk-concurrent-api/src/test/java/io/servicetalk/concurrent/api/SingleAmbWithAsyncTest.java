/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncContextMap.Key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleAmbWithAsyncTest {
    private static final String FIRST_EXECUTOR_THREAD_NAME_PREFIX = "first";
    private static final String SECOND_EXECUTOR_THREAD_NAME_PREFIX = "second";

    private static final Key<Integer> BEFORE_SUBSCRIBE_KEY = Key.newKey("before-subscribe");
    private static final int BEFORE_SUBSCRIBE_KEY_VAL = 1;
    private static final int BEFORE_SUBSCRIBE_KEY_VAL_2 = 2;
    private static final Key<Integer> BEFORE_ON_SUBSCRIBE_KEY = Key.newKey("before-on-subscribe");
    private static final int BEFORE_ON_SUBSCRIBE_KEY_VAL = 2;
    private static final int BEFORE_ON_SUBSCRIBE_KEY_VAL_2 = 3;

    @RegisterExtension
    final ExecutorExtension<Executor> firstExec = withCachedExecutor(FIRST_EXECUTOR_THREAD_NAME_PREFIX);
    @RegisterExtension
    final ExecutorExtension<Executor> secondExec = withCachedExecutor(SECOND_EXECUTOR_THREAD_NAME_PREFIX);

    @Test
    void offloadSuccessFromFirst() throws Exception {
        assertThat("Unexpected result.",
                testOffloadSecond(FIRST_EXECUTOR_THREAD_NAME_PREFIX, succeeded(1), never()), is(1));
    }

    private static void assertThrowsWithDeliberateExceptionAsCause(Executable executable) {
        Exception exception = assertThrows(Exception.class, executable);
        assertThat(exception.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void offloadErrorFromFirst() {
        assertThrowsWithDeliberateExceptionAsCause(() ->
                testOffloadSecond(SECOND_EXECUTOR_THREAD_NAME_PREFIX, never(), failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void offloadSuccessFromSecond() throws Exception {
        assertThat("Unexpected result.",
                testOffloadSecond(SECOND_EXECUTOR_THREAD_NAME_PREFIX, never(), succeeded(2)), is(2));
    }

    @Test
    void offloadErrorFromSecond() {
        assertThrowsWithDeliberateExceptionAsCause(() ->
                testOffloadSecond(SECOND_EXECUTOR_THREAD_NAME_PREFIX, never(), failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void contextFromSubscribeFirstSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromSubscribe(succeeded(1), never()), is(1));
    }

    @Test
    void contextFromSubscribeFirstError() {
        assertThrowsWithDeliberateExceptionAsCause(
                () -> testContextFromSubscribe(failed(DELIBERATE_EXCEPTION), never()));
    }

    @Test
    void contextFromSubscribeSecondSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromSubscribe(never(), succeeded(2)), is(2));
    }

    @Test
    void contextFromSubscribeSecondError() {
        assertThrowsWithDeliberateExceptionAsCause(
                () -> testContextFromSubscribe(never(), failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void contextFromSecondSubscribeFirstSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromSecondSubscribe(succeeded(1), never()), is(1));
    }

    @Test
    void contextFromSecondSubscribeFirstError() {
        assertThrowsWithDeliberateExceptionAsCause(() ->
                testContextFromSecondSubscribe(failed(DELIBERATE_EXCEPTION), never()));
    }

    @Test
    void contextFromSecondSubscribeSecondSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromSecondSubscribe(never(), succeeded(2)), is(2));
    }

    @Test
    void contextFromSecondSubscribeSecondError() {
        assertThrowsWithDeliberateExceptionAsCause(() ->
                testContextFromSecondSubscribe(never(), failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void contextFromOnSubscribeFirstSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromOnSubscribe(succeeded(1), never()), is(1));
    }

    @Test
    void contextFromOnSubscribeFirstError() {
        assertThrowsWithDeliberateExceptionAsCause(
                () -> testContextFromOnSubscribe(failed(DELIBERATE_EXCEPTION), never()));
    }

    @Test
    void contextFromOnSubscribeSecondSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromOnSubscribe(never(), succeeded(2)), is(2));
    }

    @Test
    void contextFromOnSubscribeSecondError() {
        assertThrowsWithDeliberateExceptionAsCause(
                () -> testContextFromOnSubscribe(never(), failed(DELIBERATE_EXCEPTION)));
    }

    @Test
    void contextFromSecondOnSubscribeFirstSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromSecondOnSubscribe(succeeded(1), never()), is(1));
    }

    @Test
    void contextFromSecondOnSubscribeFirstError() {
        assertThrowsWithDeliberateExceptionAsCause(() ->
                testContextFromSecondOnSubscribe(failed(DELIBERATE_EXCEPTION), never()));
    }

    @Test
    void contextFromSecondOnSubscribeSecondSuccess() throws Exception {
        assertThat("Unexpected result.", testContextFromSecondOnSubscribe(never(), succeeded(2)), is(2));
    }

    @Test
    void contextFromSecondOnSubscribeSecondError() {
        assertThrowsWithDeliberateExceptionAsCause(() ->
                testContextFromSecondOnSubscribe(never(), failed(DELIBERATE_EXCEPTION)));
    }

    private int testOffloadSecond(String completeOn, final Single<Integer> first, final Single<Integer> second)
            throws Exception {
        return first.publishOn(firstExec.executor())
                .ambWith(second.publishOn(secondExec.executor()))
                .beforeFinally(() ->
                        assertThat("Unexpected thread.", Thread.currentThread().getName(),
                                startsWith(completeOn)))
                .toFuture().get();
    }

    private int testContextFromSubscribe(final Single<Integer> first, final Single<Integer> second) throws Exception {
        return first.publishOn(firstExec.executor())
                .ambWith(second.publishOn(secondExec.executor()))
                .beforeFinally(() ->
                        assertThat("Unexpected context value.", AsyncContext.current().get(BEFORE_SUBSCRIBE_KEY),
                                is(BEFORE_SUBSCRIBE_KEY_VAL)))
                .<Integer>liftSync(subscriber -> {
                    AsyncContext.put(BEFORE_SUBSCRIBE_KEY, BEFORE_SUBSCRIBE_KEY_VAL);
                    return subscriber;
                })
                .toFuture().get();
    }

    private int testContextFromOnSubscribe(final Single<Integer> first, final Single<Integer> second) throws Exception {
        return first.beforeOnSubscribe(__ -> AsyncContext.put(BEFORE_ON_SUBSCRIBE_KEY, BEFORE_ON_SUBSCRIBE_KEY_VAL))
                .publishOn(firstExec.executor())
                .ambWith(second.publishOn(secondExec.executor()))
                .beforeFinally(() ->
                        assertThat("Unexpected context value.",
                                AsyncContext.current().get(BEFORE_ON_SUBSCRIBE_KEY),
                                is(BEFORE_ON_SUBSCRIBE_KEY_VAL)))
                .toFuture().get();
    }

    private int testContextFromSecondSubscribe(final Single<Integer> first, final Single<Integer> second)
            throws Exception {
        return first.publishOn(firstExec.executor())
                .ambWith(second.publishOn(secondExec.executor())
                        .liftSync(subscriber -> {
                            // Modify value for the same key, this update should not be visible back in the original
                            // chain
                            AsyncContext.put(BEFORE_SUBSCRIBE_KEY, BEFORE_SUBSCRIBE_KEY_VAL_2);
                            return subscriber;
                        })
                )
                .beforeFinally(() ->
                        assertThat("Unexpected context value.", AsyncContext.current().get(BEFORE_SUBSCRIBE_KEY),
                                is(BEFORE_SUBSCRIBE_KEY_VAL)))
                .<Integer>liftSync(subscriber -> {
                    AsyncContext.put(BEFORE_SUBSCRIBE_KEY, BEFORE_SUBSCRIBE_KEY_VAL);
                    return subscriber;
                })
                .toFuture().get();
    }

    private int testContextFromSecondOnSubscribe(final Single<Integer> first, final Single<Integer> second)
            throws Exception {
        return first.beforeOnSubscribe(__ -> AsyncContext.put(BEFORE_ON_SUBSCRIBE_KEY, BEFORE_ON_SUBSCRIBE_KEY_VAL))
                .publishOn(firstExec.executor())
                .ambWith(second.publishOn(secondExec.executor())
                        .beforeOnSubscribe(__ ->
                                AsyncContext.put(BEFORE_ON_SUBSCRIBE_KEY, BEFORE_ON_SUBSCRIBE_KEY_VAL_2)))
                .beforeFinally(() ->
                        assertThat("Unexpected context value.",
                                AsyncContext.current().get(BEFORE_ON_SUBSCRIBE_KEY),
                                is(BEFORE_ON_SUBSCRIBE_KEY_VAL)))
                .toFuture().get();
    }
}
