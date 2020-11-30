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

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.ExecutorRule.newRule;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompletableMergeWithPublisherTest {
    @Rule
    public final ExecutorRule<Executor> executorRule = newRule();
    private final TestSubscription subscription = new TestSubscription();
    private final TestPublisher<String> publisher = new TestPublisher.Builder<String>()
            .disableAutoOnSubscribe().build();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void testDelayedPublisherSubscriptionForReqNBuffering() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        completable.onComplete();
        subscriber.awaitSubscription().request(7);
        publisher.onNext("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(12), contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
        subscriber.awaitOnComplete();
    }

    @Test
    public void testDelayedPublisherSubscriptionForCancelBuffering() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testDelayedCompletableSubscriptionForCancelBuffering() {
        LegacyTestCompletable completable = new LegacyTestCompletable(false, true);
        toSource(completable.merge(publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        completable.sendOnSubscribe();
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        completable.verifyCancelled();
    }

    @Test
    public void testCompletableFailCancelsPublisher() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onError(DELIBERATE_EXCEPTION);
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testPublisherFailCancelsCompletable() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertFalse(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        completable.verifyCancelled();
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelCancelsPendingSourceSubscription() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        completable.verifyCancelled();
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
    }

    @Test
    public void testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        subscriber.awaitSubscription().cancel();
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
    }

    @Test
    public void testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        publisher.onComplete();
        subscriber.awaitSubscription().cancel();
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        completable.verifyCancelled();
    }

    @Test
    public void testCompletableAndPublisherCompleteSingleCompleteSignal() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        completable.onComplete();
        publisher.onNext("one", "two");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        subscriber.awaitOnComplete();
    }

    @Test
    public void testCompletableAndPublisherFailOnlySingleErrorSignal() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("one", "two");
        completable.onError(DELIBERATE_EXCEPTION);
        assertTrue(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCompletableFailsAndPublisherCompletesSingleErrorSignal() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        publisher.onComplete();
        completable.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testPublisherFailsAndCompletableCompletesSingleErrorSignal() {
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        completable.onComplete();
        assertFalse(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void offloadingWaitsForPublisherSignalsEvenIfCompletableTerminates() throws Exception {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable testCancellable = new TestCancellable();
        CountDownLatch latch = new CountDownLatch(1);
        toSource(completable.publishOn(executorRule.executor())
                .merge(publisher.publishOn(executorRule.executor())).afterOnNext(item -> {
            // The goal of this test is to have the Completable terminate, but have onNext signals from the Publisher be
            // delayed on the Executor. Even in this case the merge operator should correctly sequence the onComplete to
            // the downstream subscriber until after all the onNext events have completed.
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).whenOnComplete(latch::countDown)).subscribe(subscriber);

        publisher.onSubscribe(subscription);
        final String[] values = new String[20];
        for (int i = 0; i < values.length; ++i) {
            values[i] = i + " " + ThreadLocalRandom.current().nextLong();
        }
        subscriber.awaitSubscription().request(values.length);
        subscription.awaitRequestN(values.length);
        publisher.onNext(values);
        publisher.onComplete();

        completable.onSubscribe(testCancellable);
        completable.onComplete();

        latch.await();
        assertThat(subscriber.takeOnNext(values.length), contains(values));
        subscriber.awaitOnComplete();
    }
}
