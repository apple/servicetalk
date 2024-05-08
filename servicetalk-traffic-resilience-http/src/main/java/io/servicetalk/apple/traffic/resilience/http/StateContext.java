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
package io.servicetalk.apple.traffic.resilience.http;

import io.servicetalk.apple.circuit.breaker.api.CircuitBreaker;

import javax.annotation.Nullable;

/**
 * State information of the {@link TrafficResilienceHttpServiceFilter traffic-resilience} service filter.
 */
public final class StateContext {

    @Nullable
    private final CircuitBreaker breaker;

    StateContext(@Nullable final CircuitBreaker breaker) {
        this.breaker = breaker;
    }

    /**
     * Returns the {@link CircuitBreaker} in-use for the currently evaluated request.
     * @return The {@link CircuitBreaker} in-use for the currently evaluated request.
     */
    @Nullable
    public CircuitBreaker breaker() {
        return breaker;
    }
}
