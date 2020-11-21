/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.test.internal;

import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class TestPublisherSubscriberTest {
    @Test
    public void onSubscribe() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        assertFalse(subscriber.pollTerminal(200, MILLISECONDS));
    }

    @Test
    public void onSubscribeOnComplete() {
        onSubscribeOnTerminal(true);
    }

    @Test
    public void onSubscribeOnError() {
        onSubscribeOnTerminal(false);
    }

    @Test
    public void onSubscribeOnNextOnComplete() {
        onSubscribeOnNextOnComplete(true);
    }

    @Test
    public void onSubscribeOnNextOnError() {
        onSubscribeOnNextOnComplete(false);
    }

    @Test
    public void multipleOnNextWithTake() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber).request(2);
        subscriber.onNext(null);
        subscriber.onNext(2);
        Integer next = subscriber.takeOnNext();
        assertNull(next);
        next = subscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(2, next.intValue());
        doTerminalSignal(subscriber, true);
    }

    @Test
    public void multipleOnNextWithPollAll() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber).request(2);
        subscriber.onNext(null);
        subscriber.onNext(2);

        assertThat(subscriber.pollAllOnNext(), contains(null, 2));

        doTerminalSignal(subscriber, true);
    }

    @Test
    public void onNextNotConsumedWhenOnCompleteAllowedIfOptIn() {
        onNextNotConsumedWhenOnCompleteThrows(true, false);
    }

    @Test
    public void onNextNotConsumedWhenOnErrorAllowedIfOptIn() {
        onNextNotConsumedWhenOnCompleteThrows(false, false);
    }

    @Test(expected = IllegalStateException.class)
    public void onNextNoOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        subscriber.onNext(2);
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteNoOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        subscriber.onComplete();
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorNoOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        subscriber.onError(DELIBERATE_EXCEPTION);
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorAwaitOnCompleteThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        subscriber.onError(DELIBERATE_EXCEPTION);
        subscriber.awaitOnComplete();
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteAwaitOnErrorThrows() throws Throwable {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        subscriber.onComplete();
        throw subscriber.awaitOnError();
    }

    @Test(expected = IllegalStateException.class)
    public void onSubscribeAfterOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        doOnSubscribe(subscriber);
    }

    @Test(expected = IllegalStateException.class)
    public void onSubscribeAfterOnCompleteThrows() {
        onSubscribeAfterTerminal(true);
    }

    @Test(expected = IllegalStateException.class)
    public void onSubscribeAfterOnErrorThrows() {
        onSubscribeAfterTerminal(false);
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteAfterOnCompleteThrows() {
        onCompleteAfterOnCompleteThrows(true, true);
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteAfterOnErrorThrows() {
        onCompleteAfterOnCompleteThrows(true, false);
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorAfterOnCompleteThrows() {
        onCompleteAfterOnCompleteThrows(false, true);
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorAfterOnErrorThrows() {
        onCompleteAfterOnCompleteThrows(false, false);
    }

    @Test(expected = IllegalStateException.class)
    public void onNextNotConsumedWhenOnCompleteThrows() {
        onNextNotConsumedWhenOnCompleteThrows(true, true);
    }

    @Test(expected = IllegalStateException.class)
    public void onNextNotConsumedWhenOnErrorThrows() {
        onNextNotConsumedWhenOnCompleteThrows(false, true);
    }

    private static void onSubscribeOnTerminal(boolean onComplete) {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);

        assertThat(subscriber.pollAllOnNext(), is(empty()));

        doTerminalSignal(subscriber, onComplete);
    }

    private static void onSubscribeOnNextOnComplete(boolean onComplete) {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber).request(1);

        subscriber.onNext(2);
        Integer next = subscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(2, next.intValue());

        doTerminalSignal(subscriber, onComplete);
    }

    private static Subscription doOnSubscribe(TestPublisherSubscriber<Integer> subscriber) {
        Subscription subscription = mock(Subscription.class);
        subscriber.onSubscribe(subscription);
        Subscription realSubscription = subscriber.awaitSubscription();
        assertThat(realSubscription, notNullValue());
        return realSubscription;
    }

    private static void doTerminalSignal(TestPublisherSubscriber<Integer> subscriber, boolean onComplete) {
        doTerminalSignal(subscriber, onComplete, true);
    }

    private static void doTerminalSignal(TestPublisherSubscriber<Integer> subscriber, boolean onComplete,
                                         boolean verifyOnNextConsumed) {
        if (onComplete) {
            subscriber.onComplete();
            subscriber.awaitOnComplete(verifyOnNextConsumed);
        } else {
            subscriber.onError(DELIBERATE_EXCEPTION);
            assertSame(DELIBERATE_EXCEPTION, subscriber.awaitOnError(verifyOnNextConsumed));
        }
    }

    private static void onNextNotConsumedWhenOnCompleteThrows(boolean onComplete, boolean verifyOnNextConsumed) {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber).request(1);
        subscriber.onNext(2);
        doTerminalSignal(subscriber, onComplete, verifyOnNextConsumed);
    }

    private static void onCompleteAfterOnCompleteThrows(boolean firstOnComplete, boolean secondOnComplete) {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber);
        doTerminalSignal(subscriber, firstOnComplete);
        doTerminalSignal(subscriber, secondOnComplete);
    }

    private static void onSubscribeAfterTerminal(boolean onComplete) {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        doTerminalSignal(subscriber, onComplete);
        doOnSubscribe(subscriber);
    }
}
