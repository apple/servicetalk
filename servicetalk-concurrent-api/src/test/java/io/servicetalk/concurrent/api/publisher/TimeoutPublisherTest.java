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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.TimeoutTestUtils.AbstractTestExecutor;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleEvent;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleQueueTestExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.stubbing.Answer;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class TimeoutPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<Integer> publisherRule = new PublisherRule<>();
    @Rule
    public final MockedSubscriberRule<Integer> subscriberRule = new MockedSubscriberRule<>();
    private final ScheduleQueueTestExecutor testExecutor = new ScheduleQueueTestExecutor();
    private java.util.concurrent.ExecutorService timerSimulator;

    @Before
    public void setup() {
        timerSimulator = java.util.concurrent.Executors.newFixedThreadPool(1);
    }

    @After
    public void teardown() {
        timerSimulator.shutdown();
    }

    @Test
    public void executorScheduleThrows() {
        subscriberRule.subscribe(publisherRule.getPublisher().timeout(1, NANOSECONDS, new AbstractTestExecutor() {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        }));

        subscriberRule.verifyFailure(DELIBERATE_EXCEPTION);
        publisherRule.verifyCancelled();
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.request(10);
        subscriberRule.verifyNoEmissions();
        publisherRule.complete();

        subscriberRule.verifySuccess();
        verify(event.cancellable).cancel();
    }

    @Test
    public void dataOnCompletionNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.request(10);
        subscriberRule.verifyNoEmissions();
        publisherRule.sendItems(1, 2, 3);
        subscriberRule.verifyItems(1, 2, 3);
        publisherRule.complete();

        subscriberRule.verifySuccess();
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.request(10);
        subscriberRule.verifyNoEmissions();
        publisherRule.fail();

        subscriberRule.verifyFailure(DELIBERATE_EXCEPTION);
        verify(event.cancellable).cancel();
    }

    @Test
    public void dataOnErrorNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.request(10);
        subscriberRule.verifyNoEmissions();
        publisherRule.sendItems(1, 2, 3);
        subscriberRule.verifyItems(1, 2, 3);
        publisherRule.fail();

        subscriberRule.verifyFailure(DELIBERATE_EXCEPTION);
        verify(event.cancellable).cancel();
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.getSubscription().cancel();
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataAndTimeout() throws ExecutionException, InterruptedException {
        ScheduleEvent event = initSubscriber();

        // Sleep for at least as much time as the expiration time, because we just subscribed.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        subscriberRule.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void dataAndTimeout() throws ExecutionException, InterruptedException {
        ScheduleEvent event = initSubscriber(2, MILLISECONDS);
        subscriberRule.request(10);
        subscriberRule.verifyNoEmissions();
        publisherRule.sendItems(1, 2, 3);
        subscriberRule.verifyItems(1, 2, 3);

        // Sleep for at least as much time as the expiration time, because we just delivered data.
        Thread.sleep(5);
        timerSimulator.submit(event.runnable).get();
        subscriberRule.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(2, MILLISECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void justSubscribeTimeout() throws ExecutionException, InterruptedException {
        DelayedOnSubscribePublisher<Integer> delayedPublisher = new DelayedOnSubscribePublisher<>();

        ScheduleEvent event = initSubscriber(1, NANOSECONDS, delayedPublisher, false);
        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        Subscription mockSubscription = mock(Subscription.class);
        Subscriber<? super Integer> subscriber = delayedPublisher.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockSubscription);
        verify(mockSubscription).cancel();
        subscriberRule.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void concurrentTimeoutInvocation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        subscriberRule.subscribe(publisherRule.getPublisher().timeout(10, MILLISECONDS, new AbstractTestExecutor() {
            private final AtomicInteger timerCount = new AtomicInteger();
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                int count = timerCount.incrementAndGet();
                if (count <= 2) {
                    if (count == 1) {
                        final Future<?> f = timerSimulator.submit(task);
                        try {
                            f.get();
                        } catch (Exception e) {
                            throwException(e);
                        }
                        latch.countDown();
                    } else {
                        timerSimulator.execute(() -> {
                            try {
                                // Sleep for at least enough time for the expiration time to fire before invoking the
                                // run() method.
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                throwException(e);
                            }
                            task.run();
                            latch.countDown();
                        });
                    }
                }
                return IGNORE_CANCEL;
            }
        }));

        doAnswer((Answer<Void>) invocationOnMock -> {
            // Just in case the timer fires earlier than expected (after the first timer) we countdown the latch so the
            // test won't fail.
            latch.countDown();
            return null;
        }).when(subscriberRule.getSubscriber()).onError(any());

        latch.await();
        subscriberRule.verifyFailure(TimeoutException.class);
    }

    private ScheduleEvent initSubscriber() {
        return initSubscriber(1, NANOSECONDS);
    }

    private ScheduleEvent initSubscriber(long timeout, TimeUnit unit) {
        return initSubscriber(timeout, unit, publisherRule.getPublisher(), true);
    }

    private ScheduleEvent initSubscriber(long timeout, TimeUnit unit, Publisher<Integer> publisher,
                                         boolean expectOnSubscribe) {
        subscriberRule.subscribe(publisher.timeout(timeout, unit, testExecutor), expectOnSubscribe);
        ScheduleEvent event = testExecutor.events.poll();
        assertNotNull(event);
        if (expectOnSubscribe) {
            subscriberRule.verifyNoEmissions();
        }
        return event;
    }

    private static final class DelayedOnSubscribePublisher<T> extends Publisher<T> {
        @Nullable
        volatile Subscriber<? super T> subscriber;
        @Override
        protected void handleSubscribe(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }
    }
}
