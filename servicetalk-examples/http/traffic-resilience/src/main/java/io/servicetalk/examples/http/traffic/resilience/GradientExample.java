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

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.CapacityLimiters;

public final class GradientExample {

    public static void main(String[] args) {
        // Initializing a Gradient limiter with configuration profile that favors lower latency.
        // See the `.dynamicGradientOptimizeForThroughput()` variant to optimize for higher throughput.
        @SuppressWarnings("unused")
        final CapacityLimiter limiter = CapacityLimiters.dynamicGradientOptimizeForLatency()
                .build();
    }
}
