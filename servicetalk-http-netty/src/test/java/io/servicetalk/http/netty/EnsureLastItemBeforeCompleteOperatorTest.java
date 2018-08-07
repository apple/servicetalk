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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.PublisherRule;

import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

public class EnsureLastItemBeforeCompleteOperatorTest {
    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();

    @Test
    public void passThroughIfPredicateReturnsTrue() {
        subscriber.subscribe(publisher.getPublisher().liftSynchronous(
                new EnsureLastItemBeforeCompleteOperator<>(s -> s.length() == 3, () -> "123"))).request(1);
        publisher.sendItems("124");
        subscriber.verifyItems("124");
        publisher.complete();
        subscriber.verifySuccess();
    }

    @Test
    public void passThroughIfPredicateReturnsTrueMultipleTimes() {
        subscriber.subscribe(publisher.getPublisher().liftSynchronous(
                new EnsureLastItemBeforeCompleteOperator<>(s -> s.length() == 3, () -> "123"))).request(2);
        publisher.sendItems("124");
        subscriber.verifyItems("124");
        publisher.sendItems("125");
        subscriber.verifyItems("125");
        publisher.complete();
        subscriber.verifySuccess();
    }

    @Test
    public void onErrorIsDeliveredIfPredicateNeverReturnsTrue() {
        subscriber.subscribe(publisher.getPublisher().liftSynchronous(
                new EnsureLastItemBeforeCompleteOperator<>(s -> s.length() == 3, () -> "123"))).request(1);
        publisher.sendItems("12");
        subscriber.verifyItems("12");
        publisher.fail(false, DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void onErrorIsDeliveredIfPredicateReturnsTrue() {
        subscriber.subscribe(publisher.getPublisher().liftSynchronous(
                new EnsureLastItemBeforeCompleteOperator<>(s -> s.length() == 3, () -> "123"))).request(1);
        publisher.sendItems("124");
        subscriber.verifyItems("124");
        publisher.fail(false, DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void delayedRequestInsertsItemFromSubscription() {
        subscriber.subscribe(publisher.getPublisher().liftSynchronous(
                new EnsureLastItemBeforeCompleteOperator<>(s -> s.length() == 3, () -> "123"))).request(1);
        publisher.sendItems("12");
        subscriber.verifyItems("12");
        publisher.complete();
        subscriber.verifyNoEmissions();
        subscriber.request(1);
        subscriber.verifyItems("123");
        subscriber.verifySuccess();
    }

    @Test
    public void cancelFromTerminalOnNextDoesNotViolate2_3() {
        subscriber.subscribe(publisher.getPublisher().liftSynchronous(
                new EnsureLastItemBeforeCompleteOperator<>(s -> s.length() == 3, () -> "123"))).request(2);
        AtomicBoolean onNextCalled = new AtomicBoolean();
        doAnswer(invocation -> {
            onNextCalled.set(true);
            Subscription s = subscriber.getSubscription();
            assert s != null;
            s.cancel();
            return null;
        }).when(subscriber.getSubscriber()).onNext(eq("123"));
        publisher.sendItems("12");
        subscriber.verifyItems("12");
        publisher.complete();
        subscriber.verifyItems("123");
        subscriber.verifySuccess();
        publisher.verifyNotCancelled();
        assertTrue(onNextCalled.get());
    }
}
