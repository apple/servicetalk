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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MulticastPublisherTest {

    private TestPublisher<Integer> source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
    private TestSubscription subscription = new TestSubscription();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout(60, SECONDS);

    @Test
    public void emitItemsAndThenError() {
        Publisher<Integer> multicast = source.multicast(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        source.onSubscribe(subscription);

        subscriber1.request(2);
        subscriber2.request(2);
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1, 2);
        assertThat(subscriber1.takeItems(), contains(1, 2));
        assertThat(subscriber2.takeItems(), contains(1, 2));
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber1.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat(subscriber2.takeError(), sameInstance(DELIBERATE_EXCEPTION));
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
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        source.onSubscribe(subscription);

        assertTrue(subscriber1.subscriptionReceived());
        assertTrue(subscriber2.subscriptionReceived());

        subscriber1.request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 2);
        assertThat(subscriber1.takeItems(), contains(1, 2));
        assertTrue(subscriber2.subscriptionReceived());
        assertThat(subscriber2.takeItems(), hasSize(0));
        assertThat(subscriber2.takeTerminal(), nullValue());
    }

    @Test
    public void sourceSubscribeBefore() {
        source = new TestPublisher<>(); // With auto-on-subscribe enabled
        Publisher<Integer> multicast = source.multicast(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();

        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        assertTrue(subscriber1.subscriptionReceived());
        source.onSubscribe(subscription);

        subscriber1.request(2);
        subscriber2.request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 2);
        assertThat(subscriber1.takeItems(), contains(1, 2));
        assertThat(subscriber2.takeItems(), contains(1, 2));
    }

    @Test
    public void concurrentRequestN() throws InterruptedException {
        final int expectedSubscribers = 2000;
        Publisher<Integer> multicast = source.multicast(expectedSubscribers, expectedSubscribers);
        @SuppressWarnings("unchecked")
        TestPublisherSubscriber<Integer>[] subscribers = (TestPublisherSubscriber<Integer>[])
                new TestPublisherSubscriber[expectedSubscribers];

        final int expectedSubscribersMinus1 = expectedSubscribers - 1;
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i] = new TestPublisherSubscriber<>();
            toSource(multicast).subscribe(subscribers[i]);
        }
        subscribers[expectedSubscribersMinus1] = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscribers[expectedSubscribersMinus1]);
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            assertTrue(subscribers[i].subscriptionReceived());
        }

        source.onSubscribe(subscription);

        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS,
                new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(expectedSubscribers);
            CountDownLatch doneLatch = new CountDownLatch(expectedSubscribers);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            for (int i = 1; i <= expectedSubscribers; ++i) {
                executorService.execute(requestIRunnable(subscribers, i, barrier, throwableRef, doneLatch));
            }

            doneLatch.await();
            assertNull(throwableRef.get());
            assertThat(subscription.requested(), is((long) expectedSubscribers));
            assertFalse(subscription.isCancelled());
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void concurrentRequestNAndOnNext() throws BrokenBarrierException, InterruptedException {
        final int expectedSubscribers = 400;
        Publisher<Integer> multicast = source.multicast(expectedSubscribers, expectedSubscribers);
        @SuppressWarnings("unchecked")
        TestPublisherSubscriber<Integer>[] subscribers = (TestPublisherSubscriber<Integer>[])
                new TestPublisherSubscriber[expectedSubscribers];

        final int expectedSubscribersMinus1 = expectedSubscribers - 1;
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            subscribers[i] = new TestPublisherSubscriber<>();
            toSource(multicast).subscribe(subscribers[i]);
        }
        subscribers[expectedSubscribersMinus1] = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscribers[expectedSubscribersMinus1]);
        for (int i = 0; i < expectedSubscribersMinus1; ++i) {
            assertTrue(subscribers[i].subscriptionReceived());
        }

        source.onSubscribe(subscription);

        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS,
                new SynchronousQueue<>());
        try {
            CyclicBarrier barrier = new CyclicBarrier(expectedSubscribers + 1);
            CountDownLatch doneLatch = new CountDownLatch(expectedSubscribers);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            for (int i = 1; i <= expectedSubscribers; ++i) {
                executorService.execute(requestIRunnable(subscribers, i, barrier, throwableRef, doneLatch));
            }

            barrier.await();

            for (int i = 0; i < expectedSubscribers; ++i) {
                while (subscription.requested() - i <= 0) {
                    Thread.yield();
                }
                source.onNext(i);
            }

            doneLatch.await();
            assertNull(throwableRef.get());
            List<Integer> expectedItems = new ArrayList<>(expectedSubscribers);
            for (int x = 0; x < expectedSubscribers; ++x) {
                expectedItems.add(x);
            }
            for (int i = 0; i < expectedSubscribers; ++i) {
                final Integer[] expectedSubset = expectedItems.subList(0, i).toArray(new Integer[0]);
                List<Integer> actual = subscribers[i].takeItems().subList(0, i);
                if (expectedSubset.length == 0) {
                    assertTrue(actual.isEmpty());
                } else {
                    assertThat(actual, contains(expectedSubset));
                }
            }
            assertThat(subscription.requested(), is((long) expectedSubscribers));
            assertFalse(subscription.isCancelled());
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
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast.doOnNext(n -> {
            subscriber1.request(1);
        })).subscribe(subscriber1);
        toSource(multicast.doOnNext(n -> {
            subscriber2.request(1);
        })).subscribe(subscriber2);

        source.onSubscribe(subscription);

        assertTrue(subscriber1.subscriptionReceived());
        assertTrue(subscriber2.subscriptionReceived());

        subscriber1.request(1);
        subscriber2.request(1);
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1, 2, 3);
        assertThat(subscription.requested(), is((long) 4));
        assertThat(subscriber1.takeItems(), contains(1, 2, 3));
        assertThat(subscriber2.takeItems(), contains(1, 2, 3));
    }

    private void reentrySubscriberRequestCountIsCorrect(boolean firstIsReentry) {
        Publisher<Integer> multicast = source.multicast(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast.doOnNext(n -> {
            if (firstIsReentry) {
                subscriber1.request(1);
            }
        })).subscribe(subscriber1);
        toSource(multicast.doOnNext(n -> {
            if (!firstIsReentry) {
                subscriber2.request(1);
            }
        })).subscribe(subscriber2);

        source.onSubscribe(subscription);

        assertTrue(subscriber1.subscriptionReceived());
        assertTrue(subscriber2.subscriptionReceived());

        if (firstIsReentry) {
            subscriber1.request(2);
            subscriber2.request(1);
            assertThat(subscription.requested(), is((long) 2));
            source.onNext(1, 2, 3);
            assertThat(subscription.requested(), is((long) 5));
            assertThat(subscriber1.takeItems(), contains(1, 2, 3));
            assertThat(subscriber2.takeItems(), contains(1));
        } else {
            subscriber2.request(2);
            subscriber1.request(1);
            assertThat(subscription.requested(), is((long) 2));
            source.onNext(1, 2, 3);
            assertThat(subscription.requested(), is((long) 5));
            assertThat(subscriber2.takeItems(), contains(1, 2, 3));
            assertThat(subscriber1.takeItems(), contains(1));
        }
    }

    @Test
    public void reentryAndMultiQueueSupportsNull() {
        Publisher<Integer> multicast = source.multicast(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        AtomicBoolean onNextCalled = new AtomicBoolean();
        toSource(multicast.doOnNext(n -> {
            if (onNextCalled.compareAndSet(false, true)) {
                source.onNext(null, 3);
            }
        })).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        source.onSubscribe(subscription);

        assertTrue(subscriber1.subscriptionReceived());
        assertTrue(subscriber2.subscriptionReceived());

        subscriber1.request(3);
        subscriber2.request(1);
        assertThat(subscription.requested(), is((long) 3));

        // Deliver an item, which will trigger a re-entry null delivery.
        source.onNext(1);
        assertThat(subscription.requested(), is((long) 3));
        assertThat(subscriber1.takeItems(), contains(1, null, 3));
        assertThat(subscriber2.takeItems(), contains(1));

        // We now test that the queue can handle null items.
        subscriber2.request(2);
        assertThat(subscriber2.takeItems(), contains(null, 3));
    }

    @Test
    public void requestLongMax() {
        final int maxQueueSize = 1000;
        Publisher<Integer> multicast = source.multicast(2, maxQueueSize);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        source.onSubscribe(subscription);

        assertTrue(subscriber1.subscriptionReceived());
        assertTrue(subscriber2.subscriptionReceived());

        subscriber1.request(Long.MAX_VALUE);
        subscriber2.request(1);
        assertThat(subscription.requested(), is((long) maxQueueSize));
        source.onNext(1, 2, 3);
        assertThat(subscription.requested(), is((long) maxQueueSize));
        assertThat(subscriber1.takeItems(), contains(1, 2, 3));
        assertThat(subscriber2.takeItems(), contains(1));
    }

    private static Runnable requestIRunnable(TestPublisherSubscriber<Integer>[] subscribers,
                                             int finalI,
                                             CyclicBarrier barrier,
                                             AtomicReference<Throwable> throwableRef,
                                             CountDownLatch doneLatch) {
        return () -> {
            try {
                TestPublisherSubscriber<Integer> subscriber = subscribers[finalI - 1];
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
