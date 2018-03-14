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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import org.junit.Rule;
import org.junit.Test;

import java.util.function.IntFunction;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RepeatWhenTest {

    @Rule
    public final MockedSubscriberRule<Integer> subscriberRule = new MockedSubscriberRule<>();
    private TestPublisher<Integer> source;
    private IntFunction<Completable> shouldRepeat;
    private TestCompletable repeatSignal;

    @Test
    public void testError() {
        init(true);
        subscriberRule.request(2);
        source.sendItems(1, 2).fail();
        subscriberRule.verifyItems(1, 2).verifyFailure(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(shouldRepeat);
    }

    @Test
    public void testRepeatCount() {
        init(true);
        subscriberRule.request(2);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2);
        repeatSignal.onError(DELIBERATE_EXCEPTION); // stop repeat
        subscriberRule.verifySuccess();
        verify(shouldRepeat).apply(1);
    }

    @Test
    public void testRequestAcrossRepeat() {
        init(true);
        subscriberRule.request(3);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2);
        repeatSignal.onComplete(); // trigger repeat
        verify(shouldRepeat).apply(1);
        source.verifySubscribed().sendItems(3);
        subscriberRule.verifyItems(3).verifyNoEmissions();
    }

    @Test
    public void testTwoCompletes() {
        init(true);
        subscriberRule.request(3);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2).verifyNoEmissions();
        verify(shouldRepeat).apply(1);
        repeatSignal.onComplete(); // trigger repeat
        source.verifySubscribed();
        source.sendItems(3).onComplete();
        verify(shouldRepeat).apply(2);
        repeatSignal.onComplete(); // trigger repeat
        source.fail();
        subscriberRule.verifyItems(1, 2, 3).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testMaxRepeats() {
        init(true);
        subscriberRule.request(3);
        source.sendItems(1, 2).onComplete();
        repeatSignal.onComplete(); // trigger repeat
        subscriberRule.verifyItems(1, 2).verifyNoEmissions();
        verify(shouldRepeat).apply(1);
        source.verifySubscribed().onComplete();
        repeatSignal.verifyListenCalled().onError(DELIBERATE_EXCEPTION); // stop repeat
        subscriberRule.verifySuccess();
    }

    @Test
    public void testCancelPostCompleteButBeforeRetryStart() {
        init(false);
        subscriberRule.request(2);
        source.sendItems(1, 2).onComplete();
        repeatSignal.verifyListenCalled();
        subscriberRule.verifyItems(1, 2).cancel();
        repeatSignal.verifyCancelled();
        source.verifyNotSubscribed();
        verify(shouldRepeat).apply(1);
    }

    @Test
    public void testCancelBeforeRetry() {
        init(true);
        subscriberRule.request(2);
        source.sendItems(1, 2);
        subscriberRule.verifyItems(1, 2).cancel();
        source.onComplete();
        source.verifyCancelled();
    }

    @SuppressWarnings("unchecked")
    private void init(boolean preserveSubscriber) {
        source = new TestPublisher<>(preserveSubscriber);
        source.sendOnSubscribe();
        shouldRepeat = (IntFunction<Completable>) mock(IntFunction.class);
        repeatSignal = new TestCompletable();
        when(shouldRepeat.apply(anyInt())).thenAnswer(invocation -> {
            repeatSignal = new TestCompletable();
            return repeatSignal;
        });
        subscriberRule.subscribe(source.repeatWhen(shouldRepeat));
    }
}
