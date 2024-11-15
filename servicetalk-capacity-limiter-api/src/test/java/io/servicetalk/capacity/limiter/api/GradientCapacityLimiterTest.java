/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.capacity.limiter.api;

import io.servicetalk.capacity.limiter.api.GradientCapacityLimiterBuilder.Observer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_INITIAL_LIMIT;
import static io.servicetalk.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_MIN_LIMIT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class GradientCapacityLimiterTest {

    private static final Exception SAD_EXCEPTION = new Exception("sad");

    private static final Classification DEFAULT = () -> 0;

    private final Observer observer = mock(Observer.class);
    @Nullable
    private CapacityLimiter capacityLimiter;
    @Nullable
    private LongSupplier timeSource;
    private volatile long currentTime;

    @BeforeEach
    void setup() {
        if (timeSource == null) {
            timeSource = () -> currentTime;
        }
        capacityLimiter = new GradientCapacityLimiterBuilder()
                .timeSource(timeSource)
                .limitUpdateInterval(Duration.ofMillis(50))
                .observer(observer)
                .build();
    }

    @Test
    void canAcquireTicket() {
        CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
        assertThat(ticket, notNullValue());
        verify(observer).onActiveRequestsIncr();

        // release it and make sure we observed the release.
        ticket.completed();
        verify(observer).onActiveRequestsDecr();
        verifyNoMoreInteractions(observer);
    }

    @Test
    void capacityCanDepleteToTheMinLimit() {
        for (;;) {
            CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
            currentTime += Duration.ofMillis(10).toNanos();
            int capacity = ticket.failed(SAD_EXCEPTION);
            if (capacity == DEFAULT_MIN_LIMIT) {
                break;
            }
        }
    }

    @Test
    void canRejectTicketAcquisitions() {
        CapacityLimiter.Ticket lastTicket = null;
        for (int i = 0; i < DEFAULT_INITIAL_LIMIT; i++) {
            // abandon all the tickets up to the limit
            CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
            assertThat(ticket, notNullValue());
            lastTicket = ticket;
        }
        CapacityLimiter.Ticket lastGoodTicket = lastTicket;
        lastTicket = capacityLimiter.tryAcquire(DEFAULT, null);
        assertThat(lastTicket, nullValue());

        // complete all the tickets.
        currentTime += Duration.ofMillis(10).toNanos();
        lastGoodTicket.completed();

        // We should be able to acquire a ticket again.
        lastTicket = capacityLimiter.tryAcquire(DEFAULT, null);
        assertThat(lastTicket, notNullValue());
    }

    @Test
    void observesLimitChanges() {
        CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
        currentTime += Duration.ofMillis(10).toNanos();
        ticket.failed(SAD_EXCEPTION);

        // ticket failure will not use gradient
        verify(observer).onLimitChange(eq(-1.0), eq(-1.0), eq(-1.0), eq(100.0), eq(50.0));

        ticket = capacityLimiter.tryAcquire(DEFAULT, null);
        currentTime += Duration.ofMillis(50).toNanos();
        // Ticket success should adjust observation upward.
        ticket.completed();
        verify(observer).onLimitChange(not(eq(-1.0)), not(eq(-1.0)), not(eq(-1.0)), anyDouble(), anyDouble());
    }
}
