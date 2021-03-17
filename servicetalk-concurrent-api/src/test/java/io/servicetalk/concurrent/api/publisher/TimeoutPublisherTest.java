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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
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

@RunWith(Parameterized.class)
public class TimeoutPublisherTest {
    private static final Duration ONE_NANOSECOND = Duration.ofNanos(1);

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
        };

        abstract boolean restartAtOnNext();
    }

    @RegisterExtension
    public final ExecutorExtension<TestExecutor> executorExtension = ExecutorExtension.withTestExecutor();

    private final TestPublisher<Integer> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();
    private TestExecutor testExecutor;

    @BeforeEach
    public void setup() {
        testExecutor = executorExtension.executor();
    }

    @Test
    public void executorScheduleThrows() {
        toSource(publisher.timeout(1, NANOSECONDS, new DelegatingExecutor(testExecutor) {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(subscriber);
        publisher.onSubscribe(subscription);

        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertTrue(subscription.isCancelled());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void noDataOnCompletionNoTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
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
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void dataOnCompletionNoTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
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
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void noDataOnErrorNoTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
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
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void dataOnErrorNoTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
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
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void subscriptionCancelAlsoCancelsTimer(TimeoutPublisherTest.TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().cancel();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void noDataAndTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
        init(params);

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void dataAndTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
        init(params);

        subscriber.awaitSubscription().request(10);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        publisher.onNext(1, 2, 3);
        assertThat(subscriber.takeOnNext(3), contains(1, 2, 3));

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void justSubscribeTimeout(TimeoutPublisherTest.TimerBehaviorParam params) {
        DelayedOnSubscribePublisher<Integer> delayedPublisher = new DelayedOnSubscribePublisher<>();

        init(delayedPublisher, false, params.restartAtOnNext());

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

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(TimeoutPublisherTest.TimerBehaviorParam.class)
    public void concurrentTimeoutInvocation(TimeoutPublisherTest.TimerBehaviorParam params) throws InterruptedException {
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
                                // Sleep for at least enough time for the expiration time to fire before invoking
                                // the run() method.
                                Thread.sleep(100);
                                task.run();
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
        }).whenOnError(cause -> {
            // Just in case the timer fires earlier than expected (after the first timer) we countdown the latch so the
            // test won't fail.
            if (!(cause instanceof TimeoutException)) {
                causeRef.compareAndSet(null, cause);
            }
            countDownToZero(latch);
        })).subscribe(subscriber);

        latch.await();
        assertNull(causeRef.get());
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    private void init(TimeoutPublisherTest.TimerBehaviorParam params) {
        init(publisher, true, params.restartAtOnNext());
    }

    private void init(Publisher<Integer> publisher, boolean expectOnSubscribe, boolean untilTermination) {
        publisher = untilTermination ?
                publisher.withTimeout(ONE_NANOSECOND, testExecutor)
                : publisher.timeout(ONE_NANOSECOND, testExecutor);
        toSource(publisher).subscribe(subscriber);
        assertThat(testExecutor.scheduledTasksPending(), is(1));
        if (expectOnSubscribe) {
            subscriber.awaitSubscription();
        }
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
