/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CompletableMergeWithPublisherTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = withCachedExecutor();
    private final TestSubscription subscription = new TestSubscription();
    private final TestPublisher<String> publisher = new TestPublisher.Builder<String>()
            .disableAutoOnSubscribe().build();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    void testDelayedPublisherSubscriptionForReqNBuffering() {
        testDelayedPublisherSubscriptionForReqNBuffering(false);
    }

    @Test
    void delayErrorDelayedPublisherSubscriptionForReqNBuffering() {
        testDelayedPublisherSubscriptionForReqNBuffering(true);
    }

    private Publisher<String> applyMerge(Completable completable, boolean delayError) {
        return applyMerge(completable, delayError, publisher);
    }

    private static Publisher<String> applyMerge(Completable completable, boolean delayError,
                                                Publisher<String> publisher) {
        return delayError ? completable.mergeDelayError(publisher) : completable.merge(publisher);
    }

    private void testDelayedPublisherSubscriptionForReqNBuffering(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        completable.onComplete();
        subscriber.awaitSubscription().request(7);
        publisher.onSubscribe(subscription);
        publisher.onNext("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(12), contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
        subscriber.awaitOnComplete();
    }

    @Test
    void testDelayedPublisherSubscriptionForCancelBuffering() {
        testDelayedPublisherSubscriptionForCancelBuffering(false);
    }

    @Test
    void delayErrorDelayedPublisherSubscriptionForCancelBuffering() {
        testDelayedPublisherSubscriptionForCancelBuffering(true);
    }

    private void testDelayedPublisherSubscriptionForCancelBuffering(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void testDelayedCompletableSubscriptionForCancelBuffering() {
        testDelayedCompletableSubscriptionForCancelBuffering(false);
    }

    @Test
    void delayErrorDelayedCompletableSubscriptionForCancelBuffering() {
        testDelayedCompletableSubscriptionForCancelBuffering(true);
    }

    private void testDelayedCompletableSubscriptionForCancelBuffering(boolean delayError) {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable cancellable = new TestCancellable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        subscriber.awaitSubscription().request(5);
        completable.onSubscribe(cancellable);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        assertTrue(cancellable.isCancelled());
    }

    @Test
    void testCompletableFailCancelsPublisher() {
        TestCompletable completable = new TestCompletable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onError(DELIBERATE_EXCEPTION);
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void delayErrorCompletableFailDoesNotCancelPublisherFail() {
        delayErrorCompletableFailDoesNotCancelPublisher(true);
    }

    @Test
    void delayErrorCompletableFailDoesNotCancelPublisher() {
        delayErrorCompletableFailDoesNotCancelPublisher(false);
    }

    private void delayErrorCompletableFailDoesNotCancelPublisher(boolean secondIsError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, true)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onError(DELIBERATE_EXCEPTION);
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        if (secondIsError) {
            publisher.onError(newSecondException());
        } else {
            publisher.onComplete();
        }
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testPublisherFailCancelsCompletable() {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable cancellable = new TestCancellable();
        toSource(completable.merge(publisher)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onSubscribe(cancellable);
        assertFalse(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        assertTrue(cancellable.isCancelled());
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void delayErrorPublisherFailDoesNotCancelCompletableFail() {
        delayErrorPublisherFailDoesNotCancelCompletable(true);
    }

    @Test
    void delayErrorPublisherFailDoesNotCancelCompletable() {
        delayErrorPublisherFailDoesNotCancelCompletable(false);
    }

    private void delayErrorPublisherFailDoesNotCancelCompletable(boolean secondIsError) {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable cancellable = new TestCancellable();
        toSource(applyMerge(completable, true)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onSubscribe(cancellable);
        assertFalse(subscription.isCancelled());
        publisher.onError(DELIBERATE_EXCEPTION);
        assertFalse(cancellable.isCancelled());
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        if (secondIsError) {
            completable.onError(newSecondException());
        } else {
            completable.onComplete();
        }
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCancelCancelsPendingSourceSubscription() {
        testCancelCancelsPendingSourceSubscription(false);
    }

    @Test
    void delayErrorCancelCancelsPendingSourceSubscription() {
        testCancelCancelsPendingSourceSubscription(true);
    }

    private void testCancelCancelsPendingSourceSubscription(boolean delayError) {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable cancellable = new TestCancellable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onSubscribe(cancellable);
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        assertTrue(cancellable.isCancelled());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction() {
        testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction(false);
    }

    @Test
    void delayErrorCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction() {
        testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction(true);
    }

    private void testCancelCompletableCompletePublisherPendingCancelsNoMoreInteraction(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        completable.onComplete();
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        subscriber.awaitSubscription().cancel();
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction() {
        testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction(false);
    }

    @Test
    void delayErrorCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction() {
        testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction(true);
    }

    private void testCancelPublisherCompleteCompletablePendingCancelsNoMoreInteraction(boolean delayError) {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable cancellable = new TestCancellable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        completable.onSubscribe(cancellable);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        publisher.onComplete();
        subscriber.awaitSubscription().cancel();
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertTrue(cancellable.isCancelled());
    }

    @Test
    void testCompletableAndPublisherCompleteSingleCompleteSignal() {
        testCompletableAndPublisherCompleteSingleCompleteSignal(false);
    }

    @Test
    void delayErrorCompletableAndPublisherCompleteSingleCompleteSignal() {
        testCompletableAndPublisherCompleteSingleCompleteSignal(true);
    }

    private void testCompletableAndPublisherCompleteSingleCompleteSignal(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        completable.onComplete();
        publisher.onNext("one", "two");
        publisher.onComplete();
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        subscriber.awaitOnComplete();
    }

    @Test
    void testCompletableAndPublisherFailOnlySingleErrorSignal() {
        testCompletableAndPublisherFailOnlySingleErrorSignal(false);
    }

    @Test
    void delayErrorCompletableAndPublisherFailOnlySingleErrorSignal() {
        testCompletableAndPublisherFailOnlySingleErrorSignal(true);
    }

    private void testCompletableAndPublisherFailOnlySingleErrorSignal(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("one", "two");
        completable.onError(DELIBERATE_EXCEPTION);
        if (!delayError) {
            assertTrue(subscription.isCancelled());
        }
        publisher.onError(newSecondException());
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCompletableFailsAndPublisherCompletesSingleErrorSignal() {
        testCompletableFailsAndPublisherCompletesSingleErrorSignal(false);
    }

    @Test
    void delayErrorCompletableFailsAndPublisherCompletesSingleErrorSignal() {
        testCompletableFailsAndPublisherCompletesSingleErrorSignal(true);
    }

    private void testCompletableFailsAndPublisherCompletesSingleErrorSignal(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        publisher.onNext("one", "two");
        publisher.onComplete();
        completable.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains("one", "two"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testPublisherFailsAndCompletableCompletesSingleErrorSignal() {
        testPublisherFailsAndCompletableCompletesSingleErrorSignal(false);
    }

    @Test
    void delayErrorPublisherFailsAndCompletableCompletesSingleErrorSignal() {
        testPublisherFailsAndCompletableCompletesSingleErrorSignal(true);
    }

    private void testPublisherFailsAndCompletableCompletesSingleErrorSignal(boolean delayError) {
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
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
    void offloadingWaitsForPublisherSignalsEvenIfCompletableTerminates() throws Exception {
        offloadingWaitsForPublisherSignalsEvenIfCompletableTerminates(false);
    }

    @Test
    void delayErrorOffloadingWaitsForPublisherSignalsEvenIfCompletableTerminates() throws Exception {
        offloadingWaitsForPublisherSignalsEvenIfCompletableTerminates(true);
    }

    private void offloadingWaitsForPublisherSignalsEvenIfCompletableTerminates(boolean delayError) throws Exception {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestCancellable testCancellable = new TestCancellable();
        CountDownLatch latch = new CountDownLatch(1);
        toSource(applyMerge(completable.publishOn(executorExtension.executor()), delayError,
                            publisher.publishOn(executorExtension.executor())).afterOnNext(item -> {
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

    @Test
    void publisherAndCompletableErrorDoesNotDuplicateErrorDownstream() throws Exception {
        publisherAndCompletableErrorDoesNotDuplicateErrorDownstream(false);
    }

    @Test
    void delayErrorPublisherAndCompletableErrorDoesNotDuplicateErrorDownstream() throws Exception {
        publisherAndCompletableErrorDoesNotDuplicateErrorDownstream(true);
    }

    private void publisherAndCompletableErrorDoesNotDuplicateErrorDownstream(boolean delayError) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        TestCompletable completable = new TestCompletable();
        toSource(applyMerge(completable, delayError)).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        Future<Void> f = executorExtension.executor().submit(() -> {
            try {
                barrier.await();
            } catch (Exception e) {
                throwException(e);
            }
            completable.onError(DELIBERATE_EXCEPTION);
        }).toFuture();

        barrier.await();
        publisher.onError(DELIBERATE_EXCEPTION);

        f.get();
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onNextConcurrentWithCompletableError() throws Exception {
        onNextConcurrentWithCompletableError(false);
    }

    @Test
    void delayErrorOnNextConcurrentWithCompletableError() throws Exception {
        onNextConcurrentWithCompletableError(true);
    }

    private void onNextConcurrentWithCompletableError(boolean delayError) throws Exception {
        CountDownLatch nextLatch1 = new CountDownLatch(1);
        CountDownLatch nextLatch2 = new CountDownLatch(1);
        TestCompletable completable = new TestCompletable();
        @SuppressWarnings("unchecked")
        PublisherSource.Subscriber<String> mockSubscriber = mock(PublisherSource.Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(Long.MAX_VALUE);
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            nextLatch2.countDown();
            nextLatch1.await();
            return null;
        }).when(mockSubscriber).onNext(any());
        toSource(applyMerge(completable, delayError)).subscribe(mockSubscriber);
        publisher.onSubscribe(subscription);
        Future<Void> f = executorExtension.executor().submit(() -> {
            try {
                nextLatch2.await();
            } catch (Exception e) {
                throwException(e);
            }
            completable.onError(DELIBERATE_EXCEPTION);
            nextLatch1.countDown();
        }).toFuture();

        subscription.awaitRequestN(1);
        publisher.onNext("one");
        f.get();
        publisher.onError(newSecondException());
        verify(mockSubscriber).onNext(eq("one"));
        verify(mockSubscriber).onError(eq(DELIBERATE_EXCEPTION));
        verify(mockSubscriber, never()).onComplete();
    }

    private static IllegalStateException newSecondException() {
        return new IllegalStateException("second exception should be ignored");
    }

    @Test
    void onNextReentryCompleteConcurrentWithCompletable() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(false, true, true);
    }

    @Test
    void onNextReentryErrorConcurrentWithCompletable() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(false, false, true);
    }

    @Test
    void onNextReentryCompleteConcurrentWithCompletableError() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(false, true, false);
    }

    @Test
    void onNextReentryErrorConcurrentWithCompletableError() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(false, false, false);
    }

    @Test
    void delayErrorOnNextReentryCompleteConcurrentWithCompletable() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(true, true, true);
    }

    @Test
    void delayErrorOnNextReentryErrorConcurrentWithCompletable() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(true, false, true);
    }

    @Test
    void delayErrorOnNextReentryCompleteConcurrentWithCompletableError() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(true, true, false);
    }

    @Test
    void delayErrorOnNextReentryErrorConcurrentWithCompletableError() throws Exception {
        onNextReentryCompleteConcurrentWithCompletable(true, false, false);
    }

    private void onNextReentryCompleteConcurrentWithCompletable(boolean delayError, boolean pubOnComplete,
                                                                boolean compOnComplete) throws Exception {
        CountDownLatch nextLatch1 = new CountDownLatch(1);
        CountDownLatch nextLatch2 = new CountDownLatch(1);
        TestCompletable completable = new TestCompletable();
        AtomicInteger onNextCount = new AtomicInteger();
        @SuppressWarnings("unchecked")
        PublisherSource.Subscriber<String> mockSubscriber = mock(PublisherSource.Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(Long.MAX_VALUE);
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            nextLatch2.countDown();
            final int count = onNextCount.incrementAndGet();
            if (count == 1) {
                publisher.onNext("two");
            } else if (count == 2) {
                if (pubOnComplete) {
                    publisher.onComplete();
                } else {
                    publisher.onError(DELIBERATE_EXCEPTION);
                }
            }
            nextLatch1.await();
            return null;
        }).when(mockSubscriber).onNext(any());
        toSource(applyMerge(completable, delayError)).subscribe(mockSubscriber);
        publisher.onSubscribe(subscription);
        Future<Void> f = executorExtension.executor().submit(() -> {
            try {
                nextLatch2.await();
            } catch (Exception e) {
                throwException(e);
            }
            if (compOnComplete) {
                completable.onComplete();
            } else {
                completable.onError(DELIBERATE_EXCEPTION);
            }
            nextLatch1.countDown();
        }).toFuture();

        subscription.awaitRequestN(2);
        publisher.onNext("one");

        f.get();
        verify(mockSubscriber).onNext(eq("one"));
        verify(mockSubscriber, compOnComplete ? times(1) : atMost(1)).onNext(eq("two"));
        if (pubOnComplete && compOnComplete) {
            verify(mockSubscriber).onComplete();
            verify(mockSubscriber, never()).onError(any());
        } else {
            verify(mockSubscriber, never()).onComplete();
            verify(mockSubscriber).onError(eq(DELIBERATE_EXCEPTION));
        }
    }
}
