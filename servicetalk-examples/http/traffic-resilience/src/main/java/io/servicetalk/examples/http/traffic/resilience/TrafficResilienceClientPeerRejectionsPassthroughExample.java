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
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy;
import io.servicetalk.traffic.resilience.http.TrafficResilienceHttpClientFilter;

import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;

/**
 * A client which configures which responses will affect the capacity limiter but still pass through to the underlying
 * client.
 */
public final class TrafficResilienceClientPeerRejectionsPassthroughExample {
    public static void main(String[] args) {
        final TrafficResilienceHttpClientFilter resilienceFilter =
                new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.dynamicGradient().build())
                        .rejectionPolicy(ClientPeerRejectionPolicy.ofPassthrough(metaData ->
                                metaData.status().code() == TOO_MANY_REQUESTS.code() ||
                                metaData.status().code() == BAD_GATEWAY.code() ||
                                metaData.status().code() == SERVICE_UNAVAILABLE.code()))
                        .build();

        HttpClients.forSingleAddress("localhost", 8080)
                .appendClientFilter(resilienceFilter)
                .build();
    }
}
