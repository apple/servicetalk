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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.TestPublisher;

import org.junit.Test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DelayedSubscribePublisherTest {
    @Test
    public void singleSubscriberDelayed() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        toSource(delayedPublisher).subscribe(subscriber);
        assertFalse(publisher.isSubscribed());
        processSubscribers(delayedPublisher);
        assertTrue(publisher.isSubscribed());
        verifySubscriber(subscriber);
    }

    @Test
    public void singleSubscriberPassThrough() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        processSubscribers(delayedPublisher);
        toSource(delayedPublisher).subscribe(subscriber);
        assertTrue(publisher.isSubscribed());
        verifySubscriber(subscriber);
    }

    @Test
    public void singleSubscriberMultiThreaded() throws Exception {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            Future<?> f = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                toSource(delayedPublisher).subscribe(subscriber);
            });

            barrier.await(); // wait until the other thread is ready to call subscribe

            processSubscribers(delayedPublisher);

            f.get(); // wait until after the subscribe is done

            assertTrue(publisher.isSubscribed());
            verifySubscriber(subscriber);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void multiSubscriberDelayed() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber2 = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        toSource(delayedPublisher).subscribe(subscriber1);
        toSource(delayedPublisher).subscribe(subscriber2);
        assertFalse(publisher.isSubscribed());
        processSubscribers(delayedPublisher);
        assertTrue(publisher.isSubscribed());
        verifySubscriber(subscriber1);
        verifySubscriber(subscriber2);
    }

    @Test
    public void multiSubscriberPassThrough() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber2 = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        processSubscribers(delayedPublisher);
        toSource(delayedPublisher).subscribe(subscriber1);
        toSource(delayedPublisher).subscribe(subscriber2);
        assertTrue(publisher.isSubscribed());
        verifySubscriber(subscriber1);
        verifySubscriber(subscriber2);
    }

    @Test
    public void multiSubscriberMixed() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber2 = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        toSource(delayedPublisher).subscribe(subscriber1);
        assertFalse(publisher.isSubscribed());
        processSubscribers(delayedPublisher);
        toSource(delayedPublisher).subscribe(subscriber2);
        assertTrue(publisher.isSubscribed());
        verifySubscriber(subscriber1);
        verifySubscriber(subscriber2);
    }

    @Test
    public void multiSubscriberMultiThreaded() throws Exception {
        final int numberSubscribers = 10;
        @SuppressWarnings("unchecked")
        Subscriber<Integer>[] subscribers = (Subscriber<Integer>[]) Array.newInstance(Subscriber.class, 10);
        for (int i = 0; i < subscribers.length; ++i) {
            @SuppressWarnings("unchecked")
            Subscriber<Integer> subscriber = (Subscriber<Integer>) mock(Subscriber.class);
            subscribers[i] = subscriber;
        }
        List<Future<?>> futures = new ArrayList<>(subscribers.length);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        CyclicBarrier barrier = new CyclicBarrier(numberSubscribers + 1);
        ExecutorService executorService = Executors.newFixedThreadPool(numberSubscribers);
        try {
            for (int i = 0; i < subscribers.length; ++i) {
                final int finalI = i;
                futures.add(executorService.submit(() -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    toSource(delayedPublisher).subscribe(subscribers[finalI]);
                }));
            }

            barrier.await(); // wait until the other thread is ready to call subscribe

            processSubscribers(delayedPublisher);

            int i = 0;
            for (Future<?> future : futures) {
                future.get(); // wait until after the subscribe is done
                verifySubscriber(subscribers[i++]);
            }

            assertTrue(publisher.isSubscribed());
        } finally {
            executorService.shutdown();
        }
    }

    private static void processSubscribers(DelayedSubscribePublisher<Integer> delayedPublisher) {
        delayedPublisher.processSubscribers();
    }

    private static void verifySubscriber(Subscriber<Integer> subscriber) {
        verify(subscriber).onSubscribe(any());
    }
}
