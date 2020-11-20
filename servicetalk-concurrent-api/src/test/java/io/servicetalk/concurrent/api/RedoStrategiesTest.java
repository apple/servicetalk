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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedoStrategiesTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    protected LinkedBlockingQueue<LegacyTestCompletable> timers;
    protected Executor timerExecutor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        timers = new LinkedBlockingQueue<>();
        timerExecutor = mock(Executor.class);
        when(timerExecutor.timer(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            LegacyTestCompletable completable = new LegacyTestCompletable();
            timers.add(completable);
            return completable;
        });
    }

    protected void verifyDelayWithDeltaJitter(long delayNanos, long jitterNanos, int invocationCount) {
        ArgumentCaptor<Long> backoffWithJitter = ArgumentCaptor.forClass(Long.class);
        verify(timerExecutor, times(invocationCount)).timer(backoffWithJitter.capture(), eq(NANOSECONDS));
        assertThat("Unexpected backoff value.", backoffWithJitter.getValue(), greaterThanOrEqualTo(0L));
        assertThat("Unexpected backoff value.", backoffWithJitter.getValue(),
                lessThanOrEqualTo(delayNanos + jitterNanos));
    }

    protected void verifyDelayWithFullJitter(long delayNanos, int invocationCount) {
        ArgumentCaptor<Long> backoffWithJitter = ArgumentCaptor.forClass(Long.class);
        verify(timerExecutor, times(invocationCount)).timer(backoffWithJitter.capture(), eq(NANOSECONDS));
        assertThat("Unexpected backoff value.", backoffWithJitter.getValue(), greaterThanOrEqualTo(0L));
        assertThat("Unexpected backoff value.", backoffWithJitter.getValue(), lessThanOrEqualTo(delayNanos));
    }
}
