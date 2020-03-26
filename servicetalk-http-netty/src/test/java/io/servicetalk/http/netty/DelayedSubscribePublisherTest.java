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
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DelayedSubscribePublisherTest {
    @Test
    public void singleSubscriberDelayed() {
        singleSubscriberDelayed(false);
    }

    @Test
    public void singleFailSubscriberDelayed() {
        singleSubscriberDelayed(true);
    }

    private static void singleSubscriberDelayed(boolean fail) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        toSource(delayedPublisher).subscribe(subscriber);
        assertFalse(publisher.isSubscribed());
        processSubscribers(delayedPublisher, fail);
        assertThat(publisher.isSubscribed(), not(equalTo(fail)));
        verifySubscriber(subscriber, fail);
    }

    @Test
    public void singleSubscriberPassThrough() {
        singleSubscriberPassThrough(false);
    }

    @Test
    public void singleFailSubscriberPassThrough() {
        singleSubscriberPassThrough(true);
    }

    private static void singleSubscriberPassThrough(boolean fail) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        processSubscribers(delayedPublisher, fail);
        toSource(delayedPublisher).subscribe(subscriber);
        assertThat(publisher.isSubscribed(), not(equalTo(fail)));
        verifySubscriber(subscriber, fail);
    }

    @Test
    public void singleSubscriberMultiThreaded() throws Exception {
        singleSubscriberMultiThreaded(false);
    }

    @Test
    public void singleFailSubscriberMultiThreaded() throws Exception {
        singleSubscriberMultiThreaded(true);
    }

    private static void singleSubscriberMultiThreaded(boolean fail) throws Exception {
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

            processSubscribers(delayedPublisher, fail);

            f.get(); // wait until after the subscribe is done

            assertThat(publisher.isSubscribed(), not(equalTo(fail)));
            verifySubscriber(subscriber, fail);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void multiSubscriberDelayed() {
        multiSubscriberDelayed(false);
    }

    @Test
    public void multiFailSubscriberDelayed() {
        multiSubscriberDelayed(true);
    }

    private static void multiSubscriberDelayed(boolean fail) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber2 = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        toSource(delayedPublisher).subscribe(subscriber1);
        toSource(delayedPublisher).subscribe(subscriber2);
        assertFalse(publisher.isSubscribed());
        processSubscribers(delayedPublisher, fail);
        assertThat(publisher.isSubscribed(), not(equalTo(fail)));
        verifySubscriber(subscriber1, fail);
        verifySubscriber(subscriber2, fail);
    }

    @Test
    public void multiSubscriberPassThrough() {
        multiSubscriberPassThrough(false);
    }

    @Test
    public void multiFailSubscriberPassThrough() {
        multiSubscriberPassThrough(true);
    }

    private static void multiSubscriberPassThrough(boolean fail) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber2 = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        processSubscribers(delayedPublisher, fail);
        toSource(delayedPublisher).subscribe(subscriber1);
        toSource(delayedPublisher).subscribe(subscriber2);
        assertThat(publisher.isSubscribed(), not(equalTo(fail)));
        verifySubscriber(subscriber1, fail);
        verifySubscriber(subscriber2, fail);
    }

    @Test
    public void multiSubscriberMixed() {
        multiSubscriberMixed(false);
    }

    @Test
    public void multiFailSubscriberMixed() {
        multiSubscriberMixed(true);
    }

    private static void multiSubscriberMixed(boolean fail) {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber1 = mock(Subscriber.class);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> subscriber2 = mock(Subscriber.class);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        DelayedSubscribePublisher<Integer> delayedPublisher = new DelayedSubscribePublisher<>(toSource(publisher));
        toSource(delayedPublisher).subscribe(subscriber1);
        assertFalse(publisher.isSubscribed());
        processSubscribers(delayedPublisher, fail);
        toSource(delayedPublisher).subscribe(subscriber2);
        assertThat(publisher.isSubscribed(), not(equalTo(fail)));
        verifySubscriber(subscriber1, fail);
        verifySubscriber(subscriber2, fail);
    }

    @Test
    public void multiSubscriberMultiThreaded() throws Exception {
        multiSubscriberMultiThreaded(false);
    }

    @Test
    public void multiFailSubscriberMultiThreaded() throws Exception {
        multiSubscriberMultiThreaded(true);
    }

    private static void multiSubscriberMultiThreaded(boolean fail) throws Exception {
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

            processSubscribers(delayedPublisher, fail);

            int i = 0;
            for (Future<?> future : futures) {
                future.get(); // wait until after the subscribe is done
                verifySubscriber(subscribers[i++], fail);
            }

            assertThat(publisher.isSubscribed(), not(equalTo(fail)));
        } finally {
            executorService.shutdown();
        }
    }

    private static void processSubscribers(DelayedSubscribePublisher<Integer> delayedPublisher, boolean fail) {
        if (fail) {
            delayedPublisher.failSubscribers(DELIBERATE_EXCEPTION);
        } else {
            delayedPublisher.processSubscribers();
        }
    }

    private static void verifySubscriber(Subscriber<Integer> subscriber, boolean fail) {
        verify(subscriber).onSubscribe(any());
        if (fail) {
            verify(subscriber).onError(eq(DELIBERATE_EXCEPTION));
        }
    }
}
