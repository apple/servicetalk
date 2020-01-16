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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class TestCollectingPublisherSubscriberTest {
    @Test
    public void onSubscribe() throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);

        assertThat(subscriber.pollAllOnNext(), is(empty()));
        assertFalse(subscriber.pollTerminal(200, MILLISECONDS));
    }

    @Test
    public void onSubscribeOnComplete() throws InterruptedException {
        onSubscribeOnTerminal(true);
    }

    @Test
    public void onSubscribeOnError() throws InterruptedException {
        onSubscribeOnTerminal(false);
    }

    @Test
    public void onSubscribeOnNextOnComplete() throws InterruptedException {
        onSubscribeOnNextOnComplete(true);
    }

    @Test
    public void onSubscribeOnNextOnError() throws InterruptedException {
        onSubscribeOnNextOnComplete(false);
    }

    @Test
    public void multipleOnNextWithTake() throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        doOnSubscribe(subscriber);
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
    public void multipleOnNextWithPollAll() throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        doOnSubscribe(subscriber);
        subscriber.onNext(null);
        subscriber.onNext(2);

        assertThat(subscriber.pollAllOnNext(), contains(null, 2));

        doTerminalSignal(subscriber, true);
    }

    @Test
    public void onNextNotConsumedWhenOnCompleteAllowedIfOptIn() throws InterruptedException {
        onNextNotConsumedWhenOnCompleteThrows(true, false);
    }

    @Test
    public void onNextNotConsumedWhenOnErrorAllowedIfOptIn() throws InterruptedException {
        onNextNotConsumedWhenOnCompleteThrows(false, false);
    }

    @Test(expected = IllegalStateException.class)
    public void onNextNoOnSubscribeThrows() {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        subscriber.onNext(2);
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteNoOnSubscribeThrows() {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        subscriber.onComplete();
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorNoOnSubscribeThrows() {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        subscriber.onError(DELIBERATE_EXCEPTION);
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorAwaitOnCompleteThrows() throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        subscriber.onError(DELIBERATE_EXCEPTION);
        subscriber.awaitOnComplete();
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteAwaitOnErrorThrows() throws Throwable {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        subscriber.onComplete();
        throw subscriber.awaitOnError();
    }

    @Test(expected = IllegalStateException.class)
    public void onSubscribeAfterOnSubscribeThrows() throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        doOnSubscribe(subscriber);
    }

    @Test(expected = IllegalStateException.class)
    public void onSubscribeAfterOnCompleteThrows() throws InterruptedException {
        onSubscribeAfterTerminal(true);
    }

    @Test(expected = IllegalStateException.class)
    public void onSubscribeAfterOnErrorThrows() throws InterruptedException {
        onSubscribeAfterTerminal(false);
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteAfterOnCompleteThrows() throws InterruptedException {
        onCompleteAfterOnCompleteThrows(true, true);
    }

    @Test(expected = IllegalStateException.class)
    public void onCompleteAfterOnErrorThrows() throws InterruptedException {
        onCompleteAfterOnCompleteThrows(true, false);
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorAfterOnCompleteThrows() throws InterruptedException {
        onCompleteAfterOnCompleteThrows(false, true);
    }

    @Test(expected = IllegalStateException.class)
    public void onErrorAfterOnErrorThrows() throws InterruptedException {
        onCompleteAfterOnCompleteThrows(false, false);
    }

    @Test(expected = IllegalStateException.class)
    public void onNextNotConsumedWhenOnCompleteThrows() throws InterruptedException {
        onNextNotConsumedWhenOnCompleteThrows(true, true);
    }

    @Test(expected = IllegalStateException.class)
    public void onNextNotConsumedWhenOnErrorThrows() throws InterruptedException {
        onNextNotConsumedWhenOnCompleteThrows(false, true);
    }

    private static void onSubscribeOnTerminal(boolean onComplete) throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);

        assertThat(subscriber.pollAllOnNext(), is(empty()));

        doTerminalSignal(subscriber, onComplete);
    }

    private static void onSubscribeOnNextOnComplete(boolean onComplete) throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);

        subscriber.onNext(2);
        Integer next = subscriber.takeOnNext();
        assertNotNull(next);
        assertEquals(2, next.intValue());

        doTerminalSignal(subscriber, onComplete);
    }

    private static void doOnSubscribe(TestCollectingPublisherSubscriber<Integer> subscriber)
            throws InterruptedException {
        Subscription subscription = mock(Subscription.class);
        subscriber.onSubscribe(subscription);
        assertSame(subscription, subscriber.awaitSubscription());
    }

    private static void doTerminalSignal(TestCollectingPublisherSubscriber<Integer> subscriber, boolean onComplete)
            throws InterruptedException {
        doTerminalSignal(subscriber, onComplete, true);
    }

    private static void doTerminalSignal(TestCollectingPublisherSubscriber<Integer> subscriber, boolean onComplete,
                                         boolean verifyOnNextConsumed) throws InterruptedException {
        if (onComplete) {
            subscriber.onComplete();
            subscriber.awaitOnComplete(verifyOnNextConsumed);
        } else {
            subscriber.onError(DELIBERATE_EXCEPTION);
            assertSame(DELIBERATE_EXCEPTION, subscriber.awaitOnError(verifyOnNextConsumed));
        }
    }

    private static void onNextNotConsumedWhenOnCompleteThrows(boolean onComplete, boolean verifyOnNextConsumed)
            throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        doOnSubscribe(subscriber);
        subscriber.onNext(2);
        doTerminalSignal(subscriber, onComplete, verifyOnNextConsumed);
    }

    private static void onCompleteAfterOnCompleteThrows(boolean firstOnComplete, boolean secondOnComplete)
            throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        doOnSubscribe(subscriber);
        doTerminalSignal(subscriber, firstOnComplete);
        doTerminalSignal(subscriber, secondOnComplete);
    }

    private static void onSubscribeAfterTerminal(boolean onComplete) throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        doTerminalSignal(subscriber, onComplete);
        doOnSubscribe(subscriber);
    }
}
