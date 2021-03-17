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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TimeoutSingleTest {
    @RegisterExtension
    public final ExecutorExtension<TestExecutor> executorExtension = ExecutorExtension.withTestExecutor();

    private LegacyTestSingle<Integer> source = new LegacyTestSingle<>(false, false);
    public final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
    private TestExecutor testExecutor;

    @BeforeEach
    public void setup() {
        testExecutor = executorExtension.executor();
    }

    @Test
    public void executorScheduleThrows() {
        toSource(source.timeout(1, NANOSECONDS, new DelegatingExecutor(testExecutor) {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(subscriber);

        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        TestCancellable cancellable = new TestCancellable();
        source.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        init();

        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        source.onSuccess(1);
        assertThat(subscriber.awaitOnSuccess(), is(1));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        init();

        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        init();

        subscriber.awaitSubscription().cancel();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void noDataAndTimeout() {
        init();

        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @Test
    public void justSubscribeTimeout() {
        DelayedOnSubscribeSingle<Integer> delayedSingle = new DelayedOnSubscribeSingle<>();

        init(delayedSingle, false);

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));

        Cancellable mockCancellable = mock(Cancellable.class);
        Subscriber<? super Integer> subscriber = delayedSingle.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockCancellable);
        verify(mockCancellable).cancel();
        assertThat(this.subscriber.awaitOnError(), instanceOf(TimeoutException.class));
    }

    private void init() {
        init(source, true);
    }

    private void init(final Single<Integer> source, final boolean expectOnSubscribe) {
        toSource(source.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        assertThat(testExecutor.scheduledTasksPending(), is(1));
        if (expectOnSubscribe) {
            subscriber.awaitSubscription();
        }
    }

    private static final class DelayedOnSubscribeSingle<T> extends Single<T> {
        @Nullable
        volatile Subscriber<? super T> subscriber;

        @Override
        protected void handleSubscribe(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }
    }
}
