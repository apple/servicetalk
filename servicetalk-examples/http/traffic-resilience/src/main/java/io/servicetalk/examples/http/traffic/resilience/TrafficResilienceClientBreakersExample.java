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
package io.servicetalk.examples.http.traffic.resilience;

import io.servicetalk.capacity.limiter.api.CapacityLimiters;
import io.servicetalk.circuit.breaker.api.CircuitBreaker;
import io.servicetalk.circuit.breaker.resilience4j.Resilience4jAdapters;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.traffic.resilience.http.TrafficResilienceHttpClientFilter;

public class TrafficResilienceClientBreakersExample {
    public static void main(String[] args) {
        final TrafficResilienceHttpClientFilter resilienceFilter =
                new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.dynamicGradient().build())
                        .circuitBreakerPartitions(() -> {
                            final CircuitBreaker breakerForPathA = Resilience4jAdapters.fromCircuitBreaker(
                                    io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("example-a"));
                            final CircuitBreaker breakerForPathB = Resilience4jAdapters.fromCircuitBreaker(
                                    io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("example-b"));

                            return requestMetaData -> {
                                if ("/A".equals(requestMetaData.requestTarget())) {
                                    return breakerForPathA;
                                }
                                return breakerForPathB;
                            };
                        })
                        .build();

        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .appendClientFilter(resilienceFilter)
                .build()) {
            // use client
        }
    }
}
