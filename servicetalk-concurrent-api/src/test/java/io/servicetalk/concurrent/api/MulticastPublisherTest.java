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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MulticastPublisherTest {
    @Rule
    public final MockedSubscriberRule<Boolean> subscriber = new MockedSubscriberRule<>();

    private TestPublisher<Integer> source;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout(60, SECONDS, () ->
            System.out.println("sent: " + source.sent() + " requested: " + source.requested() + " outstanding: " +
                    source.outstandingRequested()));

    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<>(true);
    }

    @Test
    public void emitItemsAndThenError() {
        Publisher<Integer> multicast = source.multicast(2);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();
        subscriber1.subscribe(multicast);
        subscriber2.subscribe(multicast);

        source.sendOnSubscribe();

        subscriber1.request(2);
        subscriber2.request(2);
        source.verifyRequested(2);
        source.sendItems(1, 2);
        subscriber1.verifyItems(1, 2);
        subscriber2.verifyItems(1, 2);
        source.fail();
        subscriber1.verifyFailure(DELIBERATE_EXCEPTION);
        subscriber2.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void duplicateOnSubscribeIsInvalid() {
        MulticastPublisher<Integer> source = new MulticastPublisher<>(new Publisher<Integer>(immediate()) {
            @Override
            protected void handleSubscribe(Subscriber<? super Integer> subscriber) {
                // noop
            }
        }, 2, immediate());
        source.forEach(t -> {
            //ignore
        });
        source.forEach(t -> {
            //ignore
        });

        Subscription sub = mock(Subscription.class);
        source.onSubscribe(sub);
        Subscription dup = mock(Subscription.class);
        source.onSubscribe(dup);
        verify(dup).cancel();
        verify(sub, times(0)).cancel();
    }

    @Test
    public void sourceSubscribeAfter() {
        Publisher<Integer> multicast = source.multicast(2);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();
        subscriber1.subscribe(multicast, false);
        subscriber2.subscribe(multicast, false);

        source.sendOnSubscribe();

        subscriber1.verifySubscribe();
        subscriber2.verifySubscribe();

        subscriber1.request(2);
        source.verifyRequested(2);
        source.sendItems(1, 2);
        subscriber1.verifyItems(1, 2);
        subscriber2.verifyNoEmissions();
    }

    @Test
    public void sourceSubscribeBefore() {
        Publisher<Integer> multicast = source.multicast(2);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();

        source.sendOnSubscribe();

        subscriber1.subscribe(multicast, false);
        subscriber2.subscribe(multicast);
        subscriber1.verifySubscribe();

        subscriber1.request(2);
        subscriber2.request(2);
        source.verifyRequested(2);
        source.sendItems(1, 2);
        subscriber1.verifyItems(1, 2);
        subscriber2.verifyItems(1, 2);
    }

    @Test
    public void concurrentRequestN() throws InterruptedException {
        final int expectedSubscribers = 2000;
        Publisher<Integer> multicast = source.multicast(expectedSubscribers, expectedSubscribers);
        @SuppressWarnings("unchecked")
        MockedSubscriberRule<Integer>[] subscribers = (MockedSubscriberRule<Integer>[]) new MockedSubscriberRule[expectedSubscribers];

        source.sendOnSubscribe();

        final int expectedSubscribersMinus1 = expectedSubscribers - 1;
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i] = new MockedSubscriberRule<>();
            subscribers[i].subscribe(multicast, false);
        }
        subscribers[expectedSubscribersMinus1] = new MockedSubscriberRule<>();
        subscribers[expectedSubscribersMinus1].subscribe(multicast);
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i].verifySubscribe();
        }

        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS, new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(expectedSubscribers);
            CountDownLatch doneLatch = new CountDownLatch(expectedSubscribers);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            for (int i = 1; i <= expectedSubscribers; ++i) {
                executorService.execute(requestIRunnable(subscribers, i, barrier, throwableRef, doneLatch));
            }

            doneLatch.await();
            assertNull(throwableRef.get());
            source.verifyRequested(expectedSubscribers);
            source.verifyNotCancelled();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void concurrentRequestNAndOnNext() throws BrokenBarrierException, InterruptedException {
        final int expectedSubscribers = 400;
        Publisher<Integer> multicast = source.multicast(expectedSubscribers, expectedSubscribers);
        @SuppressWarnings("unchecked")
        MockedSubscriberRule<Integer>[] subscribers = (MockedSubscriberRule<Integer>[]) new MockedSubscriberRule[expectedSubscribers];

        source.sendOnSubscribe();

        final int expectedSubscribersMinus1 = expectedSubscribers - 1;
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i] = new MockedSubscriberRule<>();
            subscribers[i].subscribe(multicast, false);
        }
        subscribers[expectedSubscribersMinus1] = new MockedSubscriberRule<>();
        subscribers[expectedSubscribersMinus1].subscribe(multicast);
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i].verifySubscribe();
        }

        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS, new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(expectedSubscribers + 1);
            CountDownLatch doneLatch = new CountDownLatch(expectedSubscribers);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            for (int i = 1; i <= expectedSubscribers; ++i) {
                executorService.execute(requestIRunnable(subscribers, i, barrier, throwableRef, doneLatch));
            }

            barrier.await();

            for (int i = 0; i < expectedSubscribers; ++i) {
                while (source.outstandingRequested() <= 0) {
                    Thread.yield();
                }
                source.sendItems(i);
            }

            doneLatch.await();
            assertNull(throwableRef.get());
            Integer[] expectedItems = new Integer[expectedSubscribers];
            for (int x = 0; x < expectedItems.length; ++x) {
                expectedItems[x] = x;
            }
            for (int i = 0; i < expectedSubscribers; ++i) {
                subscribers[i].verifyItems(Mockito::verify, 0, i, expectedItems);
            }
            source.verifyRequested(expectedSubscribers);
            source.verifyNotCancelled();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void reentryFirstSubscriberRequestCountIsCorrect() {
        reentrySubscriberRequestCountIsCorrect(true);
    }

    @Test
    public void reentrySecondSubscriberRequestCountIsCorrect() {
        reentrySubscriberRequestCountIsCorrect(false);
    }

    @Test
    public void reentryBothSubscriberRequestCountIsCorrect() {
        Publisher<Integer> multicast = source.multicast(2);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();
        subscriber1.subscribe(multicast, false);
        subscriber2.subscribe(multicast, false);

        source.sendOnSubscribe();

        subscriber1.verifySubscribe();
        subscriber2.verifySubscribe();

        doAnswer((Answer<Void>) invocation -> {
            subscriber1.request(1);
            return null;
        }).when(subscriber1.subscriber()).onNext(anyInt());
        doAnswer((Answer<Void>) invocation -> {
            subscriber2.request(1);
            return null;
        }).when(subscriber2.subscriber()).onNext(anyInt());

        subscriber1.request(1);
        subscriber2.request(1);
        source.verifyRequested(1);
        source.sendItemsNoDemandCheck(1, 2, 3);
        source.verifyRequested(4);
        subscriber1.verifyItems(1, 2, 3);
        subscriber2.verifyItems(1, 2, 3);
    }

    private void reentrySubscriberRequestCountIsCorrect(boolean firstIsReentry) {
        Publisher<Integer> multicast = source.multicast(2);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();
        subscriber1.subscribe(multicast, false);
        subscriber2.subscribe(multicast, false);

        source.sendOnSubscribe();

        subscriber1.verifySubscribe();
        subscriber2.verifySubscribe();

        if (firstIsReentry) {
            doAnswer((Answer<Void>) invocation -> {
                subscriber1.request(1);
                return null;
            }).when(subscriber1.subscriber()).onNext(anyInt());

            subscriber1.request(2);
            subscriber2.request(1);
            source.verifyRequested(2);
            source.sendItemsNoDemandCheck(1, 2, 3);
            source.verifyRequested(5);
            subscriber1.verifyItems(1, 2, 3);
            subscriber2.verifyItems(1);
        } else {
            doAnswer((Answer<Void>) invocation -> {
                subscriber2.request(1);
                return null;
            }).when(subscriber2.subscriber()).onNext(anyInt());

            subscriber2.request(2);
            subscriber1.request(1);
            source.verifyRequested(2);
            source.sendItemsNoDemandCheck(1, 2, 3);
            source.verifyRequested(5);
            subscriber2.verifyItems(1, 2, 3);
            subscriber1.verifyItems(1);
        }
    }

    @Test
    public void reentryAndMultiQueueSupportsNull() {
        Publisher<Integer> multicast = source.multicast(2);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();
        subscriber1.subscribe(multicast, false);
        subscriber2.subscribe(multicast, false);

        source.sendOnSubscribe();

        subscriber1.verifySubscribe();
        subscriber2.verifySubscribe();

        AtomicBoolean onNextCalled = new AtomicBoolean();
        doAnswer((Answer<Void>) invocation -> {
            if (onNextCalled.compareAndSet(false, true)) {
                source.sendItems(null, 3);
            }
            return null;
        }).when(subscriber1.subscriber()).onNext(anyInt());

        subscriber1.request(3);
        subscriber2.request(1);
        source.verifyRequested(3);

        // Deliver an item, which will trigger a re-entry null delivery.
        source.sendItemsNoDemandCheck(1);
        source.verifyRequested(3);
        subscriber1.verifyItems(1, null, 3);
        subscriber2.verifyItems(1);

        // We now test that the queue can handle null items.
        subscriber2.request(2);
        subscriber2.verifyItems(new Integer[]{null, 3});
    }

    @Test
    public void requestLongMax() {
        final int maxQueueSize = 1000;
        Publisher<Integer> multicast = source.multicast(2, maxQueueSize);
        MockedSubscriberRule<Integer> subscriber1 = new MockedSubscriberRule<>();
        MockedSubscriberRule<Integer> subscriber2 = new MockedSubscriberRule<>();
        subscriber1.subscribe(multicast, false);
        subscriber2.subscribe(multicast, false);

        source.sendOnSubscribe();

        subscriber1.verifySubscribe();
        subscriber2.verifySubscribe();

        subscriber1.request(Long.MAX_VALUE);
        subscriber2.request(1);
        source.verifyRequested(maxQueueSize);
        source.sendItemsNoDemandCheck(1, 2, 3);
        source.verifyRequested(maxQueueSize);
        subscriber1.verifyItems(1, 2, 3);
        subscriber2.verifyItems(1);
    }

    private static Runnable requestIRunnable(MockedSubscriberRule<Integer>[] subscribers,
                                             int finalI,
                                             CyclicBarrier barrier,
                                             AtomicReference<Throwable> throwableRef,
                                             CountDownLatch doneLatch) {
        return () -> {
            try {
                MockedSubscriberRule<Integer> subscriber = subscribers[finalI - 1];
                barrier.await();
                subscriber.request(finalI);
            } catch (Throwable cause) {
                throwableRef.set(cause);
            } finally {
                doneLatch.countDown();
            }
        };
    }
}
