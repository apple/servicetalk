/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class SingleProcessorTest {
    @Rule
    public final MockedSingleListenerRule<?> rule = new MockedSingleListenerRule<>();
    @Rule
    public final MockedSingleListenerRule<?> rule2 = new MockedSingleListenerRule<>();

    @SuppressWarnings("unchecked")
    private <T> MockedSingleListenerRule<T> rule() {
        return (MockedSingleListenerRule<T>) rule;
    }

    @SuppressWarnings("unchecked")
    private <T> MockedSingleListenerRule<T> rule2() {
        return (MockedSingleListenerRule<T>) rule2;
    }

    @Test
    public void testSuccessBeforeListen() {
        testSuccessBeforeListen("foo");
    }

    @Test
    public void testSuccessThrowableBeforeListen() {
        testSuccessBeforeListen(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessNullBeforeListen() {
        testSuccessBeforeListen(null);
    }

    private <T> void testSuccessBeforeListen(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        processor.onSuccess(expected);
        rule().listen(processor).verifySuccess(expected);
    }

    @Test
    public void testErrorBeforeListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        rule().listen(processor).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessAfterListen() {
        testSuccessAfterListen("foo");
    }

    @Test
    public void testSuccessNullAfterListen() {
        testSuccessAfterListen(null);
    }

    @Test
    public void testSuccessThrowableAfterListen() {
        testSuccessAfterListen(DELIBERATE_EXCEPTION);
    }

    private <T> void testSuccessAfterListen(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        rule().listen(processor).verifyNoEmissions();
        processor.onSuccess(expected);
        rule().verifySuccess(expected);
    }

    @Test
    public void testErrorAfterListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        rule().listen(processor).verifyNoEmissions();
        processor.onError(DELIBERATE_EXCEPTION);
        rule().verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessThenError() {
        testSuccessThenError("foo");
    }

    @Test
    public void testSuccessThrowableThenError() {
        testSuccessThenError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessNullThenError() {
        testSuccessThenError(null);
    }

    private <T> void testSuccessThenError(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        processor.onSuccess(expected);
        processor.onError(DELIBERATE_EXCEPTION);
        rule().listen(processor).verifySuccess(expected);
    }

    @Test
    public void testErrorThenSuccess() {
        testErrorThenSuccess("foo");
    }

    @Test
    public void testErrorThenSuccessThrowable() {
        testErrorThenSuccess(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testErrorThenSuccessNull() {
        testErrorThenSuccess(null);
    }

    private <T> void testErrorThenSuccess(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        processor.onSuccess(expected);
        rule().listen(processor).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified("foo");
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithNull() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(null);
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithThrowable() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(DELIBERATE_EXCEPTION);
    }

    private <T> void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(@Nullable T expected) {
        SingleProcessor<T> processor = new SingleProcessor<>();
        rule().listen(processor).verifyNoEmissions();
        rule2().listen(processor).verifyNoEmissions();
        rule().cancel();
        processor.onSuccess(expected);
        rule().verifyNoEmissions();
        rule2().verifySuccess(expected);
    }
}
