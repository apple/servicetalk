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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.Before;
import org.junit.Test;

import java.util.function.IntPredicate;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RepeatTest {

    private final TestPublisherSubscriber<Integer> subscriber = newTestPublisherSubscriber();
    private final TestPublisher<Integer> source = new TestPublisher<>();
    private final IntPredicate shouldRepeat = mock(IntPredicate.class);
    private boolean shouldRepeatValue;

    @Before
    public void setUp() throws Exception {
        when(shouldRepeat.test(anyInt())).thenAnswer(invocation -> shouldRepeatValue);
        toSource(source.repeat(shouldRepeat)).subscribe(subscriber);
    }

    @Test
    public void testError() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        verifyZeroInteractions(shouldRepeat);
    }

    @Test
    public void testRepeatCount() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        assertTrue(subscriber.isCompleted());
        verify(shouldRepeat).test(1);
        verifyNoMoreInteractions(shouldRepeat);
    }

    @Test
    public void testRequestAcrossRepeat() {
        shouldRepeatValue = true;
        subscriber.request(3);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        verify(shouldRepeat).test(1);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        assertThat(subscriber.items(), contains(1, 2, 3));
        assertFalse(subscriber.isTerminated());
    }

    @Test
    public void testTwoCompletes() {
        shouldRepeatValue = true;
        subscriber.request(3);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        assertFalse(subscriber.isTerminated());
        verify(shouldRepeat).test(1);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        source.onComplete();
        verify(shouldRepeat).test(2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2, 3));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testMaxRepeats() {
        shouldRepeatValue = true;
        subscriber.request(3);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        assertFalse(subscriber.isTerminated());
        verify(shouldRepeat).test(1);
        shouldRepeatValue = false;
        source.onComplete();
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testCancel() {
        final TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        subscriber.request(2);
        source.onNext(1, 2);
        assertThat(subscriber.items(), contains(1, 2));
        subscriber.cancel();
        source.onComplete();
        assertTrue(subscription.isCancelled());
    }
}
