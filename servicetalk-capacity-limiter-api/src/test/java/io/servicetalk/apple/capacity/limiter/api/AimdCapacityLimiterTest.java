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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class AimdCapacityLimiterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AimdCapacityLimiterTest.class);
    private static final Classification DEFAULT = () -> 0;

    @Test
    void testOnLimitAdaptiveness() {
        CapacityLimiter capacityLimiter = CapacityLimiters.dynamicAIMD()
                .limits(2, 2, 4)
                .increment(1f)
                .cooldown(Duration.ZERO)
                .backoffRatio(.8f, .2f)
                .stateObserver((limit, consumed) -> LOGGER.debug("Limit: {} consumed: {}", limit, consumed))
                .build();

        increaseLimitAndVerify(capacityLimiter);

        // Fail one, Pending 0 - New Limit = 4 * .2 => 0.8 => max(2, 0.8) => 2
        final CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
        assertThat(ticket, notNullValue());
        ticket.dropped();

        // Verify new limit
        for (int i = 0; i < 2; i++) {
            capacityLimiter.tryAcquire(DEFAULT, null);
        }

        // Now blocked
        assertThat(capacityLimiter.tryAcquire(DEFAULT, null), nullValue());
    }

    @Test
    void testOnDropAdaptiveness() {
        CapacityLimiter capacityLimiter = CapacityLimiters.dynamicAIMD()
                .limits(2, 2, 4)
                .increment(1f)
                .cooldown(Duration.ZERO)
                .backoffRatio(.9f, .2f)
                .build();

        increaseLimitAndVerify(capacityLimiter);

        // Fail one, Pending 0 - New Limit = 4 * .9 => 3.6 => max(2, 3.6) => 3
        final CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
        assertThat(ticket, notNullValue());
        ticket.dropped();

        // Verify new limit
        for (int i = 0; i < 3; i++) {
            capacityLimiter.tryAcquire(DEFAULT, null);
        }

        // Now blocked
        assertThat(capacityLimiter.tryAcquire(DEFAULT, null), nullValue());
    }

    @Test
    void concurrentEvaluation() throws InterruptedException {
        final CapacityLimiter capacityLimiter = CapacityLimiters.dynamicAIMD()
                .limits(70, 70, 120)
                .build();

        final ExecutorService executor = Executors.newFixedThreadPool(5);
        final AtomicLong succeeded = new AtomicLong(0);
        final AtomicLong failed = new AtomicLong(0);

        for (int i = 0; i < 120; i++) {
            executor.submit(() -> {
                final CapacityLimiter.Ticket ticket = capacityLimiter.tryAcquire(DEFAULT, null);
                if (ticket != null) {
                    ticket.completed();
                    succeeded.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Executor didn't terminate in time.");
        }

        assertThat(succeeded.get(), equalTo(120L));
    }

    private static void increaseLimitAndVerify(final CapacityLimiter capacityLimiter) {
        CapacityLimiter.Ticket first = capacityLimiter.tryAcquire(DEFAULT, null);
        // Pending 1
        assertThat(first, notNullValue());

        CapacityLimiter.Ticket second = capacityLimiter.tryAcquire(DEFAULT, null);
        // Pending 2
        assertThat(second, notNullValue());

        // Pending 1 - New Limit 3
        first.completed();

        // Pending 0 - New Limit 4
        second.completed();

        List<CapacityLimiter.Ticket> tickets = new ArrayList<>(4);
        // Verify new limit
        for (int i = 0; i < 4; i++) {
            tickets.add(capacityLimiter.tryAcquire(DEFAULT, null));
        }
        // Pending 4 - Limit 4

        // Finalize pending
        for (CapacityLimiter.Ticket ticket : tickets) {
            ticket.completed();
        }
        // Pending 0 - Limit 4
    }
}
