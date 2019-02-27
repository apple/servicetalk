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

import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class CompletableMergeWithPublisherTest {

    private final TestSubscription subscription = new TestSubscription();
    private final TestPublisher<String> publisher = new TestPublisher.Builder<String>()
            .disableAutoOnSubscribe().build();
    private final TestPublisherSubscriber<String> subscriber = newTestPublisherSubscriber();

    @Test
    public void testDelayedPublisherSubscriptionForReqNBuffering() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(5);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.request(7);
        publisher.onNext("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        publisher.onComplete();
        assertThat(subscriber.items(), contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testDelayedPublisherSubscriptionForCancelBuffering() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(5);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testDelayedCompletableSubscriptionForCancelBuffering() {
        TestCompletable completable = new TestCompletable(false, true);
        toSource(completable.merge(publisher)).subscribe(subscriber);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(5);
        completable.sendOnSubscribe();
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.cancel();
        assertTrue(subscription.isCancelled());
        completable.verifyCancelled();
    }

    @Test
    public void testCompletableFailCancelsPublisher() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        completable.onError(DELIBERATE_EXCEPTION);
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testPublisherFailCancelsCompletable() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        assertFalse(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        completable.verifyCancelled();
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelCancelsPendingSourceSubscription() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.cancel();
        assertTrue(subscription.isCancelled());
        completable.verifyCancelled();
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
    }

    @Test
    public void testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        completable.onComplete();
        subscriber.request(2);
        publisher.onNext("one", "two");
        subscriber.cancel();
        assertThat(subscriber.takeItems(), contains("one", "two"));
        assertTrue(subscription.isCancelled());
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
    }

    @Test
    public void testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(2);
        publisher.onNext("one", "two");
        publisher.onComplete();
        subscriber.cancel();
        assertThat(subscriber.takeItems(), contains("one", "two"));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
        completable.verifyCancelled();
    }

    @Test
    public void testCompletableAndPublisherCompleteSingleCompleteSignal() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(2);
        completable.onComplete();
        publisher.onNext("one", "two");
        publisher.onComplete();
        assertThat(subscriber.items(), contains("one", "two"));
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testCompletableAndPublisherFailOnlySingleErrorSignal() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(3);
        publisher.onNext("one", "two");
        completable.onError(DELIBERATE_EXCEPTION);
        assertTrue(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains("one", "two"));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCompletableFailsAndPublisherCompletesSingleErrorSignal() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(2);
        publisher.onNext("one", "two");
        publisher.onComplete();
        completable.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains("one", "two"));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testPublisherFailsAndCompletableCompletesSingleErrorSignal() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertTrue(subscriber.subscriptionReceived());
        subscriber.request(2);
        publisher.onNext("one", "two");
        completable.onComplete();
        assertFalse(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains("one", "two"));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }
}
