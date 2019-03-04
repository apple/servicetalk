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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSingleSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TimeoutSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<TestExecutor> executorRule = ExecutorRule.withTestExecutor();

    private TestSingle<Integer> source = new TestSingle<>(false, false);
    public final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
    private final TestExecutor testExecutor = executorRule.executor();

    @Test
    public void executorScheduleThrows() {
        toSource(source.timeout(1, NANOSECONDS, new Executor() {
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

            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(subscriber);

        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        TestCancellable cancellable = new TestCancellable();
        source.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        toSource(source.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        assertTrue(subscriber.cancellableReceived());

        assertFalse(subscriber.hasResult());
        assertThat(subscriber.error(), nullValue());
        source.onSuccess(1);
        assertThat(subscriber.takeResult(), is(1));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        toSource(source.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        assertTrue(subscriber.cancellableReceived());

        assertFalse(subscriber.hasResult());
        assertThat(subscriber.error(), nullValue());
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), is(DELIBERATE_EXCEPTION));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        toSource(source.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        assertTrue(subscriber.cancellableReceived());

        subscriber.cancel();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void noDataAndTimeout() {
        toSource(source.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        assertTrue(subscriber.cancellableReceived());

        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(subscriber.takeError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @Test
    public void justSubscribeTimeout() {
        DelayedOnSubscribeSingle<Integer> delayedSingle = new DelayedOnSubscribeSingle<>();

        toSource(delayedSingle.timeout(1, NANOSECONDS, testExecutor)).subscribe(subscriber);
        testExecutor.advanceTimeBy(1, NANOSECONDS);

        Cancellable mockCancellable = mock(Cancellable.class);
        Subscriber<? super Integer> subscriber = delayedSingle.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockCancellable);
        verify(mockCancellable).cancel();
        assertThat(this.subscriber.takeError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
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
