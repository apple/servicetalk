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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifyOriginalAndSuppressedCauses;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifySuppressed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PublisherFlatMapSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private final TestPublisher<Integer> source = new TestPublisher<>();
    private final TestSubscription subscription = new TestSubscription();
    private static ExecutorService executorService;
    private static Executor executor;

    @BeforeClass
    public static void beforeClass() {
        executorService = Executors.newFixedThreadPool(10);
        executor = io.servicetalk.concurrent.api.Executors.from(executorService);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @Test
    public void concurrentSingleAndPublisherTermination() throws Exception {
        final List<String> elements = range(0, 1000).mapToObj(Integer::toString).collect(toList());
        final Publisher<String> publisher = fromIterable(elements);
        final Single<List<String>> single = publisher.flatMapMergeSingle(x -> executor.submit(() -> x), 1024)
                .collect(ArrayList::new, (strings, s) -> {
                    strings.add(s);
                    return strings;
                });
        for (int i = 0; i < 10; i++) {
            List<String> list = single.toFuture().get();
            assertThat("Unexpected items received", list, hasSize(1000));
        }
    }

    @Test
    public void concurrentSingleErrorAndPublisherTermination() throws Exception {
        final Publisher<Integer> publisher = fromIterable(() -> range(0, 1000).iterator());
        AtomicReference<Throwable> error = new AtomicReference<>();
        final Single<List<Integer>> single = publisher.flatMapMergeSingleDelayError(x -> executor.submit(() -> {
            if (x % 2 == 0) {
                return x;
            }
            throw new DeliberateException();
        }), 1024).recoverWith(t -> {
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
    public void testSingleItemSyncSingle() {
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSingleItemCompletesWithNull() {
        toSource(source.<Integer>flatMapMergeSingle(integer1 -> Single.succeeded(null), 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(new Integer[]{null}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSingleItemSourceCompleteFirst() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onComplete();
        single.onSuccess(2);
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSingleItemSingleCompleteFirst() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        single.onSuccess(2);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSingleItemSingleError() {
        toSource(source.<Integer>flatMapMergeSingle(integer1 -> Single.failed(DELIBERATE_EXCEPTION), 2))
                .subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSingleErrorPostSourceComplete() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onComplete();
        single.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSourceEmitsErrorNoOnNexts() {
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSourceEmitsErrorPostOnNexts() {
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(2));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSourceEmitsErrorPostOnNextsSingleNotCompleted() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>(true);
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        single.verifyCancelled();
        single.onError(new DeliberateException());
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testSubscriberCancel() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>();
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        subscriber.cancel();
        single.verifyCancelled();
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testSingleCompletePostCancel() {
        final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber.Builder<Integer>()
                .disableDemandCheck().build();
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>(true);
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        subscriber.cancel();
        single.verifyCancelled();
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        single.onSuccess(4);
        assertThat(subscriber.takeItems(), contains(4));
        source.onComplete();
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testSingleErrorPostCancel() {
        LegacyTestSingle<Integer> single = new LegacyTestSingle<>(true);
        toSource(source.flatMapMergeSingle(integer1 -> single, 2)).subscribe(subscriber);
        subscriber.request(1);
        source.onNext(1);
        subscriber.cancel();
        single.verifyCancelled();
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        single.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testMaxConcurrency() {
        List<LegacyTestSingle<Integer>> emittedSingles = new ArrayList<>();
        toSource(source.flatMapMergeSingle(integer -> {
            LegacyTestSingle<Integer> s = new LegacyTestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(3);
        // Should not request more than max concurrency.
        assertThat(subscription.requested(), is(2L));

        source.onNext(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));
        emittedSingles.remove(0).onSuccess(2);
        assertThat(subscriber.takeItems(), contains(2));

        // Total requested must equal actual requested.
        assertThat(subscription.requested(), is(3L));

        emittedSingles.remove(0).onSuccess(3);
        assertThat(subscriber.takeItems(), contains(3));
        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(1));
        emittedSingles.remove(0).onSuccess(4);
        assertThat(subscriber.takeItems(), contains(4));
        assertThat(subscriber.takeTerminal(), is(complete()));

        // Total requested must equal actual requested.
        assertThat(subscription.requested(), is(3L));
    }

    @Test
    public void testMapperThrows() {
        toSource(source.<Integer>flatMapMergeSingle(integer1 -> {
            throw DELIBERATE_EXCEPTION;
        }, 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(1);

        try {
            source.onNext(1);
            fail();
        } catch (Throwable cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);

            // Now simulate failing the publisher by emit onError(...)
            source.onError(cause);
        }
        assertFalse(subscription.isCancelled());
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testNoFlowControl() {
        List<LegacyTestSingle<Integer>> emittedSingles = new ArrayList<>();
        toSource(source.flatMapMergeSingle(integer1 -> {
            LegacyTestSingle<Integer> s1 = new LegacyTestSingle<>();
            emittedSingles.add(s1);
            return s1;
        }, 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(Long.MAX_VALUE);
        // Should not request more than max concurrency.
        assertThat(subscription.requested(), is(2L));

        source.onNext(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));
        emittedSingles.remove(0).onSuccess(2);
        assertThat(subscriber.takeItems(), contains(2));

        // Request enough on completion to reach max concurrency.
        assertThat(subscription.requested(), is(3L));

        emittedSingles.remove(0).onSuccess(3);
        // Request enough on completion to reach max concurrency.
        assertThat(subscription.requested(), is(4L));
        assertThat(subscriber.takeItems(), contains(3));

        source.onNext(1);
        source.onComplete();
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(1));
        emittedSingles.remove(0).onSuccess(4);
        assertThat(subscriber.takeItems(), contains(4));
        assertThat(subscriber.takeTerminal(), is(complete()));

        // Request enough on completion to reach max concurrency.
        assertThat(subscription.requested(), is(6L));
    }

    @Test
    public void testRequestPostSingleError() {
        toSource(source.<Integer>flatMapMergeSingleDelayError(integer1 -> Single.failed(DELIBERATE_EXCEPTION), 2))
                .subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(3);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1); // Request no more than max concurrency.
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        assertThat(subscription.requested(), is(3L));
        source.onNext(1); // Request more with 1 single completion.
        assertThat(subscription.requested(), is(3L));
        source.onComplete(); // Stop requesting more.
        verifySuppressed(subscriber.takeError(), DELIBERATE_EXCEPTION);
    }

    @Test
    public void testRequestMultipleTimes() {
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), 10)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 1);
        assertThat(subscriber.takeItems(), contains(2, 2));
        subscriber.request(2);
        source.onNext(1, 1);
        assertThat(subscriber.takeItems(), contains(2, 2));
    }

    @Test
    public void testRequestMultipleTimesBreachMaxConcurrency() {
        toSource(source.flatMapMergeSingle(integer -> Single.succeeded(2), 2)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(2);
        subscriber.request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 1);
        source.onNext(1, 1);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(2, 2, 2, 2));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testMultipleSingleErrors() {
        List<DeliberateException> errors = new ArrayList<>();
        toSource(source.flatMapMergeSingleDelayError(integer -> {
            DeliberateException de = new DeliberateException();
            errors.add(de);
            return Single.<Integer>failed(de);
        }, 2)).subscribe(subscriber);
        subscriber.request(3);
        source.onNext(1, 1);
        source.onComplete();
        assertThat("Unexpected emitted error count.", errors, hasSize(2));
        DeliberateException first = errors.remove(0);
        for (DeliberateException error : errors) {
            verifyOriginalAndSuppressedCauses(subscriber.takeError(), first, error);
        }
    }

    @Test
    public void testRequestLongMaxValue() {
        int maxConcurrency = 2;
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), maxConcurrency)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(Long.MAX_VALUE);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        source.onNext(2);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 1)));
        subscriber.request(Long.MAX_VALUE);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 1)));
    }

    @Test
    public void testAccumulateToLongMaxValue() {
        int maxConcurrency = 2;
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), maxConcurrency)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(Long.MAX_VALUE - 1);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        subscriber.request(2);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        source.onNext(1, 2);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 2)));
    }

    @Test
    public void testAccumulateToIntMaxValue() {
        int maxConcurrency = 2;
        toSource(source.flatMapMergeSingle(integer1 -> Single.succeeded(2), maxConcurrency)).subscribe(subscriber);
        source.onSubscribe(subscription);
        subscriber.request(Integer.MAX_VALUE - 1);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        subscriber.request(2);
        assertThat(subscription.requested(), is((long) maxConcurrency));
        source.onNext(1, 2);
        assertThat(subscription.requested(), is((long) (maxConcurrency + 2)));
    }

    @Test
    public void testReentry() throws InterruptedException {
        Queue<String> resultsQueue = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final Integer[] expectedNumbers = new Integer[1000000];
        // big enough to trigger stack overflow if we are not careful.
        for (int i = 0; i < expectedNumbers.length; ++i) {
            expectedNumbers[i] = i;
        }
        PublisherFlatMapSingle<Integer, String> pub = new PublisherFlatMapSingle<>(Publisher.from(expectedNumbers),
                value -> Single.succeeded(Integer.toString(value)),
                1, false, immediate());
        toSource(pub).subscribe(new Subscriber<String>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(String s) {
                resultsQueue.add(s);
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

        latch.await();
        assertNull(causeRef.get());
        for (Integer expectedNumber : expectedNumbers) {
            assertEquals(expectedNumber.toString(), resultsQueue.poll());
        }
        assertTrue(resultsQueue.isEmpty());
    }

    @Test
    public void testEmitFromQueue() throws Exception {
        List<TestSingle<Integer>> emittedSingles = new ArrayList<>();
        TestCollectingPublisherSubscriber<Integer> blockingSubscriber = new TestCollectingPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber.Builder<Integer>()
                .lastSubscriber(blockingSubscriber).build();
        toSource(source.flatMapMergeSingle(integer -> {
            TestSingle<Integer> s = new TestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2)).subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);
        source.onNext(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));

        TestSingle<Integer> single1 = emittedSingles.remove(0);
        TestSingle<Integer> single2 = emittedSingles.remove(0);

        executorService.execute(() -> {
            single1.onSuccess(2);
            single2.onSuccess(3);
        });

        Integer nextItem = blockingSubscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(2, nextItem.intValue());
        nextItem = blockingSubscriber.takeOnNext();
        assertNotNull(nextItem);
        assertEquals(3, nextItem.intValue());
        assertFalse(blockingSubscriber.pollTerminal(200, MILLISECONDS));

        source.onComplete();
        blockingSubscriber.awaitOnComplete();
    }

    @Test
    public void testRequestAndEmitConcurrency() throws Exception {
        int totalToRequest = 1000;
        Set<Integer> received = new LinkedHashSet<>(totalToRequest);
        toSource(source.flatMapMergeSingle(Single::succeeded, 2).beforeOnNext(received::add)).subscribe(subscriber);
        source.onSubscribe(subscription);
        CountDownLatch requestingStarting = new CountDownLatch(1);
        Future<?> submitFuture = executorService.submit(() -> {
            requestingStarting.countDown();
            for (int i = 0; i < totalToRequest; i++) {
                subscriber.request(1);
            }
        });
        // Just to make sure we have both threads running concurrently.
        requestingStarting.await();
        for (int i = 0; i < totalToRequest; i++) {
            subscription.awaitRequestN(i + 1);
            source.onNext(i);
        }
        submitFuture.get(); // Await everything requested.
        assertThat("Unexpected items emitted.", received, hasSize(totalToRequest));
        assertThat(received, containsInAnyOrder(range(0, totalToRequest).boxed().toArray()));
    }
}
