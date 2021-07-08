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
package io.servicetalk.concurrent.test.internal;

import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TestPublisherSubscriberTest {
    @Test
    void onSubscribe() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        assertThat(subscriber.pollTerminal(200, MILLISECONDS), is(nullValue()));
    }

    @Test
    void onSubscribeOnComplete() {
        onSubscribeOnTerminal(true);
    }

    @Test
    void onSubscribeOnError() {
        onSubscribeOnTerminal(false);
    }

    @Test
    void onSubscribeOnNextOnComplete() {
        onSubscribeOnNextOnComplete(true);
    }

    @Test
    void onSubscribeOnNextOnError() {
        onSubscribeOnNextOnComplete(false);
    }

    @Test
    void multipleOnNextWithTake() {
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
    void multipleOnNextWithPollAll() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        doOnSubscribe(subscriber).request(2);
        subscriber.onNext(null);
        subscriber.onNext(2);

        assertThat(subscriber.pollAllOnNext(), contains(null, 2));

        doTerminalSignal(subscriber, true);
    }

    @Test
    void onNextNotConsumedWhenOnCompleteAllowedIfOptIn() {
        onNextNotConsumedWhenOnCompleteThrows(true, false);
    }

    @Test
    void onNextNotConsumedWhenOnErrorAllowedIfOptIn() {
        onNextNotConsumedWhenOnCompleteThrows(false, false);
    }

    @Test
    void onNextNoOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        assertThrows(IllegalStateException.class, () -> subscriber.onNext(2));
    }

    @Test
    void onCompleteNoOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        assertThrows(IllegalStateException.class, subscriber::onComplete);
    }

    @Test
    void onErrorNoOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        assertThrows(IllegalStateException.class, () -> subscriber.onError(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorAwaitOnCompleteThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        subscriber.onError(DELIBERATE_EXCEPTION);
        assertThrows(IllegalStateException.class, subscriber::awaitOnComplete);
    }

    @Test
    void onCompleteAwaitOnErrorThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        subscriber.onComplete();
        assertThrows(IllegalStateException.class, () -> {
            throw subscriber.awaitOnError();
        });
    }

    @Test
    void onSubscribeAfterOnSubscribeThrows() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

        doOnSubscribe(subscriber);
        assertThrows(IllegalStateException.class, () -> doOnSubscribe(subscriber));
    }

    @Test
    void onSubscribeAfterOnCompleteThrows() {
        assertThrows(IllegalStateException.class, () -> onSubscribeAfterTerminal(true));
    }

    @Test
    void onSubscribeAfterOnErrorThrows() {
        assertThrows(IllegalStateException.class, () -> onSubscribeAfterTerminal(false));
    }

    @Test
    void onCompleteAfterOnCompleteThrows() {
        assertThrows(IllegalStateException.class, () -> onCompleteAfterOnCompleteThrows(true, true));
    }

    @Test
    void onCompleteAfterOnErrorThrows() {
        assertThrows(IllegalStateException.class, () -> onCompleteAfterOnCompleteThrows(true, false));
    }

    @Test
    void onErrorAfterOnCompleteThrows() {
        assertThrows(IllegalStateException.class, () -> onCompleteAfterOnCompleteThrows(false, true));
    }

    @Test
    void onErrorAfterOnErrorThrows() {
        assertThrows(IllegalStateException.class, () -> onCompleteAfterOnCompleteThrows(false, false));
    }

    @Test
    void onNextNotConsumedWhenOnCompleteThrows() {
        assertThrows(IllegalStateException.class,
                     () -> onNextNotConsumedWhenOnCompleteThrows(true, true));
    }

    @Test
    void onNextNotConsumedWhenOnErrorThrows() {
        assertThrows(IllegalStateException.class,
                     () -> onNextNotConsumedWhenOnCompleteThrows(false, true));
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
