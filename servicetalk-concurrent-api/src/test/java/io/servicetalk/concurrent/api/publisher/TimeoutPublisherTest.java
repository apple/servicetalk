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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TimeoutPublisherTest {

    private enum TimerBehaviorParam {
        IDLE_TIMER { // timeout : idle
            @Override
            boolean restartAtOnNext() {
                return true;
            }
        },
        TERMINATION_TIMER {
            @Override // timeoutTerminal : termination
            boolean restartAtOnNext() {
                return false;
            }
        },
        DEMAND_TIMER {
            @Override
            boolean restartAtOnNext() {
                return false;
            }
        };

        abstract boolean restartAtOnNext();
    }

    @RegisterExtension
    static final ExecutorExtension<TestExecutor> executorExtension = ExecutorExtension.withTestExecutor();

    private final TestPublisher<Integer> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();
    private TestExecutor testExecutor;

    @BeforeEach
    void setup() {
        testExecutor = executorExtension.executor();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void timeoutExceptionDeliveredBeforeUpstreamException(TimerBehaviorParam params) {
        toSource(initNoSubscribe(new Publisher<Integer>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Integer> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    private boolean terminated;
                    @Override
                    public void request(final long n) {
                    }

                    @Override
                    public void cancel() {
                        if (!terminated) {
                            terminated = true;
                            subscriber.onError(new AssertionError("unexpected error, should have seen timeout"));
                        }
                    }
                });
            }
        }, params, ofNanos(1), testExecutor))
                .subscribe(subscriber);
        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void executorScheduleThrows(TimerBehaviorParam params) {
        toSource(initNoSubscribe(publisher, params, ofNanos(1), new DelegatingExecutor(testExecutor) {
                    @Override
                    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                        throw DELIBERATE_EXCEPTION;
                    }
                })
        ).subscribe(subscriber);
        publisher.onSubscribe(subscription);

        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertTrue(subscription.isCancelled());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void noDataOnCompletionNoTimeout(TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().request(10);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        publisher.onComplete();
        subscriber.awaitOnComplete();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void dataOnCompletionNoTimeout(TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().request(10);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        publisher.onNext(1, 2, 3);
        assertThat(subscriber.takeOnNext(3), contains(1, 2, 3));
        publisher.onComplete();
        subscriber.awaitOnComplete();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void noDataOnErrorNoTimeout(TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().request(10);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void dataOnErrorNoTimeout(TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().request(10);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        publisher.onNext(1, 2, 3);
        assertThat(subscriber.takeOnNext(3), contains(1, 2, 3));
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void subscriptionCancelAlsoCancelsTimer(TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().cancel();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void noDataAndTimeout(TimerBehaviorParam params) {
        init(params);

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void dataAndTimeout(TimerBehaviorParam params) {
        final long millisMultiplier = 100;
        final long numItems = 3;
        init(params, ofMillis(millisMultiplier + 1));

        assertThat(testExecutor.scheduledTasksPending(), is(1));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
        subscriber.awaitSubscription().request(numItems);
        if (params == TimerBehaviorParam.TERMINATION_TIMER) {
            testExecutor.advanceTimeBy(millisMultiplier, MILLISECONDS);
            publisher.onNext(0);
            assertThat(subscriber.takeOnNext(), is(0));
        } else {
            for (int x = 0; x < numItems; x++) {
                testExecutor.advanceTimeBy(millisMultiplier, MILLISECONDS);
                publisher.onNext(x);
                assertThat(subscriber.takeOnNext(), is(x));
            }
        }

        testExecutor.advanceTimeBy(millisMultiplier + 1, MILLISECONDS);

        // timer should have now fired.
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));
        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(params.restartAtOnNext() ? 3 : 1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimerBehaviorParam.class)
    void justSubscribeTimeout(TimerBehaviorParam params) {
        DelayedOnSubscribePublisher<Integer> delayedPublisher = new DelayedOnSubscribePublisher<>();

        init(delayedPublisher, params, ofNanos(1), false);

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));

        Subscription mockSubscription = mock(Subscription.class);
        Subscriber<? super Integer> subscriber = delayedPublisher.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockSubscription);
        verify(mockSubscription).cancel();
        assertThat(this.subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    @Test
    void concurrentTimeoutInvocation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();

        // In order to simulate concurrent execution, we introduce an Executor that does not respect the delay for the
        // first timer schedule. Internally, we expect the TimeoutPublisher to reschedule the timer. For that we use
        // TestExecutor, which will allow us to advance the time and trigger the actual timeout, which will simulate
        // concurrent execution.
        toSource(publisher.timeout(10, MILLISECONDS, new Executor() {
            private final AtomicInteger timerCount = new AtomicInteger();

            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                int count = timerCount.incrementAndGet();
                if (count <= 2) {
                    if (count == 1) {
                        try {
                            task.run();
                        } catch (Throwable cause) {
                            causeRef.compareAndSet(null, cause);
                            countDownToZero(latch);
                        }
                        latch.countDown();
                    } else {
                        try {
                            try {
                                testExecutor.schedule(task, delay, unit);
                                testExecutor.advanceTimeBy(delay, unit);
                            } catch (Throwable cause) {
                                causeRef.compareAndSet(null, cause);
                                countDownToZero(latch);
                            }
                            latch.countDown();
                        } catch (Throwable cause) {
                            causeRef.compareAndSet(null, cause);
                            countDownToZero(latch);
                        }
                    }
                }
                return IGNORE_CANCEL;
            }

            @Override
            public long currentTime(final TimeUnit unit) {
                return testExecutor.currentTime(unit);
            }

            @Override
            public Completable closeAsync() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Completable onClose() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Cancellable execute(final Runnable task) throws RejectedExecutionException {
                throw new UnsupportedOperationException();
            }
        })).subscribe(subscriber);

        latch.await();
        assertNull(causeRef.get());
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    @Test
    @RepeatedTest(100)
    void timeoutDemandTimerCancellation() {
        init(TimerBehaviorParam.DEMAND_TIMER, Duration.ofSeconds(1));
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);
        assertThat(testExecutor.scheduledTasksPending(), is(0));
        ForkJoinPool.commonPool().execute(() -> publisher.onNext(1));
        assertThat(subscriber.takeOnNext(), is(1));
        subscription.request(1);
        assertThat(testExecutor.scheduledTasksPending(), is(0));
    }

    private void init(TimerBehaviorParam params) {
        init(params, ofNanos(1));
    }

    private void init(TimerBehaviorParam params, Duration duration) {
        init(publisher, params, duration, true);
    }

    private void init(Publisher<Integer> publisher, TimerBehaviorParam params,
                      Duration duration, boolean expectOnSubscribe) {
        publisher = initNoSubscribe(publisher, params, duration, testExecutor);
        toSource(publisher).subscribe(subscriber);
        assertThat(testExecutor.scheduledTasksPending(), is(1));
        if (expectOnSubscribe) {
            subscriber.awaitSubscription();
        }
    }

    private Publisher<Integer> initNoSubscribe(Publisher<Integer> publisher, TimerBehaviorParam params,
                                               Duration duration, io.servicetalk.concurrent.Executor executor) {
        return params == TimerBehaviorParam.DEMAND_TIMER ? publisher.timeoutDemand(duration, executor) :
                params.restartAtOnNext() ? publisher.timeout(duration, executor) :
                        publisher.timeoutTerminal(duration, executor);
    }

   private static void countDownToZero(CountDownLatch latch) {
        while (latch.getCount() > 0) {
            latch.countDown(); // count down an extra time to complete the test early.
        }
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
