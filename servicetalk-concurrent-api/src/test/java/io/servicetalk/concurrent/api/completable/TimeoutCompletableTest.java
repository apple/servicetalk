/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TimeoutCompletableTest {
    @RegisterExtension
    static final ExecutorExtension<TestExecutor> executorExtension = ExecutorExtension.withTestExecutor();
    private final TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
    private final TestCompletable source = new TestCompletable();
    private TestExecutor testExecutor;

    @BeforeEach
    void setup() {
        testExecutor = executorExtension.executor();
    }

    @Test
    void timeoutExceptionDeliveredBeforeUpstreamException() {
        toSource(new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                subscriber.onSubscribe(new Cancellable() {
                    private boolean terminated;
                    @Override
                    public void cancel() {
                        if (!terminated) {
                            terminated = true;
                            subscriber.onError(new AssertionError("unexpected error, should have seen timeout"));
                        }
                    }
                });
            }
        }.timeout(ofNanos(1), testExecutor))
                .subscribe(subscriber);
        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    @Test
    void executorScheduleThrows() {
        toSource(source.timeout(1, NANOSECONDS, new DelegatingExecutor(testExecutor) {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(subscriber);

        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        TestCancellable cancellable = new TestCancellable();
        source.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
    }

    @Test
    void noDataOnCompletionNoTimeout() {
        init();

        source.onComplete();
        subscriber.awaitOnComplete();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    void noDataOnErrorNoTimeout() {
        init();

        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    void subscriptionCancelAlsoCancelsTimer() {
        init();

        subscriber.awaitSubscription().cancel();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    void noDataAndTimeout() {
        init();

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @Test
    void justSubscribeTimeout() {
        DelayedOnSubscribeCompletable delayedCompletable = new DelayedOnSubscribeCompletable();

        init(delayedCompletable, false);

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));

        Cancellable mockCancellable = mock(Cancellable.class);
        CompletableSource.Subscriber subscriber = delayedCompletable.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockCancellable);
        verify(mockCancellable).cancel();
        assertThat(this.subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    @Test
    @Disabled("Requires the notion of globally mocked time source using a TestExecutor")
    void defaultExecutorSubscribeTimeout() {
        DelayedOnSubscribeCompletable delayedCompletable = new DelayedOnSubscribeCompletable();
        // Assume the timeout call is not under the user's control, e.g. comes from a library of Completable providers
        final Completable operationThatInternallyTimesOut = delayedCompletable.timeout(Duration.ofDays(1));

        TestExecutor testExecutor = new TestExecutor();
        // TODO(dariusz): Replace all executors created with the test instance
        // Executors.setFactory(AllExecutorFactory.create(() -> testExecutor));

        toSource(operationThatInternallyTimesOut).subscribe(subscriber);
        testExecutor.advanceTimeBy(1, DAYS);

        CompletableSource.Subscriber subscriber = delayedCompletable.subscriber;
        assertNotNull(subscriber);
        assertThat(this.subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    @Test
    void cancelDoesOnError() throws Exception {
        DelayedOnSubscribeCompletable delayedCompletable = new DelayedOnSubscribeCompletable();
        init(delayedCompletable, false);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CompletableSource.Subscriber subscriber = delayedCompletable.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(() -> {
            subscriber.onError(DELIBERATE_EXCEPTION);
            cancelLatch.countDown();
        });
        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
        cancelLatch.await();
        Throwable error = this.subscriber.awaitOnError();
        assertThat(error, instanceOf(TimeoutException.class));
    }

    private void init() {
        init(source, true);
    }

    private void init(Completable source, boolean expectOnSubscribe) {
        toSource(source.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        assertThat(testExecutor.scheduledTasksPending(), is(1));
        if (expectOnSubscribe) {
            assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        }
    }

    private static final class DelayedOnSubscribeCompletable extends Completable {
        @Nullable
        volatile Subscriber subscriber;

        @Override
        protected void handleSubscribe(final Subscriber subscriber) {
            this.subscriber = subscriber;
        }
    }
}
