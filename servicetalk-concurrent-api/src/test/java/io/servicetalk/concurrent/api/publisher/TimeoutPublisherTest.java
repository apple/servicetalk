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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.api.TimeoutTestUtils.AbstractTestExecutor;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleEvent;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleQueueTestExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertNull;

public class TimeoutPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisher<Integer> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<Integer> subscriber = newTestPublisherSubscriber();
    private final ScheduleQueueTestExecutor testExecutor = new ScheduleQueueTestExecutor();
    private java.util.concurrent.ExecutorService timerSimulator;
    private TestSubscription subscription = new TestSubscription();

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
        toSource(publisher.timeout(1, NANOSECONDS, new AbstractTestExecutor() {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(subscriber);
        publisher.onSubscribe(subscription);

        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriber.request(10);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
        publisher.onComplete();

        assertTrue(subscriber.isCompleted());
        verify(event.cancellable).cancel();
    }

    @Test
    public void dataOnCompletionNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriber.request(10);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
        publisher.onNext(1, 2, 3);
        assertThat(subscriber.items(), contains(1, 2, 3));
        publisher.onComplete();

        assertTrue(subscriber.isCompleted());
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriber.request(10);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
        publisher.onError(DELIBERATE_EXCEPTION);

        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        verify(event.cancellable).cancel();
    }

    @Test
    public void dataOnErrorNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriber.request(10);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
        publisher.onNext(1, 2, 3);
        assertThat(subscriber.items(), contains(1, 2, 3));
        publisher.onError(DELIBERATE_EXCEPTION);

        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        verify(event.cancellable).cancel();
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        ScheduleEvent event = initSubscriber();

        subscriber.cancel();
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataAndTimeout() throws Exception {
        ScheduleEvent event = initSubscriber();

        // Sleep for at least as much time as the expiration time, because we just subscribed.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        assertThat(subscriber.error(), instanceOf(TimeoutException.class));
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void dataAndTimeout() throws Exception {
        ScheduleEvent event = initSubscriber(2, MILLISECONDS);
        subscriber.request(10);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
        publisher.onNext(1, 2, 3);
        assertThat(subscriber.items(), contains(1, 2, 3));

        // Sleep for at least as much time as the expiration time, because we just delivered data.
        Thread.sleep(5);
        timerSimulator.submit(event.runnable).get();
        assertThat(subscriber.error(), instanceOf(TimeoutException.class));
        assertTrue(event.delayEquals(2, MILLISECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void justSubscribeTimeout() throws Exception {
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
        assertThat(this.subscriber.error(), instanceOf(TimeoutException.class));
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void concurrentTimeoutInvocation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();

        // The timeout operator doesn't expose a way to control the underlying time source and always uses
        // System.nanoTime(). This was intentional to avoid expanding public API surface when the majority of the time
        // System.nanoTime() is the correct choice. However that makes testing a bit more challenging here and we resort
        // to sleep/approximations.
        // 10 ms -> long enough for the first timeout runnable to first without timing out, so that the second time out
        // runnable will fire and result in a timeout. This doesn't always work so we just fallback and drain the
        // CountDownLatch if not.
        // Sleep for at least enough time for the expiration time to fire before invoking
        // the run() method.
        // Just in case the timer fires earlier than expected (after the first timer) we countdown the latch so the
        // test won't fail.
        toSource(publisher.timeout(10, MILLISECONDS, new AbstractTestExecutor() {
            private final AtomicInteger timerCount = new AtomicInteger();
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                int count = timerCount.incrementAndGet();
                if (count <= 2) {
                    if (count == 1) {
                        try {
                            timerSimulator.submit(task).get();
                        } catch (Throwable cause) {
                            causeRef.compareAndSet(null, cause);
                            countDownToZero(latch);
                        }
                        latch.countDown();
                    } else {
                        try {
                            timerSimulator.execute(() -> {
                                try {
                                    // Sleep for at least enough time for the expiration time to fire before invoking
                                    // the run() method.
                                    Thread.sleep(100);
                                    task.run();
                                } catch (Throwable cause) {
                                    causeRef.compareAndSet(null, cause);
                                    countDownToZero(latch);
                                }
                                latch.countDown();
                            });
                        } catch (Throwable cause) {
                            causeRef.compareAndSet(null, cause);
                            countDownToZero(latch);
                        }
                    }
                }
                return IGNORE_CANCEL;
            }
        }).doOnError(cause -> {
            // Just in case the timer fires earlier than expected (after the first timer) we countdown the latch so the
            // test won't fail.
            if (!(cause instanceof TimeoutException)) {
                causeRef.compareAndSet(null, cause);
            }
            countDownToZero(latch);
        })).subscribe(subscriber);

        latch.await();
        assertNull(causeRef.get());
        assertThat(subscriber.error(), instanceOf(TimeoutException.class));
    }

    private static void countDownToZero(CountDownLatch latch) {
        while (latch.getCount() > 0) {
            latch.countDown(); // count down an extra time to complete the test early.
        }
    }

    private ScheduleEvent initSubscriber() {
        return initSubscriber(1, NANOSECONDS);
    }

    private ScheduleEvent initSubscriber(long timeout, TimeUnit unit) {
        return initSubscriber(timeout, unit, publisher, true);
    }

    private ScheduleEvent initSubscriber(long timeout, TimeUnit unit, Publisher<Integer> publisher,
                                         boolean expectOnSubscribe) {
        toSource(publisher.timeout(timeout, unit, testExecutor)).subscribe(subscriber);
        if (expectOnSubscribe) {
            assertTrue(subscriber.subscriptionReceived());
        }
        ScheduleEvent event = testExecutor.events.poll();
        assertNotNull(event);
        if (expectOnSubscribe) {
            assertTrue(subscriber.subscriptionReceived());
            assertThat(subscriber.items(), hasSize(0));
            assertFalse(subscriber.isTerminated());
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
