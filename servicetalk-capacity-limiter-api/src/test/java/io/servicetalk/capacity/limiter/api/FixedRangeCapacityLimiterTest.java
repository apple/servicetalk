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

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class FixedRangeCapacityLimiterTest {
    private final FixedCapacityLimiter provider = new FixedCapacityLimiter(2);

    @Test
    void belowCapacityThenComplete() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        ticket.completed();

        evaluateExpectingAllow();
    }

    @Test
    void belowCapacityThenFailed() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        ticket.failed(DELIBERATE_EXCEPTION);

        evaluateExpectingAllow();
    }

    @Test
    void belowCapacityThenCancelled() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        ticket.ignored();

        evaluateExpectingAllow();
    }

    @Test
    void aboveCapacityThenPendingComplete() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        final CapacityLimiter.Ticket critTicket = evaluateExpectingSoftReject();

        evaluateExpectingReject();
        ticket.completed();
        critTicket.completed();
        evaluateExpectingAllow();
    }

    @Test
    void aboveCapacityThenPendingFailed() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        final CapacityLimiter.Ticket critReject = evaluateExpectingSoftReject();

        evaluateExpectingReject();
        ticket.failed(DELIBERATE_EXCEPTION);
        critReject.failed(DELIBERATE_EXCEPTION);
        evaluateExpectingAllow();
    }

    @Test
    void aboveCapacityThenPendingCancelled() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        final CapacityLimiter.Ticket prioTicket = evaluateExpectingSoftReject();

        evaluateExpectingReject();
        ticket.failed(DELIBERATE_EXCEPTION);
        prioTicket.failed(DELIBERATE_EXCEPTION);
        evaluateExpectingAllow();
    }

    private CapacityLimiter.Ticket evaluateExpectingAllow() {
        final CapacityLimiter.Ticket ticket = provider.tryAcquire(() -> 50, null);
        assertThat("Unexpected result, expected ticket.", ticket, notNullValue());
        return ticket;
    }

    private CapacityLimiter.Ticket evaluateExpectingSoftReject() {
        final CapacityLimiter.Ticket ticket = provider.tryAcquire(() -> 50, null);
        assertThat("Unexpected result, expected a ticket.", ticket, nullValue());
        final CapacityLimiter.Ticket critTicket = provider.tryAcquire(() -> 100, null);
        assertThat("Unexpected result, expected a ticket.", critTicket, notNullValue());
        return critTicket;
    }

    private void evaluateExpectingReject() {
        final CapacityLimiter.Ticket critTicket = provider.tryAcquire(() -> 100, null);
        assertThat("Unexpected result, expected null.", critTicket, nullValue());
    }
}
