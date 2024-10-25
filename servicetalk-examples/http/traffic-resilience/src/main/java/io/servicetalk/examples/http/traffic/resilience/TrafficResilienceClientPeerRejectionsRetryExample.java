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
import io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy;
import io.servicetalk.traffic.resilience.http.TrafficResilienceHttpClientFilter;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;

import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.DEFAULT_CAPACITY_REJECTION_PREDICATE;
import static io.servicetalk.buffer.api.CharSequences.parseLong;
import static io.servicetalk.http.api.HttpHeaderNames.RETRY_AFTER;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofExponentialBackoffDeltaJitter;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

public class TrafficResilienceClientPeerRejectionsRetryExample {
    public static void main(String[] args) {
        final TrafficResilienceHttpClientFilter resilienceFilter =
                new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.dynamicGradient().build())
                        .rejectionPolicy(ClientPeerRejectionPolicy.ofRejectionWithRetries(DEFAULT_CAPACITY_REJECTION_PREDICATE,
                                metaData -> ofSeconds(parseLong(metaData.headers().get(RETRY_AFTER, "1")))))
                        .build();

        final RetryingHttpRequesterFilter retryingHttpRequesterFilter =
                new RetryingHttpRequesterFilter.Builder().retryDelayedRetries((__, retry) ->
                        ofExponentialBackoffDeltaJitter(retry.delay(), ofMillis(500), ofSeconds(2), 2))
                .build();

        HttpClients.forSingleAddress("localhost", 8080)
                .appendClientFilter(retryingHttpRequesterFilter)
                .appendClientFilter(resilienceFilter)
                .build();
    }
}
