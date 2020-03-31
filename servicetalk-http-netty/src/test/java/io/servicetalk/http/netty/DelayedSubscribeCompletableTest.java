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

import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.TestCompletable;

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

public class DelayedSubscribeCompletableTest {
    @Test
    public void singleSubscriberDelayed() {
        Subscriber subscriber = mock(Subscriber.class);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
        toSource(delayedCompletable).subscribe(subscriber);
        assertFalse(completable.isSubscribed());
        processSubscribers(delayedCompletable);
        assertTrue(completable.isSubscribed());
        verifySubscriber(subscriber);
    }

    @Test
    public void singleSubscriberPassThrough() {
        Subscriber subscriber = mock(Subscriber.class);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
        processSubscribers(delayedCompletable);
        toSource(delayedCompletable).subscribe(subscriber);
        assertTrue(completable.isSubscribed());
        verifySubscriber(subscriber);
    }

    @Test
    public void singleSubscriberMultiThreaded() throws Exception {
        Subscriber subscriber = mock(Subscriber.class);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            Future<?> f = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                toSource(delayedCompletable).subscribe(subscriber);
            });

            barrier.await(); // wait until the other thread is ready to call subscribe

            processSubscribers(delayedCompletable);

            f.get(); // wait until after the subscribe is done

            assertTrue(completable.isSubscribed());
            verifySubscriber(subscriber);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void multiSubscriberDelayed() {
        Subscriber subscriber1 = mock(Subscriber.class);
        Subscriber subscriber2 = mock(Subscriber.class);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
        toSource(delayedCompletable).subscribe(subscriber1);
        toSource(delayedCompletable).subscribe(subscriber2);
        assertFalse(completable.isSubscribed());
        processSubscribers(delayedCompletable);
        assertTrue(completable.isSubscribed());
        verifySubscriber(subscriber1);
        verifySubscriber(subscriber2);
    }

    @Test
    public void multiSubscriberPassThrough() {
        Subscriber subscriber1 = mock(Subscriber.class);
        Subscriber subscriber2 = mock(Subscriber.class);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
        processSubscribers(delayedCompletable);
        toSource(delayedCompletable).subscribe(subscriber1);
        toSource(delayedCompletable).subscribe(subscriber2);
        assertTrue(completable.isSubscribed());
        verifySubscriber(subscriber1);
        verifySubscriber(subscriber2);
    }

    @Test
    public void multiSubscriberMixed() {
        Subscriber subscriber1 = mock(Subscriber.class);
        Subscriber subscriber2 = mock(Subscriber.class);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
        toSource(delayedCompletable).subscribe(subscriber1);
        assertFalse(completable.isSubscribed());
        processSubscribers(delayedCompletable);
        toSource(delayedCompletable).subscribe(subscriber2);
        assertTrue(completable.isSubscribed());
        verifySubscriber(subscriber1);
        verifySubscriber(subscriber2);
    }

    @Test
    public void multiSubscriberMultiThreaded() throws Exception {
        final int numberSubscribers = 10;
        Subscriber[] subscribers = (Subscriber[]) Array.newInstance(Subscriber.class, 10);
        for (int i = 0; i < subscribers.length; ++i) {
            subscribers[i] = mock(Subscriber.class);
        }
        List<Future<?>> futures = new ArrayList<>(subscribers.length);
        TestCompletable completable = new TestCompletable();
        DelayedSubscribeCompletable delayedCompletable = new DelayedSubscribeCompletable(toSource(completable));
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
                    toSource(delayedCompletable).subscribe(subscribers[finalI]);
                }));
            }

            barrier.await(); // wait until the other thread is ready to call subscribe

            processSubscribers(delayedCompletable);

            int i = 0;
            for (Future<?> future : futures) {
                future.get(); // wait until after the subscribe is done
                verifySubscriber(subscribers[i++]);
            }

            assertTrue(completable.isSubscribed());
        } finally {
            executorService.shutdown();
        }
    }

    private static void processSubscribers(DelayedSubscribeCompletable delayedCompletable) {
        delayedCompletable.processSubscribers();
    }

    private static void verifySubscriber(Subscriber subscriber) {
        verify(subscriber).onSubscribe(any());
    }
}
