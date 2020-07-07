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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.PublisherSource.Subscription;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifyOriginalAndSuppressedCauses;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifySuppressed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PublisherFlatMapMergeTest {
    private static final long TERMINAL_POLL_MS = 10;
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Nullable
    private static ExecutorService executorService;
    @Nullable
    private static Executor executor;

    private final TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
    private TestPublisher<Integer> publisher = new TestPublisher<>();

    @BeforeClass
    public static void beforeClass() {
        executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
        executor = io.servicetalk.concurrent.api.Executors.from(executorService);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Test
    public void singleConcurrencyComplete() throws InterruptedException {
        singleConcurrencyComplete(publisher.flatMapMerge(i -> from(i + 1), 1, 1));
    }

    @Test
    public void defaultConcurrencyComplete() throws InterruptedException {
        singleConcurrencyComplete(publisher.flatMapMerge(i -> from(i + 1)));
    }

    @Test
    public void singleConcurrencyDelayErrorComplete() throws InterruptedException {
        singleConcurrencyComplete(publisher.flatMapMergeDelayError(i -> from(i + 1), 1, 1));
    }

    @Test
    public void defaultConcurrencyDelayErrorComplete() throws InterruptedException {
        singleConcurrencyComplete(publisher.flatMapMergeDelayError(i -> from(i + 1)));
    }

    private void singleConcurrencyComplete(Publisher<Integer> flatMappedPublisher) throws InterruptedException {
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
    public void singleConcurrencyNullComplete() throws InterruptedException {
        toSource(publisher.flatMapMerge(i -> from((Integer) null), 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onComplete();
        Integer nextItem = subscriber.takeOnNext();
        assertNull(nextItem);
        subscriber.awaitOnComplete();
    }

    @Test
    public void singleItemSourceCompleteFirst() throws InterruptedException {
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onComplete();
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));

        mappedPublisher.onNext(2);
        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());

        mappedPublisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void singleItemMappedCompleteFirst() throws InterruptedException {
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);

        mappedPublisher.onNext(2);
        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());

        mappedPublisher.onComplete();
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));

        publisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void singleItemMappedError() throws InterruptedException {
        toSource(publisher.flatMapMerge(i -> Publisher.<Integer>failed(DELIBERATE_EXCEPTION), 1, 1))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void singleItemMappedErrorPostSourceComplete() throws InterruptedException {
        TestPublisher<Integer> mappedPublisher = new TestPublisher<>();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1, 1)).subscribe(subscriber);
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
    public void sourceEmitsErrorNoOnNext() throws InterruptedException {
        toSource(publisher.flatMapMerge(i -> from(i + 1), 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void sourceEmitsErrorPostOnNexts() throws InterruptedException {
        toSource(publisher.flatMapMerge(i -> from(i + 1), 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        publisher.onError(DELIBERATE_EXCEPTION);

        Integer nextItem = subscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void sourceEmitsErrorPostOnNextsMappedNotCompleted() throws InterruptedException {
        TestSubscription mappedSubscription = new TestSubscription();
        TestPublisher<Integer> mappedPublisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        publisher.onNext(1);
        assertTrue(mappedPublisher.isSubscribed());
        mappedPublisher.onSubscribe(mappedSubscription);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertTrue(mappedSubscription.isCancelled());
    }

    @Test
    public void sourceCancelAlsoCancelMapped() throws InterruptedException {
        TestSubscription mappedSubscription = new TestSubscription();
        TestPublisher<Integer> mappedPublisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1, 1)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);
        publisher.onNext(1);
        assertTrue(mappedPublisher.isSubscribed());
        mappedPublisher.onSubscribe(mappedSubscription);
        subscription.cancel();
        assertTrue(mappedSubscription.isCancelled());
    }

    @Test
    public void mappedCompletePostCancel() throws InterruptedException {
        mappedTerminalPostCancel(false);
    }

    @Test
    public void mappedErrorPostCancel() throws InterruptedException {
        mappedTerminalPostCancel(true);
    }

    private void mappedTerminalPostCancel(boolean onError) throws InterruptedException {
        TestSubscription mappedSubscription = new TestSubscription();
        TestPublisher<Integer> mappedPublisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build();
        toSource(publisher.flatMapMerge(i -> mappedPublisher, 1, 1)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);
        publisher.onNext(1);
        assertTrue(mappedPublisher.isSubscribed());
        mappedPublisher.onSubscribe(mappedSubscription);
        subscription.cancel();
        assertTrue(mappedSubscription.isCancelled());

        mappedPublisher.onNext(2);
        // We don't allow any emissions post cancel/terminal.
        assertThat(subscriber.pollAllOnNext(), is(empty()));
        if (onError) {
            mappedPublisher.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            mappedPublisher.onComplete();
            // Since FlatMap needs to track the requestN amount leased to mapped Publishers, the cancel state is
            // aggressive in suppressing onNext signals, and this currently extends to onComplete.
            assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));
        }
    }

    @Test
    public void mapperThrows() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.<Integer>flatMapMerge(i -> {
            throw DELIBERATE_EXCEPTION;
        }, 1, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);

        try {
            publisher.onNext(1);
            fail();
        } catch (Throwable cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);

            // Now simulate failing the publisher by emit onError(...)
            publisher.onError(cause);
        }

        assertFalse(upstreamSubscription.isCancelled());
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void mixedOrderMappedEmission() throws InterruptedException {
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
        }, 2, 5)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);

        // Should not request more than max concurrency.
        verifyCumulativeDemand(upstreamSubscription, 2);
        publisher.onNext(1);
        publisher.onNext(2);

        // Verify there are two mapped Publishers, and each is given 1 requestN.
        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        first.doOnSubscribe(1);
        first.verifyCumulativeDemand(5);

        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);
        second.doOnSubscribe(2);
        second.verifyCumulativeDemand(5);

        // Mixed emissions between the first two publishers
        first.mappedPublisher.onNext(1, 2);
        second.mappedPublisher.onNext(3, 4, 5);
        assertThat(subscriber.pollAllOnNext(), contains(1, 2, 3, 4, 5));

        // Complete second and verify first can still publish
        second.mappedPublisher.onComplete();
        first.mappedPublisher.onNext(6, 7, 8);
        assertThat(subscriber.pollAllOnNext(), contains(6, 7, 8));

        first.mappedPublisher.onComplete();
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));

        verifyCumulativeDemand(upstreamSubscription, 4);

        // Emit a third item, and it should be given the max quota.
        publisher.onNext(3);
        assertThat(mappedPublishers, hasSize(3));
        TestSubscriptionPublisherPair<Integer> third = mappedPublishers.get(2);
        third.doOnSubscribe(3);
        third.verifyCumulativeDemand(5);

        // terminate the Publisher, verify mapped items still emitted and termination delayed.
        publisher.onComplete();
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));

        // terminate the outstanding mapped publisher and verify the Publisher terminates
        third.mappedPublisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestLongMaxMultipleTimes() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2, 1)).subscribe(subscriber);
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
    public void accumulateToLongMax() throws InterruptedException {
        accumulateToMax(Long.MAX_VALUE);
    }

    @Test
    public void accumulateToIntMax() throws InterruptedException {
        accumulateToMax(Integer.MAX_VALUE);
    }

    private void accumulateToMax(long max) throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2, 1)).subscribe(subscriber);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(max - 1);

        verifyCumulativeDemand(upstreamSubscription, 2);
        subscription.request(2);
        verifyCumulativeDemand(upstreamSubscription, 2);
        publisher.onNext(1, 3);
        verifyCumulativeDemand(upstreamSubscription, 4);
    }

    @Test
    public void requestAfterMappedError() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.<Integer>flatMapMergeDelayError(i -> Publisher.failed(DELIBERATE_EXCEPTION), 2, 1))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        verifyCumulativeDemand(upstreamSubscription, 2);

        publisher.onNext(1, 2);
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));
        publisher.onComplete();
        verifySuppressed(subscriber.awaitOnError(), DELIBERATE_EXCEPTION);
    }

    @Test
    public void requestMultipleTimes() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 10, 1)).subscribe(subscriber);
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
    public void requestMultipleTimesBreachMaxConcurrency() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2, 1)).subscribe(subscriber);
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
    public void multipleMappedErrors() throws InterruptedException {
        List<DeliberateException> errors = new ArrayList<>();
        toSource(publisher.<Integer>flatMapMergeDelayError(i -> {
            DeliberateException de = new DeliberateException();
            errors.add(de);
            return Publisher.failed(de);
        }, 2, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);

        publisher.onNext(1, 2);
        publisher.onComplete();
        assertThat(errors, hasSize(2));
        DeliberateException first = errors.remove(0);
        for (DeliberateException error : errors) {
            verifyOriginalAndSuppressedCauses(subscriber.awaitOnError(), first, error);
        }
    }

    @Test
    public void subscriptionReentry() throws InterruptedException {
        Queue<Integer> resultsQueue = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final int maxRange = 1000000;
        toSource(publisher.flatMapMerge(i -> range(0, maxRange), 10, 1)).subscribe(
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
    public void emitFromExecutor() throws InterruptedException, ExecutionException {
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
        }, 2, 5)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        verifyCumulativeDemand(upstreamSubscription, 2);

        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.verifyCumulativeDemand(5);
        second.doOnSubscribe(2);
        second.verifyCumulativeDemand(5);

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

        subscriber.awaitOnComplete(false);
        assertThat(subscriber.pollAllOnNext(), containsInAnyOrder(1, 2, 3, 4, 5, 6));
    }

    @Test
    public void requestAndEmitConcurrency() throws Exception {
        int totalToRequest = 1000;
        List<Integer> received = new ArrayList<>(totalToRequest);
        CountDownLatch requestingStarting = new CountDownLatch(1);
        TestSubscription upstreamSubscription = new TestSubscription();
        publisher = new TestPublisher.Builder<Integer>()
                .disableAutoOnSubscribe().build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        toSource(publisher.flatMapMerge(Publisher::from, 2, 5).beforeOnNext(received::add))
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
    public void concurrentMappedAndPublisherTermination() throws Exception {
        assert executor != null;
        Single<List<Integer>> single = range(0, 1000)
                .flatMapMerge(i -> executor.submit(() -> i).toPublisher(), 1024, 10)
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
    public void concurrentMappedDeliveryOrderPreserved() throws InterruptedException {
        assert executor != null;
        final int upstreamItems = 10000;
        final int mappedItems = 5;
        final TestCollectingPublisherSubscriber<IntPair> subscriber = new TestCollectingPublisherSubscriber<>();
        Publisher<IntPair> publisher = range(0, upstreamItems).flatMapMerge(outer -> range(0, mappedItems)
                .map(inner -> new IntPair(outer, inner)).publishAndSubscribeOn(executor), upstreamItems, mappedItems);
        toSource(publisher).subscribe(subscriber);
        subscriber.awaitSubscription().request(upstreamItems * mappedItems);

        // Wait until completion, and then verify that for each mapped source its elements are delivered in order
        // despite concurrent emission from the mapped sources.
        subscriber.awaitOnComplete(false);
        List<IntPair> emissionList = subscriber.pollAllOnNext();
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
    public void concurrentMappedErrorAndPublisherTermination() throws Exception {
        assert executor != null;
        AtomicReference<Throwable> error = new AtomicReference<>();
        Single<List<Integer>> single = range(0, 1000)
                .flatMapMergeDelayError(i -> executor.submit(() -> {
                    if (i % 2 == 0) {
                        return i;
                    }
                    throw new DeliberateException();
                }).toPublisher(), 1024, 10)
                .recoverWith(t -> {
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
            assertThat("Unexpected exception.", cause, instanceOf(CompositeException.class));
            assertThat("Unexpected exception.", cause.getCause(), instanceOf(DeliberateException.class));
            assertThat("Unexpected exception.", cause.getSuppressed().length,
                    equalTo(499/*everything but the first error is suppressed*/));
        }
    }

    @Test
    public void mappedQuotaIsRenewedAfterSingleMappedPublisherCompletes() throws InterruptedException {
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
        }, 2, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);

        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.verifyCumulativeDemand(2);
        second.doOnSubscribe(2);
        second.verifyCumulativeDemand(2);

        // Delay the completion of the first mapped publisher, to verify that flatMap requests more upstream demand.
        second.mappedPublisher.onNext(3, 4);
        second.verifyCumulativeDemand(4);
        assertThat(subscriber.pollAllOnNext(), contains(3, 4));

        second.mappedPublisher.onComplete();
        upstreamSubscription.awaitRequestN(3);

        publisher.onNext(3);
        TestSubscriptionPublisherPair<Integer> third = mappedPublishers.get(2);
        third.doOnSubscribe(3);
        third.verifyCumulativeDemand(2);

        third.mappedPublisher.onComplete();
        first.mappedPublisher.onComplete();
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));

        publisher.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void mappedQuotaIsRenewedAfterExhausted() throws InterruptedException {
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
        }, 2, 2)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);

        publisher.onNext(1, 2);

        assertThat(mappedPublishers, hasSize(2));
        TestSubscriptionPublisherPair<Integer> first = mappedPublishers.get(0);
        TestSubscriptionPublisherPair<Integer> second = mappedPublishers.get(1);

        first.doOnSubscribe(1);
        first.verifyCumulativeDemand(2);
        second.doOnSubscribe(2);
        second.verifyCumulativeDemand(2);

        first.mappedPublisher.onNext(1, 2);
        first.verifyCumulativeDemand(4);

        second.mappedPublisher.onNext(3, 4);
        second.verifyCumulativeDemand(4);

        assertThat(subscriber.pollAllOnNext(), contains(1, 2, 3, 4));
        first.mappedPublisher.onComplete();
        second.mappedPublisher.onComplete();
        assertFalse(subscriber.pollTerminal(TERMINAL_POLL_MS, MILLISECONDS));

        publisher.onComplete();
        subscriber.awaitOnComplete();
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

        private void verifyCumulativeDemand(long totalRequestN) throws InterruptedException {
            PublisherFlatMapMergeTest.verifyCumulativeDemand(mappedSubscription, totalRequestN);
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
