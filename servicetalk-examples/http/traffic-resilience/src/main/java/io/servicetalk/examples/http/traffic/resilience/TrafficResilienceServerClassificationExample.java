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
import io.servicetalk.traffic.resilience.http.TrafficResilienceHttpServiceFilter;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.netty.HttpServers;

public class TrafficResilienceServerClassificationExample {

    public static void main(String[] args) throws Exception {
        final TrafficResilienceHttpServiceFilter resilienceFilter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> CapacityLimiters.dynamicGradient().build())
                        .classifier(() -> (meta) -> "/health".equals(meta.requestTarget()) ? () -> 20 : () -> 100)
                        .build();

        HttpServers.forPort(0)
                .appendNonOffloadingServiceFilter(resilienceFilter)
                .listenAndAwait((ctx, request, responseFactory) -> Single.succeeded(responseFactory.ok()));
    }
}
