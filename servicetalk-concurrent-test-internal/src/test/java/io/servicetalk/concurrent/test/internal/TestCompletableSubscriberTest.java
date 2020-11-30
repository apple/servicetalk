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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;

import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class TestCompletableSubscriberTest {
    @Test
    public void onSubscribe() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        doOnSubscribe(subscriber);
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

    private static void onSubscribeOnTerminal(boolean onComplete) {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        doOnSubscribe(subscriber);
        assertFalse(subscriber.pollTerminal(200, MILLISECONDS));
        doTerminalSignal(subscriber, onComplete);
    }

    @Test
    public void singleItem() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        doOnSubscribe(subscriber);
        subscriber.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void singleItemCancelBefore() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        doOnSubscribe(subscriber).cancel();
        subscriber.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void singleItemCancelAfter() {
        TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
        Cancellable c = doOnSubscribe(subscriber);
        subscriber.onComplete();
        c.cancel();
        subscriber.awaitOnComplete();
    }

    private static Cancellable doOnSubscribe(TestCompletableSubscriber subscriber) {
        PublisherSource.Subscription subscription = mock(PublisherSource.Subscription.class);
        subscriber.onSubscribe(subscription);
        Cancellable realCancellable = subscriber.awaitSubscription();
        assertThat(realCancellable, notNullValue());
        return realCancellable;
    }

    private static void doTerminalSignal(TestCompletableSubscriber subscriber, boolean onComplete) {
        if (onComplete) {
            subscriber.onComplete();
            subscriber.awaitOnComplete();
        } else {
            subscriber.onError(DELIBERATE_EXCEPTION);
            assertSame(DELIBERATE_EXCEPTION, subscriber.awaitOnError());
        }
    }
}
