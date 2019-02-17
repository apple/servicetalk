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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TimeoutTestUtils;
import io.servicetalk.concurrent.api.TimeoutTestUtils.AbstractTestExecutor;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class TimeoutCompletableTest {
    @Rule
    public final MockedCompletableListenerRule listener = new MockedCompletableListenerRule();

    private TestSingle<Integer> source;
    private final TimeoutTestUtils.ScheduleQueueTestExecutor testExecutor = new TimeoutTestUtils.ScheduleQueueTestExecutor();
    private java.util.concurrent.ExecutorService timerSimulator;

    @Before
    public void setUp() throws Exception {
        timerSimulator = java.util.concurrent.Executors.newFixedThreadPool(1);
        source = new TestSingle<>(false, false);
    }

    @After
    public void teardown() {
        timerSimulator.shutdown();
    }

    @Test
    public void executorScheduleThrows() {
        listener.listen(source.ignoreResult().timeout(1, NANOSECONDS, new AbstractTestExecutor() {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        }));

        listener.verifyFailure(DELIBERATE_EXCEPTION);
        source.verifyCancelled();
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        ScheduleEvent event = initSubscriber();

        listener.verifyNoEmissions();
        source.onSuccess(1);

        listener.verifyCompletion();
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        ScheduleEvent event = initSubscriber();

        listener.verifyNoEmissions();
        source.onError(DELIBERATE_EXCEPTION);

        listener.verifyFailure(DELIBERATE_EXCEPTION);
        verify(event.cancellable).cancel();
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        ScheduleEvent event = initSubscriber();

        listener.cancel();
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataAndTimeout() throws Exception {
        ScheduleEvent event = initSubscriber();

        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        listener.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void justSubscribeTimeout() throws Exception {
        DelayedOnSubscribeCompletable delayedCompletable = new DelayedOnSubscribeCompletable();

        ScheduleEvent event = initSubscriber(delayedCompletable, false);
        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        Cancellable mockCancellable = mock(Cancellable.class);
        CompletableSource.Subscriber subscriber = delayedCompletable.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockCancellable);
        verify(mockCancellable).cancel();
        listener.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    private ScheduleEvent initSubscriber() {
        return initSubscriber(source.ignoreResult(), true);
    }

    private ScheduleEvent initSubscriber(Completable completable, boolean expectOnSubscribe) {
        listener.listen(completable.timeout(1, NANOSECONDS, testExecutor), expectOnSubscribe);
        ScheduleEvent event = testExecutor.events.poll();
        assertNotNull(event);
        if (expectOnSubscribe) {
            listener.verifyNoEmissions();
        }
        return event;
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
