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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TimeoutCompletableTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule<TestExecutor> executorRule = ExecutorRule.withTestExecutor();
    private final TestCompletableSubscriber listener = new TestCompletableSubscriber();
    private final TestSingle<Integer> source = new TestSingle<>();
    private TestExecutor testExecutor;

    @Before
    public void setup() {
        testExecutor = executorRule.executor();
    }

    @Test
    public void executorScheduleThrows() {
        toSource(source.ignoreElement().idleTimeout(1, NANOSECONDS, new DelegatingExecutor(testExecutor) {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(listener);

        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
        TestCancellable cancellable = new TestCancellable();
        source.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        init();

        source.onSuccess(1);
        listener.awaitOnComplete();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        init();

        source.onError(DELIBERATE_EXCEPTION);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        init();

        listener.awaitSubscription().cancel();

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(0));
    }

    @Test
    public void noDataAndTimeout() {
        init();

        testExecutor.advanceTimeBy(1, NANOSECONDS);
        assertThat(listener.awaitOnError(), instanceOf(TimeoutException.class));

        assertThat(testExecutor.scheduledTasksPending(), is(0));
        assertThat(testExecutor.scheduledTasksExecuted(), is(1));
    }

    @Test
    public void justSubscribeTimeout() {
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
        assertThat(listener.awaitOnError(), instanceOf(TimeoutException.class));
    }

    private void init() {
        init(source.ignoreElement(), true);
    }

    private void init(Completable source, boolean expectOnSubscribe) {
        toSource(source.idleTimeout(1, NANOSECONDS, testExecutor)).subscribe(listener);
        assertThat(testExecutor.scheduledTasksPending(), is(1));
        if (expectOnSubscribe) {
            assertThat(listener.pollTerminal(10, MILLISECONDS), is(nullValue()));
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
