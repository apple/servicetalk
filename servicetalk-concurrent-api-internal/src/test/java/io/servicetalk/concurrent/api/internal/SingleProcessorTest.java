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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.MockedSingleListenerRule;

import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class SingleProcessorTest {
    @Rule
    public final MockedSingleListenerRule<String> rule = new MockedSingleListenerRule<>();
    @Rule
    public final MockedSingleListenerRule<String> rule2 = new MockedSingleListenerRule<>();

    @Test
    public void testSuccessBeforeListen() {
        testSuccessBeforeListen("foo");
    }

    @Test
    public void testSuccessNullBeforeListen() {
        testSuccessBeforeListen(null);
    }

    private void testSuccessBeforeListen(@Nullable String expected) {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onSuccess(expected);
        rule.listen(processor).verifySuccess(expected);
    }

    @Test
    public void testErrorBeforeListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        rule.listen(processor).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessAfterListen() {
        testSuccessAfterListen("foo");
    }

    @Test
    public void testSuccessNullAfterListen() {
        testSuccessAfterListen("foo");
    }

    private void testSuccessAfterListen(@Nullable String expected) {
        SingleProcessor<String> processor = new SingleProcessor<>();
        rule.listen(processor).verifyNoEmissions();
        processor.onSuccess(expected);
        rule.verifySuccess(expected);
    }

    @Test
    public void testErrorAfterListen() {
        SingleProcessor<String> processor = new SingleProcessor<>();
        rule.listen(processor).verifyNoEmissions();
        processor.onError(DELIBERATE_EXCEPTION);
        rule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSuccessThenError() {
        testSuccessThenError("foo");
    }

    @Test
    public void testSuccessNullThenError() {
        testSuccessThenError(null);
    }

    private void testSuccessThenError(@Nullable String expected) {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onSuccess(expected);
        processor.onError(DELIBERATE_EXCEPTION);
        rule.listen(processor).verifySuccess(expected);
    }

    @Test
    public void testErrorThenSuccess() {
        testErrorThenSuccess("foo");
    }

    @Test
    public void testErrorThenSuccessNull() {
        testErrorThenSuccess(null);
    }

    private void testErrorThenSuccess(@Nullable String expected) {
        SingleProcessor<String> processor = new SingleProcessor<>();
        processor.onError(DELIBERATE_EXCEPTION);
        processor.onSuccess(expected);
        rule.listen(processor).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified("foo");
    }

    @Test
    public void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotifiedWithNull() {
        cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(null);
    }

    private void cancelRemovesListenerAndStillAllowsOtherListenersToBeNotified(@Nullable String expected) {
        SingleProcessor<String> processor = new SingleProcessor<>();
        rule.listen(processor).verifyNoEmissions();
        rule2.listen(processor).verifyNoEmissions();
        rule.cancel();
        processor.onSuccess(expected);
        rule.verifyNoEmissions();
        rule2.verifySuccess(expected);
    }
}
