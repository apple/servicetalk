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
package io.servicetalk.apple.capacity.limiter.api;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class FixedCapacityLimiterTest {
    private final FixedCapacityLimiter provider = new FixedCapacityLimiter(1);

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
        assertThat(ticket.ignored(), equalTo(1));

        evaluateExpectingAllow();
    }

    @Test
    void aboveCapacityThenPendingComplete() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        evaluateExpectingReject();
        assertThat(ticket.completed(), equalTo(1));
        evaluateExpectingAllow();
    }

    @Test
    void aboveCapacityThenPendingFailed() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        evaluateExpectingReject();
        assertThat(ticket.failed(DELIBERATE_EXCEPTION), equalTo(1));
        evaluateExpectingAllow();
    }

    @Test
    void aboveCapacityThenPendingCancelled() {
        final CapacityLimiter.Ticket ticket = evaluateExpectingAllow();
        evaluateExpectingReject();
        assertThat(ticket.ignored(), equalTo(1));
        evaluateExpectingAllow();
    }

    private CapacityLimiter.Ticket evaluateExpectingAllow() {
        final CapacityLimiter.Ticket ticket = provider.tryAcquire(() -> 100, null);
        assertThat("Unexpected result, expected allow.", ticket, notNullValue());
        return ticket;
    }

    private void evaluateExpectingReject() {
        final CapacityLimiter.Ticket ticket = provider.tryAcquire(() -> 100, null);
        assertThat("Unexpected result, expected reject.", ticket, nullValue());
    }
}
