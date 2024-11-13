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
import io.servicetalk.capacity.limiter.api.GradientCapacityLimiterBuilder;

import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.capacity.limiter.api.CapacityLimiters.dynamicGradientOptimizeForThroughput;

public final class GradientObservabilityExample {

    @SuppressWarnings({"UseOfSystemOutOrSystemErr", "PMD.SystemPrintln"})
    public static void main(String[] args) {
        @SuppressWarnings("unused")
        final CapacityLimiter limiter = dynamicGradientOptimizeForThroughput()
                .observer(new GradientCapacityLimiterBuilder.Observer() {
                    private final AtomicLong started = new AtomicLong(0);
                    private final AtomicLong finished = new AtomicLong(0);

                    @Override
                    public void onActiveRequestsIncr() {
                        started.incrementAndGet();
                        System.out.printf("In flight requests: %d%n", started.get() - finished.get());
                        // With this approach you can track in-flight requests monotonically in telemetry platforms.
                    }

                    @Override
                    public void onActiveRequestsDecr() {
                        finished.incrementAndGet();
                        System.out.printf("In flight requests: %d%n", started.get() - finished.get());
                        // With this approach you can track in-flight requests monotonically in telemetry platforms.
                    }

                    @Override
                    public void onLimitChange(final double longRtt, final double shortRtt, final double gradient,
                                              final double oldLimit, final double newLimit) {
                        System.out.printf("Gradient state changed. Previous Limit: %f, New Limit: %f, shortRTT: %f, " +
                                "longRTT: %f, gradient: %f%n", oldLimit, newLimit, shortRtt, longRtt, gradient);
                    }

                })
                .build();
    }
}
