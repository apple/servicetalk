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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
import static io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent.Type.STATE_TRANSITION;
import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Set of adapters converting from <a href="https://resilience4j.readme.io">Resilience4j</a> APIs to ServiceTalk APIs.
 */
public final class Resilience4jAdapters {

    private static final int IGNORE_RETRIES_ARG = 1;

    private Resilience4jAdapters() {
        // No instances.
    }

    /**
     * ServiceTalk Circuit Breaker adapter for Resilience4j's
     * {@link io.github.resilience4j.circuitbreaker.CircuitBreaker}.
     * The {@code breaker} can be mutated outside the boundaries of the
     * {@link io.servicetalk.apple.circuit.breaker.api.CircuitBreaker} API, allowing
     * users to manually reset it or even transition states if needed.
     *
     * @param breaker The {@link io.github.resilience4j.circuitbreaker.CircuitBreaker} that will be adapted as a
     * {@link io.servicetalk.apple.circuit.breaker.api.CircuitBreaker}.
     * @return A {@link io.servicetalk.apple.circuit.breaker.api.CircuitBreaker} adapter of the provided
     * {@code breaker}.
     * @see <a href="https://resilience4j.readme.io/docs/circuitbreaker">Resilience4j CircuitBreaker</a>
     */
    public static io.servicetalk.apple.circuit.breaker.api.CircuitBreaker fromCircuitBreaker(
            final io.github.resilience4j.circuitbreaker.CircuitBreaker breaker) {
        return new R4jCircuitBreaker(requireNonNull(breaker));
    }

    private static final class R4jCircuitBreaker implements CircuitBreaker {

        private final io.github.resilience4j.circuitbreaker.CircuitBreaker breaker;

        private long breakerOpenedAtMillis;

        private R4jCircuitBreaker(final io.github.resilience4j.circuitbreaker.CircuitBreaker breaker) {
            this.breaker = breaker;
            this.breaker.getEventPublisher().onStateTransition(event -> {
                if (event.getEventType() == STATE_TRANSITION &&
                        event.getStateTransition().getToState() == OPEN) {
                        breakerOpenedAtMillis = NANOSECONDS.toMillis(nanoTime());
                }
            });
        }

        @Override
        public String name() {
            return breaker.getName();
        }

        @Override
        public boolean tryAcquirePermit() {
            return breaker.tryAcquirePermission();
        }

        @Override
        public void ignorePermit() {
            breaker.releasePermission();
        }

        @Override
        public void onError(final long duration, final TimeUnit durationUnit, final Throwable throwable) {
            breaker.onError(duration, durationUnit, throwable);
        }

        @Override
        public void onSuccess(final long duration, final TimeUnit durationUnit) {
            breaker.onSuccess(duration, durationUnit);
        }

        @Override
        public void forceOpenState() {
            breaker.transitionToForcedOpenState();
        }

        @Override
        public void reset() {
            breaker.reset();
        }

        @Override
        public Duration remainingDurationInOpenState() {
            final long openDurationMillis = breaker.getCircuitBreakerConfig()
                    .getWaitIntervalFunctionInOpenState().apply(IGNORE_RETRIES_ARG);
            final long openDeadline = breakerOpenedAtMillis + openDurationMillis;
            return Duration.ofMillis(max(0, openDeadline - NANOSECONDS.toMillis(nanoTime())));
        }
    }
}
