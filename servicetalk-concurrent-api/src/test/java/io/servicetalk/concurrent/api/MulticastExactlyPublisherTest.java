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

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

@Timeout(60)
class MulticastExactlyPublisherTest {
    private TestPublisher<Integer> source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
    private TestSubscription subscription = new TestSubscription();

    @Test
    void emitItemsAndThenError() {
        Publisher<Integer> multicast = source.multicastToExactly(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        source.onSubscribe(subscription);

        subscriber1.awaitSubscription().request(2);
        subscriber2.awaitSubscription().request(2);
        assertThat(subscription.requested(), is((long) 2));
        source.onNext(1, 2);
        assertThat(subscriber1.takeOnNext(2), contains(1, 2));
        assertThat(subscriber2.takeOnNext(2), contains(1, 2));
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber1.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat(subscriber2.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void sourceSubscribeBefore() {
        source = new TestPublisher<>(); // With auto-on-subscribe enabled
        Publisher<Integer> multicast = source.multicastToExactly(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();

        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        subscriber1.awaitSubscription();
        source.onSubscribe(subscription);

        subscriber1.awaitSubscription().request(2);
        subscriber2.awaitSubscription().request(2);
        assertThat(subscription.requested(), is(2L));
        source.onNext(1, 2);
        assertThat(subscriber1.takeOnNext(2), contains(1, 2));
        assertThat(subscriber2.takeOnNext(2), contains(1, 2));
    }

    @Test
    void concurrentRequestN() throws InterruptedException {
        final int expectedSubscribers = 50;
        Publisher<Integer> multicast = source.multicastToExactly(expectedSubscribers, expectedSubscribers);
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
            subscribers[i].awaitSubscription();
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
            assertThat(throwableRef.get(), is(nullValue()));
            assertThat(subscription.requested(), is(1L));
            assertThat(subscription.isCancelled(), is(false));
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void reentryBothSubscriberRequestCountIsCorrect() {
        Publisher<Integer> multicast = source.multicastToExactly(2);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast.whenOnNext(n -> subscriber1.awaitSubscription().request(1))).subscribe(subscriber1);
        toSource(multicast.whenOnNext(n -> subscriber2.awaitSubscription().request(1))).subscribe(subscriber2);

        source.onSubscribe(subscription);

        subscriber1.awaitSubscription();
        subscriber2.awaitSubscription();

        subscriber1.awaitSubscription().request(1);
        subscriber2.awaitSubscription().request(1);
        assertThat(subscription.requested(), is((long) 1));
        source.onNext(1, 2, 3);
        assertThat(subscription.requested(), is((long) 4));
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, 3));
        assertThat(subscriber2.takeOnNext(3), contains(1, 2, 3));
    }

    @Test
    void requestLongMax() {
        final int maxQueueSize = 1000;
        Publisher<Integer> multicast = source.multicastToExactly(2, maxQueueSize);
        TestPublisherSubscriber<Integer> subscriber1 = new TestPublisherSubscriber<>();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);

        source.onSubscribe(subscription);

        subscriber1.awaitSubscription();
        subscriber2.awaitSubscription();

        subscriber1.awaitSubscription().request(Long.MAX_VALUE);
        subscriber2.awaitSubscription().request(3);
        assertThat(subscription.requested(), is(3L));
        source.onNext(1, 2, 3);
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, 3));
        assertThat(subscriber2.takeOnNext(), is(1));
    }

    @Test
    void longMaxForAllSubs() throws Exception {
        Publisher<Integer> original = range(1, 10);
        ArrayList<Integer> items = original.collect((Supplier<ArrayList<Integer>>) ArrayList::new, (list, integer) -> {
            list.add(integer);
            return list;
        }).toFuture().get();

        Publisher<Integer> multi = original.multicastToExactly(2, 5);
        List<Integer> first = new ArrayList<>();
        List<Integer> second = new ArrayList<>();
        multi.forEach(first::add);
        multi.forEach(second::add);

        assertThat(first, contains(items.toArray()));
        assertThat(second, equalTo(first));
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
                subscriber.awaitSubscription().request(finalI);
            } catch (Throwable cause) {
                throwableRef.set(cause);
            } finally {
                doneLatch.countDown();
            }
        };
    }
}
