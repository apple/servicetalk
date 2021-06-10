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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.PublisherSource.Subscription;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublisherFlatMapMergeTest {
    private static final long TERMINAL_POLL_MS = 10;
    @Nullable
    private static ExecutorService executorService;
    @Nullable
    private static Executor executor;

    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> publisher = new TestPublisher<>();

    @BeforeAll
    static void beforeClass() {
        executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
        executor = io.servicetalk.concurrent.api.Executors.from(executorService);
    }

    @AfterAll
    static void afterClass() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Test
    void singleToPublisherOnNextErrorPropagated() {
        toSource(publisher.flatMapMerge(x -> succeeded(x).toPublisher(), 2)
                .<Integer>map(y -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        publisher.onNext(1, 2);
        publisher.onComplete();
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void mappedRecoverMakesProgress() throws Exception {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = mock(Subscriber.class);
        CountDownLatch latchOnSubscribe = new CountDownLatch(1);
        CountDownLatch latchOnError = new CountDownLatch(1);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        BlockingQueue<Integer> results = new ArrayBlockingQueue<>(10);
        doAnswer(a -> {
            Subscription s = a.getArgument(0);
            s.request(4);
            latchOnSubscribe.countDown();
            return null;
        }).when(mockSubscriber).onSubscribe(any(Subscription.class));
        doAnswer(a -> {
            causeRef.set(a.getArgument(0));
            latchOnError.countDown();
            return null;
        }).when(mockSubscriber).onError(any());
        doAnswer(a -> {
            results.add(a.getArgument(0));
            throw DELIBERATE_EXCEPTION;
        }).when(mockSubscriber).onNext(any());

        Processor<Integer, Integer> processor = newPublisherProcessor();
        toSource(fromSource(processor).flatMapMergeDelayError(i -> from(i + 10).onErrorResume(cause ->
                from(i + 20).concat(failed(cause))))).subscribe(mockSubscriber);

        latchOnSubscribe.await();
        processor.onNext(1);
        assertThat(results.take(), is(11));
        assertThat(results.take(), is(21));
        assertThat(causeRef.get(), is(nullValue()));
        processor.onComplete();
        latchOnError.await();
        final Throwable t = causeRef.get();
        assertThat(t, is(DELIBERATE_EXCEPTION));
    }

    @Test
    void singleConcurrencyComplete() {
        singleConcurrencyComplete(publisher.flatMapMerge(i -> from(i + 1), 1));
    }

    @Test
    void defaultConcurrencyComplete() {
        singleConcurrencyComplete(publisher.flatMapMerge(i -> from(i + 1)));
    }

    @Test
    void singleConcurrencyDelayErrorComplete() {
        singleConcurrencyComplete(publisher.flatMapMergeDelayError(i -> from(i + 1), 1));
    }

    @Test
    void defaultConcurrencyDelayErrorComplete() {
        singleConcurrencyComplete(publisher.flatMapMergeDelayError(i -> from(i + 1)));
    }

    private void singleConcurrencyComplete(Publisher<Integer> flatMappedPublisher) {
        toSource(flatMappedPublisher).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onComplete();
        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());
        subscriber.awaitOnComplete();
    }

    @Test
    void singleConcurrencyNullComplete() {
        toSource(publisher.flatMapMerge(i -> from((Integer) null), 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onComplete();
        Integer nextItem = subscriber.takeOnNext();
        assertNull(nextItem);
        subscriber.awaitOnComplete();
    }

    @Test
    void singleItemSourceCompleteFirst() {
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onComplete();
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));

        mappedPublisher.onNext(2);
        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());

        mappedPublisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void singleItemMappedCompleteFirst() {
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);

        mappedPublisher.onNext(2);
        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());

        mappedPublisher.onComplete();
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));

        publisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void singleItemMappedError() {
        toSource(publisher.flatMapMerge(i -> Publisher.<Integer>failed(DELIBERATE_EXCEPTION), 1))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void singleItemMappedErrorPostSourceComplete() {
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onComplete();

        mappedPublisher.onNext(2);
        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());

        mappedPublisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testDuplicateTerminal() {
        PublisherSource<Integer> mappedPublisher = subscriber -> {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onComplete();
            // intentionally violate the RS spec to verify the operator's behavior.
            // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.7
            subscriber.onComplete();
        };
        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = mock(Subscriber.class);
        toSource(publisher.flatMapMerge(i -> fromSource(mappedPublisher), 1)).subscribe(mockSubscriber);
        publisher.onNext(1);
        publisher.onComplete();
        verify(mockSubscriber).onComplete();
    }

    @Test
    void cancelPropagatedBeforeErrorButOriginalErrorPreserved() {
        CountDownLatch cancelledLatch = new CountDownLatch(1);
        publisher = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                }

                @Override
                public void cancel() {
                    try {
                        cancelledLatch.await();
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                    subscriber1.onError(new IllegalStateException("shouldn't reach the Subscriber!"));
                }
            });
            return subscriber1;
        });
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);

        assert executor != null;
        executor.execute(() -> mappedPublisher.onError(DELIBERATE_EXCEPTION));
        // Verify that cancel happens before terminal. This ensures that sources which allow for multiple sequential
        // Subscribers can clear out there current subscriber in preparation for the next Subscriber and avoid duplicate
        // subscribe related errors.
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));
        cancelledLatch.countDown();
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void sourceEmitsErrorNoOnNext() {
        toSource(publisher.flatMapMerge(i -> from(i + 1), 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void sourceEmitsErrorPostOnNexts() {
        toSource(publisher.flatMapMerge(i -> from(i + 1), 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onError(DELIBERATE_EXCEPTION);

        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void sourceEmitsErrorPostOnNextsMappedNotCompleted() {
        TestSubscription mappedSubscription = new TestSubscription();
        TestPublisher<Integer> mappedPublisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        assertTrue(mappedPublisher.isSubscribed());
        mappedPublisher.onSubscribe(mappedSubscription);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertTrue(mappedSubscription.isCancelled());
    }

    @Test
    void sourceCancelAlsoCancelMapped() {
        TestSubscription mappedSubscription = new TestSubscription();
        TestPublisher<Integer> mappedPublisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);
        publisher.onNext(1);
        assertTrue(mappedPublisher.isSubscribed());
        mappedPublisher.onSubscribe(mappedSubscription);
        subscription.cancel();
        assertTrue(mappedSubscription.isCancelled());
    }

    @Test
    void mappedCompletePostCancel() {
        mappedTerminalPostCancel(false);
    }

    @Test
    void mappedErrorPostCancel() {
        mappedTerminalPostCancel(true);
    }

    private void mappedTerminalPostCancel(boolean onError) {
        TestSubscription mappedSubscription = new TestSubscription();
        TestPublisher<Integer> mappedPublisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);
        publisher.onNext(1);
        assertTrue(mappedPublisher.isSubscribed());
        mappedPublisher.onSubscribe(mappedSubscription);
        subscription.cancel();
        assertTrue(mappedSubscription.isCancelled());

        mappedPublisher.onNext(2);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        if (onError) {
            mappedPublisher.onError(DELIBERATE_EXCEPTION);
        } else {
            mappedPublisher.onComplete();
        }
        // Since FlatMap needs to track the requestN amount leased to mapped Publishers, the cancel state is
        // aggressive in suppressing onNext signals (tck tests fail if signals are delivered after cancel).
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));
    }

    @Test
    void errorDeliveredWithoutDrainingOptimisticDemand() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });

        toSource(publisher.flatMapMerge(i -> range(0, 5), 10)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);

        verifyCumulativeDemand(upstreamSubscription, 10);
        publisher.onNext(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        publisher.onError(DELIBERATE_EXCEPTION);

        assertThat(subscriber.pollAllOnNext(), contains(0));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void errorFromMappedSubscriberIsSequencedWithOnNextSignals() throws InterruptedException {
        List<TestSubscriptionPublisherPair<Integer>> mappedPublishers = new ArrayList<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(i -> {
            TestSubscriptionPublisherPair<Integer> pair = new TestSubscriptionPublisherPair<>(i);
            mappedPublishers.add(pair);
            return pair.mappedPublisher;
        }, 2)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);

        verifyCumulativeDemand(upstreamSubscription, 2);
        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.mappedSubscription.awaitRequestN(1);
        second.doOnSubscribe(2);
        second.mappedSubscription.awaitRequestN(1);

        // Exhaust outstanding requestN from downstream
        first.mappedPublisher.onNext(10);
        assertThat(subscriber.pollAllOnNext(), contains(10));

        // These signals should be queued, and the error shouldn't jump the queue.
        second.mappedPublisher.onNext(11);
        second.mappedPublisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        assertTrue(upstreamSubscription.isCancelled());

        subscription.request(1);
        assertThat(subscriber.pollAllOnNext(), contains(11));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void mapperThrows() {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.<Integer>flatMapMerge(i -> {
            throw DELIBERATE_EXCEPTION;
        }, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);

        publisher.onNext(1);
        assertFalse(upstreamSubscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void mixedOrderMappedEmission() throws InterruptedException {
        List<TestSubscriptionPublisherPair<Integer>> mappedPublishers = new ArrayList<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(i -> {
            TestSubscriptionPublisherPair<Integer> pair = new TestSubscriptionPublisherPair<>(i);
            mappedPublishers.add(pair);
            return pair.mappedPublisher;
        }, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);

        // Should not request more than max concurrency.
        verifyCumulativeDemand(upstreamSubscription, 2);
        publisher.onNext(1);
        publisher.onNext(2);

        // Verify there are two mapped Publishers, and each is given 1 requestN.
        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        first.doOnSubscribe(1);
        first.mappedSubscription.awaitRequestN(5);

        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);
        second.doOnSubscribe(2);
        second.mappedSubscription.awaitRequestN(3);

        // Mixed emissions between the first two publishers
        first.mappedPublisher.onNext(1, 2);
        second.mappedPublisher.onNext(3, 4, 5);
        assertThat(subscriber.pollAllOnNext(), contains(1, 2, 3, 4, 5));

        // Complete second and verify first can still publish
        second.mappedPublisher.onComplete();
        first.mappedPublisher.onNext(6, 7, 8);
        assertThat(subscriber.pollAllOnNext(), contains(6, 7, 8));

        first.mappedPublisher.onComplete();
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));

        verifyCumulativeDemand(upstreamSubscription, 4);

        // Emit a third item, and it should be given the max quota.
        publisher.onNext(3);
        assertThat(mappedPublishers, hasSize(3));
        TestSubscriptionPublisherPair<Integer> third = mappedPublishers.get(2);
        third.doOnSubscribe(3);
        third.mappedSubscription.awaitRequestN(5);

        // terminate the Publisher, verify mapped items still emitted and termination delayed.
        publisher.onComplete();
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));

        // terminate the outstanding mapped publisher and verify the Publisher terminates
        third.mappedPublisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void requestLongMaxMultipleTimes() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();

        subscription.request(Long.MAX_VALUE);
        verifyCumulativeDemand(upstreamSubscription, 2);
        publisher.onNext(1);
        publisher.onNext(2);

        subscription.request(Long.MAX_VALUE);
        verifyCumulativeDemand(upstreamSubscription, 4);
        publisher.onNext(3);
        publisher.onNext(4);
        verifyCumulativeDemand(upstreamSubscription, 6);
    }

    @Test
    void accumulateToLongMax() throws InterruptedException {
        accumulateToMax(Long.MAX_VALUE);
    }

    @Test
    void accumulateToIntMax() throws InterruptedException {
        accumulateToMax(Integer.MAX_VALUE);
    }

    private void accumulateToMax(long max) throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(max - 1);

        verifyCumulativeDemand(upstreamSubscription, 2);
        subscription.request(2);
        verifyCumulativeDemand(upstreamSubscription, 2);
        publisher.onNext(1, 3);
        verifyCumulativeDemand(upstreamSubscription, 4);
    }

    @Test
    void requestAfterMappedError() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.<Integer>flatMapMergeDelayError(i -> failed(DELIBERATE_EXCEPTION), 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        verifyCumulativeDemand(upstreamSubscription, 2);

        publisher.onNext(1, 2);
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));
        publisher.onComplete();
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void requestMultipleTimes() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 10)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(2);
        verifyCumulativeDemand(upstreamSubscription, 10);

        publisher.onNext(1, 2, 3, 4, 5);
        assertThat(subscriber.pollAllOnNext(), contains(1, 2));

        subscription.request(3);
        publisher.onNext(6, 7, 8, 9, 10);
        assertThat(subscriber.pollAllOnNext(), contains(3, 4, 5));

        subscription.request(7);
        assertThat(subscriber.pollAllOnNext(), contains(6, 7, 8, 9, 10));

        verifyCumulativeDemand(upstreamSubscription, 20);
        publisher.onNext(11, 12, 13, 14, 15);
        assertThat(subscriber.pollAllOnNext(), contains(11, 12));
    }

    @Test
    void requestMultipleTimesBreachMaxConcurrency() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(2);
        subscription.request(2);
        verifyCumulativeDemand(upstreamSubscription, 2);

        publisher.onNext(1, 2);
        verifyCumulativeDemand(upstreamSubscription, 4);
        publisher.onNext(3, 4);
        verifyCumulativeDemand(upstreamSubscription, 6);

        publisher.onComplete();
        assertThat(subscriber.pollAllOnNext(), contains(1, 2, 3, 4));
        subscriber.awaitOnComplete();
    }

    @Test
    void multipleMappedErrors() {
        Queue<DeliberateException> errors = new ArrayDeque<>();
        toSource(publisher.<Integer>flatMapMergeDelayError(i -> {
            DeliberateException de = new DeliberateException();
            errors.add(de);
            return failed(de);
        }, 2, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);

        publisher.onNext(1, 2);
        publisher.onComplete();
        Throwable cause = subscriber.awaitOnError();
        assertThat(errors, hasSize(2));
        assertThat(asList(cause.getSuppressed()), hasSize(1));
        assertThat(cause, is(errors.poll()));
        assertThat(cause.getSuppressed()[0], is(errors.poll()));
    }

    @Test
    void subscriptionReentry() throws InterruptedException {
        Queue<Integer> resultsQueue = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final int maxRange = 1000000;
        toSource(publisher.flatMapMerge(i -> range(0, maxRange), 10)).subscribe(
                new Subscriber<Integer>() {
                    @Nullable
                    private Subscription subscription;
                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(@Nullable final Integer integer) {
                        assert subscription != null;
                        resultsQueue.add(integer);
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        causeRef.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        publisher.onNext(1);
        publisher.onComplete();
        latch.await();
        assertNull(causeRef.get());
        for (int i = 0; i < maxRange; ++i) {
            assertThat(resultsQueue.poll(), is(i));
        }
        assertTrue(resultsQueue.isEmpty());
    }

    @Test
    void emitFromExecutor() throws InterruptedException, ExecutionException {
        List<TestSubscriptionPublisherPair<Integer>> mappedPublishers = new ArrayList<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(i -> {
            TestSubscriptionPublisherPair<Integer> pair = new TestSubscriptionPublisherPair<>(i);
            mappedPublishers.add(pair);
            return pair.mappedPublisher;
        }, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        verifyCumulativeDemand(upstreamSubscription, 2);

        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.mappedSubscription.awaitRequestN(3);
        second.doOnSubscribe(2);
        second.mappedSubscription.awaitRequestN(3);

        assert executorService != null;
        Future<?> firstEmitFuture = executorService.submit(() -> {
            first.mappedPublisher.onNext(1, 2, 3);
            first.mappedPublisher.onComplete();
        });

        Future<?> secondEmitFuture = executorService.submit(() -> {
            second.mappedPublisher.onNext(4, 5, 6);
            second.mappedPublisher.onComplete();
        });

        publisher.onComplete();
        firstEmitFuture.get();
        secondEmitFuture.get();

        assertThat(subscriber.takeOnNext(6), containsInAnyOrder(1, 2, 3, 4, 5, 6));
        subscriber.awaitOnComplete();
    }

    @Test
    void requestAndEmitConcurrency() throws Exception {
        int totalToRequest = 1000;
        List<Integer> received = new ArrayList<>(totalToRequest);
        CountDownLatch requestingStarting = new CountDownLatch(1);
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2).beforeOnNext(received::add))
                .subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        assert executorService != null;
        Future<?> submitFuture = executorService.submit(() -> {
            requestingStarting.countDown();
            for (int i = 0; i < totalToRequest; i++) {
                subscription.request(1);
            }
        });

        // Just to make sure we have both threads running concurrently.
        requestingStarting.await();
        for (int i = 0; i < totalToRequest; i++) {
            upstreamSubscription.awaitRequestN(i + 1);
            publisher.onNext(i);
        }

        submitFuture.get();
        assertThat("Unexpected items emitted.", received, hasSize(totalToRequest));
        assertThat(received, containsInAnyOrder(IntStream.range(0, totalToRequest).boxed().toArray()));
    }

    @Test
    void concurrentMappedAndPublisherTermination() throws Exception {
        assert executor != null;
        Single<List<Integer>> single = range(0, 1000)
                .flatMapMerge(i -> executor.submit(() -> i).toPublisher(), 1024)
                .collect(ArrayList::new, (ints, i) -> {
                    ints.add(i);
                    return ints;
                });
        for (int i = 0; i < 10; i++) {
            List<Integer> list = single.toFuture().get();
            assertThat("Unexpected items received", list, hasSize(1000));
        }
    }

    @Test
    void concurrentMappedDeliveryOrderPreserved() {
        assert executor != null;
        final int upstreamItems = 10000;
        final int mappedItems = 5;
        final TestPublisherSubscriber<IntPair> subscriber = new TestPublisherSubscriber<>();
        Publisher<IntPair> publisher = range(0, upstreamItems).flatMapMerge(outer -> range(0, mappedItems)
                .map(inner -> new IntPair(outer, inner)).publishAndSubscribeOn(executor), upstreamItems);
        toSource(publisher).subscribe(subscriber);
        subscriber.awaitSubscription().request(upstreamItems * mappedItems);

        // Wait until completion, and then verify that for each mapped source its elements are delivered in order
        // despite concurrent emission from the mapped sources.
        List<IntPair> emissionList = subscriber.takeOnNext(upstreamItems * mappedItems);
        subscriber.awaitOnComplete();
        assertThat(emissionList, hasSize(upstreamItems * mappedItems));
        Map<Integer, List<Integer>> emissionMap = new HashMap<>();
        for (IntPair intPair : emissionList) {
            List<Integer> innerList = emissionMap.computeIfAbsent(intPair.x, key -> new ArrayList<>(mappedItems));
            // Publisher#range(..) emits in order, so if the previously emitted item for this mapped source is greater
            // than or equal to we emitted out of order and should fail.
            if (innerList.isEmpty() || innerList.get(innerList.size() - 1) < intPair.y) {
                innerList.add(intPair.y);
            } else {
                throw new AssertionError("mapped element: " + intPair.x + " had out of order emissions. existing: " +
                        innerList + " new: " + intPair.y);
            }
        }
    }

    @Test
    void concurrentSkipQueueDoesNotDeadlock() throws Throwable {
        assert executorService != null;
        List<TestSubscriptionPublisherPair<Integer>> mappedPublishers = new ArrayList<>();
        CountDownLatch onNextLatch = new CountDownLatch(1);
        CountDownLatch onNextSecondLatch = new CountDownLatch(2);
        CountDownLatch onNextThirdLatch = new CountDownLatch(3);
        CountDownLatch onNextWaitLatch = new CountDownLatch(1);
        CountDownLatch onCompleteLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(i -> {
            TestSubscriptionPublisherPair<Integer> pair = new TestSubscriptionPublisherPair<>(i);
            mappedPublishers.add(pair);
            return pair.mappedPublisher;
        }, 3)).subscribe(new Subscriber<Integer>() {
            @Override
            public void onSubscribe(final Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final Integer integer) {
                onNextLatch.countDown();
                onNextSecondLatch.countDown();
                onNextThirdLatch.countDown();
                try {
                    onNextWaitLatch.await();
                } catch (InterruptedException e) {
                    throwException(e);
                }
            }

            @Override
            public void onError(final Throwable t) {
                errorRef.set(t);
                onCompleteLatch.countDown();
            }

            @Override
            public void onComplete() {
                onCompleteLatch.countDown();
            }
        });

        upstreamSubscription.awaitRequestN(2);
        publisher.onNext(1, 2);
        publisher.onComplete();

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        Future<?> f = executorService.submit(() -> {
            try {
                first.doOnSubscribe(1);
                first.mappedSubscription.awaitRequestN(1);
                first.mappedPublisher.onNext(1);
            } catch (Throwable cause) {
                first.mappedPublisher.onError(cause);
                return;
            }
            first.mappedPublisher.onComplete();
        });

        // Wait for the executorService thread to be in onNext, we want to force concurrent delivery.
        onNextLatch.await();

        // Deliver the first signal, and allow the executorService thread to exit onNext.
        second.doOnSubscribe(2);
        second.mappedSubscription.awaitRequestN(2);
        second.mappedPublisher.onNext(2);
        onNextWaitLatch.countDown();

        // Wait for the onNext from this thread to be delivered in the executorService thread.
        onNextSecondLatch.await();

        // Wait for the executorService thread to deliver onComplete and release the lock in the operator.
        f.get();

        // The second mapped publisher previously had items queued, and there are no other thread holding the lock in
        // the operator, it should be delivered.
        second.mappedPublisher.onNext(3);
        onNextThirdLatch.await();

        // Deliver the last onComplete and verify normal termination.
        second.mappedPublisher.onComplete();
        onCompleteLatch.await();
        Throwable cause = errorRef.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    void concurrentMappedErrorAndPublisherTermination() throws Exception {
        assert executor != null;
        AtomicReference<Throwable> error = new AtomicReference<>();
        Single<List<Integer>> single = range(0, 1000)
                .flatMapMergeDelayError(i -> executor.submit(() -> {
                    if (i % 2 == 0) {
                        return i;
                    }
                    throw new DeliberateException();
                }).toPublisher(), 1024, 500)
                .onErrorResume(t -> {
                    error.set(t);
                    return Publisher.empty();
                }).collect(ArrayList::new, (ints, s) -> {
                    ints.add(s);
                    return ints;
                });

        for (int i = 0; i < 10; i++) {
            List<Integer> list = single.toFuture().get();
            assertThat("Unexpected items received", list, hasSize(500));
            Throwable cause = error.get();
            assertThat(cause, instanceOf(DeliberateException.class));
            // everything but the first error is suppressed
            assertThat("Unexpected exception.", asList(cause.getSuppressed()), hasSize(499));
        }
    }

    @Test
    void mappedQuotaIsRenewedAfterSingleMappedPublisherCompletes() throws InterruptedException {
        List<TestSubscriptionPublisherPair<Integer>> mappedPublishers = new ArrayList<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(i -> {
            TestSubscriptionPublisherPair<Integer> pair = new TestSubscriptionPublisherPair<>(i);
            mappedPublishers.add(pair);
            return pair.mappedPublisher;
        }, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);

        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.mappedSubscription.awaitRequestN(2);
        second.doOnSubscribe(2);
        second.mappedSubscription.awaitRequestN(2);

        // Delay the completion of the first mapped publisher, to verify that flatMap requests more upstream demand.
        second.mappedPublisher.onNext(3, 4);
        second.mappedSubscription.awaitRequestN(4);
        assertThat(subscriber.pollAllOnNext(), contains(3, 4));

        second.mappedPublisher.onComplete();
        upstreamSubscription.awaitRequestN(3);

        publisher.onNext(3);
        TestSubscriptionPublisherPair<Integer> third = mappedPublishers.get(2);
        third.doOnSubscribe(3);
        third.mappedSubscription.awaitRequestN(2);

        third.mappedPublisher.onComplete();
        first.mappedPublisher.onComplete();
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));

        publisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void mappedQuotaIsRenewedAfterExhausted() throws InterruptedException {
        List<TestSubscriptionPublisherPair<Integer>> mappedPublishers = new ArrayList<>();
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(i -> {
            TestSubscriptionPublisherPair<Integer> pair = new TestSubscriptionPublisherPair<>(i);
            mappedPublishers.add(pair);
            return pair.mappedPublisher;
        }, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);

        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.mappedSubscription.awaitRequestN(2);
        second.doOnSubscribe(2);
        second.mappedSubscription.awaitRequestN(2);

        first.mappedPublisher.onNext(1, 2);
        first.mappedSubscription.awaitRequestN(4);

        second.mappedPublisher.onNext(3, 4);
        second.mappedSubscription.awaitRequestN(4);

        assertThat(subscriber.pollAllOnNext(), contains(1, 2, 3, 4));
        first.mappedPublisher.onComplete();
        second.mappedPublisher.onComplete();
        assertThat(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS), is(nullValue()));

        publisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void lastActiveMappedPublisherCanDeliverData() throws Throwable {
        final int mappedRangeBegin = 5;
        final int mappedRangeEnd = 10;
        CountDownLatch latch = new CountDownLatch(mappedRangeEnd - mappedRangeBegin);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        final int lastIndex = 100;
        toSource(range(0, lastIndex)
                .flatMapMerge(i -> i == lastIndex - 1 ? range(mappedRangeBegin, mappedRangeEnd) : never(), lastIndex)
        ).subscribe(new Subscriber<Integer>() {
            @Nullable
            private Subscription subscription;
            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(@Nullable final Integer integer) {
                assert subscription != null;
                latch.countDown();
                subscription.request(1);
            }

            @Override
            public void onError(final Throwable t) {
                causeRef.set(t);
                latchToZero();
            }

            @Override
            public void onComplete() {
                causeRef.set(new IllegalStateException("onComplete not expected"));
                latchToZero();
            }

            private void latchToZero() {
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        });
        latch.await();
        Throwable cause = causeRef.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    void internalQueueSizeIsBoundedByDownstreamDemand() throws Throwable {
        // We only expect 1 signal, and if we get 2 then the test has failed. The goal is to only request 1 onNext and
        // verify the operator bounds the amount of memory behind the scenes.
        final AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final long memory = Runtime.getRuntime().maxMemory();
        final byte[] array = new byte[(int) min(Integer.MAX_VALUE >>> 3, memory)];
        array[0] = 1; // this value doesn't matter, just to suppressing warning about not writing to array.
        toSource(range(0, 100000000).flatMapMerge(i -> from(array.clone()), 1))
                .subscribe(new Subscriber<byte[]>() {
                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        // Only request a single element, flatMapMerge shouldn't continue to request and queue data
                        // behind the scenes.
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(@Nullable final byte[] integer) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onError(final Throwable t) {
                        throwableRef.set(t);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        throwableRef.set(new IllegalStateException("unexpected onComplete"));
                        countDownLatch.countDown();
                    }
                });
        // Wait some time to validate the operator bounds internal queue sizes. This is expected to timeout as we are
        // asserting that a condition doesn't happen.
        final boolean timedOut = countDownLatch.await(2, SECONDS);
        final Throwable cause = throwableRef.get();
        if (cause != null) {
            throw cause;
        }
        assertFalse(timedOut, "The countDownLatch didn't timeout, and there was also no exception?!");
    }

    private static final class TestSubscriptionPublisherPair<T> {
        @Nullable
        final T item;
        final TestSubscription mappedSubscription = new TestSubscription();
        final TestPublisher<T> mappedPublisher = new TestPublisher.Builder<T>()
                .disableAutoOnSubscribe().build();

        TestSubscriptionPublisherPair(@Nullable T item) {
            this.item = item;
        }

        private void doOnSubscribe(T expectedItem) {
            assertThat(item, is(expectedItem));
            assertThat(mappedSubscription.requested(), is(0L));
            mappedPublisher.onSubscribe(mappedSubscription);
        }
    }

    private static void verifyCumulativeDemand(TestSubscription testSubscription, long totalRequestN)
            throws InterruptedException {
        testSubscription.awaitRequestN(totalRequestN);
        assertThat(testSubscription.requested(), is(totalRequestN));
    }

    private static final class IntPair {
        final int x;
        final int y;

        private IntPair(final int x, final int y) {
            this.x = x;
            this.y = y;
        }
    }
}
