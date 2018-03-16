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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

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

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PublisherFlatmapSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout(30, SECONDS);
    @Rule
    public final MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();

    private TestPublisher<Integer> source;
    private static ExecutorService executor;

    @BeforeClass
    public static void beforeClass() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<Integer>().sendOnSubscribe();
    }

    @Test
    public void testSingleItemSyncSingle() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), 2))
                .request(1);
        source.sendItems(1).onComplete();
        subscriber.verifySuccess(2);
    }

    @Test
    public void testSingleItemCompletesWithNull() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(null), 2))
                .request(1);
        source.sendItems(1).onComplete();
        subscriber.verifyItems(new Integer[] {null}).verifySuccess();
    }

    @Test
    public void testSingleItemSourceCompleteFirst() throws Exception {
        TestSingle<Integer> single = new TestSingle<>();
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2))
                .request(1);
        source.sendItems(1).onComplete();
        single.onSuccess(2);
        subscriber.verifySuccess(2);
    }

    @Test
    public void testSingleItemSingleCompleteFirst() throws Exception {
        TestSingle<Integer> single = new TestSingle<>();
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2))
                .request(1);
        source.sendItems(1);
        single.onSuccess(2);
        source.onComplete();
        subscriber.verifySuccess(2);
    }

    @Test
    public void testSingleItemSingleError() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.error(DELIBERATE_EXCEPTION), 2))
                .request(1);
        source.sendItems(1);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSingleErrorPostSourceComplete() throws Exception {
        TestSingle<Integer> single = new TestSingle<>();
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2)).request(1);
        source.sendItems(1).onComplete();
        single.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSourceEmitsErrorNoOnNexts() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), 2))
                .request(1);
        source.fail();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSourceEmitsErrorPostOnNexts() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), 2))
                .request(1);
        source.sendItems(1).fail();
        subscriber.verifyItems(2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSourceEmitsErrorPostOnNextsSingleNotCompleted() throws Exception {
        TestSingle<Integer> single = new TestSingle<>();
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2))
                .request(1);
        source.sendItems(1).fail();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        single.verifyCancelled();
    }

    @Test
    public void testSubscriberCancel() throws Exception {
        TestSingle<Integer> single = new TestSingle<>();
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2))
                .request(1);
        source.sendItems(1);
        subscriber.cancel();
        single.verifyCancelled();
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testSingleCompletePostCancel() throws Exception {
        TestSingle<Integer> single = new TestSingle<>(true);
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2))
                .request(1);
        source.sendItems(1);
        subscriber.cancel();
        single.verifyCancelled();
        subscriber.verifyNoEmissions();
        single.onSuccess(4);
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testSingleErrorPostCancel() throws Exception {
        TestSingle<Integer> single = new TestSingle<>(true);
        subscriber.subscribe(source.flatmapSingle(integer -> single, 2))
                .request(1);
        source.sendItems(1);
        subscriber.cancel();
        single.verifyCancelled();
        subscriber.verifyNoEmissions();
        single.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testMaxConcurrency() throws Exception {
        List<TestSingle<Integer>> emittedSingles = new ArrayList<>();
        subscriber.subscribe(source.flatmapSingle(integer -> {
            TestSingle<Integer> s = new TestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2)).request(3);
        source.verifyRequested(2); // Should not request more than max concurrency.

        source.sendItems(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));
        emittedSingles.remove(0).onSuccess(2);
        subscriber.verifyItems(2);

        source.verifyRequested(3); // Total requested must equal actual requested.

        emittedSingles.remove(0).onSuccess(3);
        subscriber.verifyItems(3);
        source.sendItems(1).onComplete();
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(1));
        emittedSingles.remove(0).onSuccess(4);
        subscriber.verifySuccessNoRequestN(4);

        source.verifyRequested(3); // Total requested must equal actual requested.
    }

    @Test
    public void testMapperThrows() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> {
            throw DELIBERATE_EXCEPTION;
        }, 2)).request(1);

        try {
            source.sendItems(1);
            fail();
        } catch (Throwable cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);

            // Now simulate failing the publisher by emit onError(...)
            source.fail(cause);
        }
        source.verifyNotCancelled();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testNoFlowControl() throws Exception {
        List<TestSingle<Integer>> emittedSingles = new ArrayList<>();
        subscriber.subscribe(source.flatmapSingle(integer -> {
            TestSingle<Integer> s = new TestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2)).request(Long.MAX_VALUE);
        source.verifyRequested(2); // Should not request more than max concurrency.

        source.sendItems(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));
        emittedSingles.remove(0).onSuccess(2);
        subscriber.verifyItems(2);

        source.verifyRequested(3); // Request enough on completion to reach max concurrency.

        emittedSingles.remove(0).onSuccess(3);
        source.verifyRequested(4); // Request enough on completion to reach max concurrency.
        subscriber.verifyItems(3);

        source.sendItems(1).onComplete();
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(1));
        emittedSingles.remove(0).onSuccess(4);
        subscriber.verifySuccessNoRequestN(4);

        source.verifyRequested(6); // Request enough on completion to reach max concurrency.
    }

    @Test
    public void testRequestPostSingleError() throws Exception {
        subscriber.subscribe(source.flatmapSingleDelayError(integer -> Single.error(DELIBERATE_EXCEPTION), 2))
                .request(3);
        source.verifyRequested(2).sendItems(1); // Request no more than max concurrency.
        subscriber.verifyNoEmissions();
        source.verifyRequested(3).sendItems(1); // Request more with 1 single completion.
        source.verifyRequested(3).onComplete(); // Stop requesting more.
        subscriber.verifySuppressedFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testRequestMultipleTimes() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), 10))
                .request(2);
        source.verifyRequested(2).sendItems(1, 1);
        subscriber.verifyItems(sub -> verify(sub, times(2)), 2);
        subscriber.request(2);
        source.sendItems(1, 1);
        subscriber.verifyItems(sub -> verify(sub, times(4)), 2);
    }

    @Test
    public void testRequestMultipleTimesBreachMaxConcurrency() throws Exception {
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), 2))
                .request(2).request(2);
        source.verifyRequested(2);
        source.sendItems(1, 1).sendItems(1, 1).onComplete();
        subscriber.verifyItems(sub -> verify(sub, times(4)), 2)
                .verifySuccess();
    }

    @Test
    public void testMultipleSingleErrors() throws Exception {
        List<DeliberateException> errors = new ArrayList<>();
        subscriber.subscribe(source.flatmapSingleDelayError(integer -> {
            DeliberateException de = new DeliberateException();
            errors.add(de);
            return Single.error(de);
        }, 2))
                .request(3);
        source.sendItems(1, 1).onComplete();
        assertThat("Unexpected emitted error count.", errors, hasSize(2));
        DeliberateException first = errors.remove(0);
        for (DeliberateException error : errors) {
            subscriber.verifySuppressedFailure(first, error);
        }
    }

    @Test
    public void testRequestLongMaxValue() throws Exception {
        int maxConcurrency = 2;
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), maxConcurrency))
                .request(Long.MAX_VALUE);
        source.verifyRequested(maxConcurrency).sendItems(2).verifyOutstanding(maxConcurrency);
        subscriber.request(Long.MAX_VALUE);
        source.verifyOutstanding(maxConcurrency);
        source.verifyRequested(maxConcurrency + 1);
    }

    @Test
    public void testAccumulateToLongMaxValue() throws Exception {
        int maxConcurrency = 2;
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), maxConcurrency))
                .request(Long.MAX_VALUE - 1);
        source.verifyRequested(maxConcurrency);
        subscriber.request(2);
        source.verifyRequested(maxConcurrency);
        source.verifyOutstanding(maxConcurrency);
        source.sendItems(1, 2);
        source.verifyRequested(maxConcurrency + 2);
        source.verifyOutstanding(maxConcurrency);
    }

    @Test
    public void testAccumulateToIntMaxValue() throws Exception {
        int maxConcurrency = 2;
        subscriber.subscribe(source.flatmapSingle(integer -> Single.success(2), maxConcurrency))
                .request(Integer.MAX_VALUE - 1);
        source.verifyRequested(maxConcurrency);
        subscriber.request(2);
        source.verifyRequested(maxConcurrency);
        source.verifyOutstanding(maxConcurrency);
        source.sendItems(1, 2);
        source.verifyRequested(maxConcurrency + 2);
        source.verifyOutstanding(maxConcurrency);
    }

    @Test
    public void testReentry() throws InterruptedException {
        Queue<String> resultsQueue = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final Integer[] expectedNumbers = new Integer[1000000]; // big enough to trigger stack overflow if we are not careful.
        for (int i = 0; i < expectedNumbers.length; ++i) {
            expectedNumbers[i] = i;
        }
        PublisherFlatmapSingle<Integer, String> pub = new PublisherFlatmapSingle<>(Publisher.from(expectedNumbers),
                                                                                   value -> Single.success(Integer.toString(value)),
                                                                                   1, false);
        pub.subscribe(new Subscriber<String>() {
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
        BlockingSubscriber<Integer> subscriber = new BlockingSubscriber<>();
        source.flatmapSingle(integer -> {
            TestSingle<Integer> s = new TestSingle<>();
            emittedSingles.add(s);
            return s;
        }, 2).subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);
        source.sendItems(1, 1);
        assertThat("Unexpected number of Singles emitted.", emittedSingles, hasSize(2));

        TestSingle<Integer> single1 = emittedSingles.remove(0);
        TestSingle<Integer> single2 = emittedSingles.remove(0);
        executor.execute(() -> {
            single1.onSuccess(2);
            single2.onSuccess(3);
        });

        subscriber.awaitAndVerifyAwaitingItem(2); // Second item would only be sent once the first thread is free.
        subscriber.unblock(2);
        subscriber.verifyReceived(2);

        subscriber.awaitAndVerifyAwaitingItem(3);
        subscriber.unblock(3);
        subscriber.verifyReceived(2, 3);
    }

    @Test
    public void testRequestAndEmitConcurrency() throws Exception {
        int totalToRequest = 100000;
        Set<Integer> received = new LinkedHashSet<>(totalToRequest);
        subscriber.subscribe(source.flatmapSingle(Single::success, 2).doBeforeNext(received::add));
        CountDownLatch requestingStarting = new CountDownLatch(1);
        Future<?> submit = executor.submit(() -> {
            requestingStarting.countDown();
            for (int i = 0; i < totalToRequest; i++) {
                subscriber.request(1);
            }
        });
        // Just to make sure we have both threads running concurrently.
        requestingStarting.await();
        for (int i = 1; i <= totalToRequest; i++) {
            //noinspection StatementWithEmptyBody
            while (source.getOutstandingRequested() <= 0) {
                // Don't send if we emit faster than request.
            }
            source.sendItems(i);
        }
        submit.get(); // Await everything requested.
        assertThat("Unexpected items emitted.", received, hasSize(totalToRequest));
        List<Integer> last = received.stream().skip(totalToRequest - 1).collect(toList());
        assertThat("Unexpected number of items in last.", last, hasSize(1));
        assertThat("Unexpected order of items received: " + received, last.get(0), equalTo(totalToRequest));
    }
}
