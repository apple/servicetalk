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
/*
 * Copyright 2017 The Resilience4j Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.apple.circuit.breaker.api;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * ServiceTalk API for a <a href="https://martinfowler.com/bliki/CircuitBreaker.html">Circuit Breaker</a>.
 */
public interface CircuitBreaker {

    /**
     * Return the name of this {@link CircuitBreaker}.
     * @return the name of this {@link CircuitBreaker}.
     */
    String name();

    /**
     * Attempt to acquire a permit to execute a call.
     *
     * @return {@code true} when a permit was successfully acquired.
     */
    boolean tryAcquirePermit();

    /**
     * Releases a previously {@link #tryAcquirePermit() acquired} permit, without influencing the breaker in any
     * way. In other words this permit is considered ignored.
     */
    void ignorePermit();

    /**
     * Track a failed call and the reason.
     *
     * @param duration – The elapsed time duration of the call
     * @param durationUnit – The duration unit
     * @param throwable – The throwable which must be recorded
     */
    void onError(long duration, TimeUnit durationUnit, Throwable throwable);

    /**
     * Track a successful call.
     *
     * @param duration The elapsed time duration of the call
     * @param durationUnit The duration unit
     */
    void onSuccess(long duration, TimeUnit durationUnit);

    /**
     * Forcefully open the breaker, if automatic state transition is supported it should be disabled after this call.
     */
    void forceOpenState();

    /**
     * Returns the circuit breaker to its original closed state and resetting any internal state (timers, metrics).
     */
    void reset();

    /**
     * If the state of the breaker is open, this retrieves the remaining duration it should stay open.
     * Expectation is that after this duration, the breaker shall allow/offer more permits.
     *
     * @return The remaining duration that the breaker will remain open.
     */
    Duration remainingDurationInOpenState();
}
