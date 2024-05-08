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
package io.servicetalk.apple.capacity.management.resilience4j;

import io.servicetalk.apple.circuit.breaker.api.CircuitBreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Resilience4jAdaptersTest {

    io.github.resilience4j.circuitbreaker.CircuitBreaker r4jBreaker;
    CircuitBreaker breaker;

    @BeforeEach
    void setup() {
        CircuitBreakerConfig config = CircuitBreakerConfig
                .custom()
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .currentTimestampFunction(clock -> nanoTime(), NANOSECONDS)
                .build();

        r4jBreaker = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("foobar", config);
        breaker = Resilience4jAdapters.fromCircuitBreaker(r4jBreaker);
    }

    @Test
    void verifyOpenStateDueToFailureRate() {
        // 1st request allowed
        assertMakeRequestAllowed();
        // Failures increased by 1
        breaker.onError(10, MILLISECONDS, new Exception());

        // 2nd request allowed
        assertMakeRequestAllowed();
        // Failures increased to 2. Min number of calls reached, and we have 100% failure rate
        breaker.onError(10, MILLISECONDS, new Exception());

        // All calls should now be rejected
        assertMakeRequestRejected();
    }

    @Test
    void verifyOpenStateDueToManualOpen() {
        r4jBreaker.transitionToForcedOpenState();
        // All calls should now be rejected
        assertMakeRequestRejected();

        r4jBreaker.transitionToClosedState();
        assertMakeRequestAllowed();
    }

    private void assertMakeRequestAllowed() {
        assertTrue(breaker.tryAcquirePermit());
    }

    private void assertMakeRequestRejected() {
        assertFalse(breaker.tryAcquirePermit());
    }
}
