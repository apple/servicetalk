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
package io.servicetalk.concurrent.api;

import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class CompletableMergeWithPublisherTest {

    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();
    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();

    @Test
    public void testDelayedPublisherSubscriptionForReqNBuffering() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher(true)));
        subscriber.verifySubscribe();
        subscriber.request(5);
        publisher.sendOnSubscribe();
        completable.onComplete();
        subscriber.request(7);
        publisher.sendItems("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        publisher.complete();
        subscriber.verifySuccessNoRequestN("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    }

    @Test
    public void testDelayedPublisherSubscriptionForCancelBuffering() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher(true)));
        subscriber.verifySubscribe();
        subscriber.request(5);
        publisher.sendOnSubscribe();
        completable.onComplete();
        subscriber.cancel();
        publisher.verifyCancelled();
    }

    @Test
    public void testDelayedCompletableSubscriptionForCancelBuffering() {
        TestCompletable completable = new TestCompletable(false, true);
        subscriber.subscribe(completable.merge(publisher.getPublisher(true)));
        subscriber.verifySubscribe();
        subscriber.request(5);
        completable.sendOnSubscribe();
        publisher.sendOnSubscribe();
        completable.onComplete();
        subscriber.cancel();
        publisher.verifyCancelled();
        completable.verifyCancelled();
    }

    @Test
    public void testCompletableFailCancelsPublisher() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        completable.onError(DELIBERATE_EXCEPTION);
        publisher.verifyCancelled();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testPublisherFailCancelsCompletable() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        publisher.fail(false, DELIBERATE_EXCEPTION);
        completable.verifyCancelled();
        publisher.verifyNotCancelled();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelCancelsPendingSourceSubscription() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        subscriber.cancel();
        publisher.verifyCancelled();
        completable.verifyCancelled();
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        completable.onComplete();
        subscriber.request(2);
        publisher.sendItems("one", "two");
        subscriber.cancel();
        subscriber.verifyItems("one", "two");
        publisher.verifyCancelled();
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        subscriber.request(2);
        publisher.sendItems("one", "two");
        publisher.complete();
        subscriber.cancel();
        subscriber.verifyItems("one", "two");
        subscriber.verifyNoEmissions();
        completable.verifyCancelled();
    }

    @Test
    public void testCompletableAndPublisherCompleteSingleCompleteSignal() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        subscriber.request(2);
        completable.onComplete();
        publisher.sendItems("one", "two");
        publisher.complete();
        subscriber.verifyItems("one", "two");
        subscriber.verifySuccess("one", "two");
    }

    @Test
    public void testCompletableAndPublisherFailOnlySingleErrorSignal() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        subscriber.request(3);
        publisher.sendItems("one", "two");
        completable.onError(DELIBERATE_EXCEPTION);
        publisher.fail(true, DELIBERATE_EXCEPTION);
        subscriber.verifyItems("one", "two");
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCompletableFailsAndPublisherCompletesSingleErrorSignal() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        subscriber.request(2);
        publisher.sendItems("one", "two");
        publisher.complete();
        completable.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyItems("one", "two");
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testPublisherFailsAndCompletableCompletesSingleErrorSignal() {
        TestCompletable completable = new TestCompletable();
        subscriber.subscribe(completable.merge(publisher.getPublisher()));
        subscriber.verifySubscribe();
        subscriber.request(2);
        publisher.sendItems("one", "two");
        completable.onComplete();
        publisher.fail(false, DELIBERATE_EXCEPTION);
        subscriber.verifyItems("one", "two");
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }
}
