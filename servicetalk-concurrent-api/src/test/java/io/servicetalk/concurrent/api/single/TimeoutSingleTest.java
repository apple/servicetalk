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
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TimeoutTestUtils.AbstractTestExecutor;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleEvent;
import io.servicetalk.concurrent.api.TimeoutTestUtils.ScheduleQueueTestExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ExecutionException;
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

public class TimeoutSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final MockedSingleListenerRule<Integer> subscriberRule = new MockedSingleListenerRule<>();
    private TestSingle<Integer> source;
    private final ScheduleQueueTestExecutor testExecutor = new ScheduleQueueTestExecutor();
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
        subscriberRule.listen(source.timeout(1, NANOSECONDS, new AbstractTestExecutor() {
            @Override
            public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
                throw DELIBERATE_EXCEPTION;
            }
        }));

        subscriberRule.verifyFailure(DELIBERATE_EXCEPTION);
        source.verifyCancelled();
    }

    @Test
    public void noDataOnCompletionNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.verifyNoEmissions();
        source.onSuccess(1);

        subscriberRule.verifySuccess(1);
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataOnErrorNoTimeout() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.verifyNoEmissions();
        source.onError(DELIBERATE_EXCEPTION);

        subscriberRule.verifyFailure(DELIBERATE_EXCEPTION);
        verify(event.cancellable).cancel();
    }

    @Test
    public void subscriptionCancelAlsoCancelsTimer() {
        ScheduleEvent event = initSubscriber();

        subscriberRule.cancel();
        verify(event.cancellable).cancel();
    }

    @Test
    public void noDataAndTimeout() throws ExecutionException, InterruptedException {
        ScheduleEvent event = initSubscriber();

        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        subscriberRule.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    @Test
    public void justSubscribeTimeout() throws ExecutionException, InterruptedException {
        DelayedOnSubscribeSingle<Integer> delayedSingle = new DelayedOnSubscribeSingle<>();

        ScheduleEvent event = initSubscriber(delayedSingle, false);
        // Sleep for at least as much time as the expiration time, because we just subscribed data.
        Thread.sleep(1);
        timerSimulator.submit(event.runnable).get();
        Cancellable mockCancellable = mock(Cancellable.class);
        Subscriber<? super Integer> subscriber = delayedSingle.subscriber;
        assertNotNull(subscriber);
        subscriber.onSubscribe(mockCancellable);
        verify(mockCancellable).cancel();
        subscriberRule.verifyFailure(TimeoutException.class);
        assertTrue(event.delayEquals(1, NANOSECONDS));
        verify(event.cancellable, never()).cancel();
        assertTrue(testExecutor.events.isEmpty());
    }

    private ScheduleEvent initSubscriber() {
        return initSubscriber(source, true);
    }

    private ScheduleEvent initSubscriber(Single<Integer> single, boolean expectOnSubscribe) {
        subscriberRule.listen(single.timeout(1, NANOSECONDS, testExecutor));
        ScheduleEvent event = testExecutor.events.poll();
        assertNotNull(event);
        if (expectOnSubscribe) {
            subscriberRule.verifyNoEmissions();
        }
        return event;
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
