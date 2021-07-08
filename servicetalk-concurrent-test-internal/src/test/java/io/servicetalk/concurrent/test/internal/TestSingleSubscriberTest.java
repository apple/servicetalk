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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class TestSingleSubscriberTest {
    @Test
    void onSubscribe() {
        TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
        doOnSubscribe(subscriber);
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

    private static void onSubscribeOnTerminal(boolean onComplete) {
        TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
        doOnSubscribe(subscriber);
        assertThat(subscriber.pollTerminal(200, MILLISECONDS), is(nullValue()));
        doTerminalSignal(subscriber, onComplete);
    }

    @Test
    void singleItem() {
        singleItem(ThreadLocalRandom.current().nextInt());
    }

    @Test
    void singleItemNull() {
        singleItem(null);
    }

    @Test
    void singleItemCancelBefore() {
        TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
        doOnSubscribe(subscriber).cancel();
        subscriber.onSuccess(10);
        assertThat(subscriber.awaitOnSuccess(), is(10));
    }

    @Test
    void singleItemCancelAfter() {
        TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
        Cancellable c = doOnSubscribe(subscriber);
        subscriber.onSuccess(10);
        c.cancel();
        assertThat(subscriber.awaitOnSuccess(), is(10));
    }

    private static void singleItem(@Nullable Integer i) {
        TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
        doOnSubscribe(subscriber);
        subscriber.onSuccess(i);
        assertThat(subscriber.awaitOnSuccess(), is(i));
    }

    private static Cancellable doOnSubscribe(TestSingleSubscriber<Integer> subscriber) {
        PublisherSource.Subscription subscription = mock(PublisherSource.Subscription.class);
        subscriber.onSubscribe(subscription);
        Cancellable realCancellable = subscriber.awaitSubscription();
        assertThat(realCancellable, notNullValue());
        return realCancellable;
    }

    private static void doTerminalSignal(TestSingleSubscriber<Integer> subscriber, boolean onComplete) {
        if (onComplete) {
            Integer value = ThreadLocalRandom.current().nextInt();
            subscriber.onSuccess(value);
            assertThat(subscriber.awaitOnSuccess(), is(value));
        } else {
            subscriber.onError(DELIBERATE_EXCEPTION);
            assertSame(DELIBERATE_EXCEPTION, subscriber.awaitOnError());
        }
    }
}
