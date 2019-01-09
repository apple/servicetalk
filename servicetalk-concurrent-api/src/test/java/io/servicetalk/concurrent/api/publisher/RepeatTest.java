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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.TestPublisher;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.function.IntPredicate;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RepeatTest {

    @Rule
    public final MockedSubscriberRule<Integer> subscriberRule = new MockedSubscriberRule<>();

    private TestPublisher<Integer> source;
    private IntPredicate shouldRepeat;
    private boolean shouldRepeatValue;

    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<>(true);
        source.sendOnSubscribe();
        shouldRepeat = mock(IntPredicate.class);
        when(shouldRepeat.test(anyInt())).thenAnswer(invocation -> shouldRepeatValue);
        subscriberRule.subscribe(source.repeat(shouldRepeat));
    }

    @Test
    public void testError() {
        subscriberRule.request(2);
        source.sendItems(1, 2).fail();
        subscriberRule.verifyItems(1, 2).verifyFailure(DELIBERATE_EXCEPTION);
        verifyZeroInteractions(shouldRepeat);
    }

    @Test
    public void testRepeatCount() {
        subscriberRule.request(2);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2).verifySuccess();
        verify(shouldRepeat).test(1);
        verifyNoMoreInteractions(shouldRepeat);
    }

    @Test
    public void testRequestAcrossRepeat() {
        subscriberRule.request(3);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2);
        verify(shouldRepeat).test(1);
        source.verifySubscribed().sendItems(3);
        subscriberRule.verifySuccess(3);
    }

    @Test
    public void testTwoCompletes() {
        shouldRepeatValue = true;
        subscriberRule.request(3);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2).verifyNoEmissions();
        verify(shouldRepeat).test(1);
        source.verifySubscribed();
        source.sendItems(3).onComplete();
        verify(shouldRepeat).test(2);
        source.fail();
        subscriberRule.verifyItems(1, 2, 3).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testMaxRepeats() {
        shouldRepeatValue = true;
        subscriberRule.request(3);
        source.sendItems(1, 2).onComplete();
        subscriberRule.verifyItems(1, 2).verifyNoEmissions();
        verify(shouldRepeat).test(1);
        shouldRepeatValue = false;
        source.onComplete();
        subscriberRule.verifySuccess();
    }

    @Test
    public void testCancel() {
        subscriberRule.request(2);
        source.sendItems(1, 2);
        subscriberRule.verifyItems(1, 2).cancel();
        source.onComplete();
        source.verifyCancelled();
    }
}
