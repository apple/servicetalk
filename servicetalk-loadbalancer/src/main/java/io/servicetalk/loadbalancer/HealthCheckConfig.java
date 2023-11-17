/*
 * Copyright © 2021-2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.concurrent.api.Executor;

import java.time.Duration;

import static io.servicetalk.loadbalancer.L4HealthCheck.validateHealthCheckIntervals;

final class HealthCheckConfig {
    final Executor executor;
    final Duration healthCheckInterval;
    final Duration jitter;
    final int failedThreshold;
    final long resubscribeLowerBoundNanos;
    final long resubscribeUpperBoundNanos;

    HealthCheckConfig(final Executor executor, final Duration healthCheckInterval, final Duration healthCheckJitter,
                      final int failedThreshold, final Duration resubscribeInterval,
                      final Duration healthCheckResubscribeJitter) {
        this.executor = executor;
        this.healthCheckInterval = healthCheckInterval;
        this.failedThreshold = failedThreshold;
        this.jitter = healthCheckJitter;

        validateHealthCheckIntervals(resubscribeInterval, healthCheckResubscribeJitter);
        this.resubscribeLowerBoundNanos = resubscribeInterval.minus(healthCheckResubscribeJitter).toNanos();
        this.resubscribeUpperBoundNanos = resubscribeInterval.plus(healthCheckResubscribeJitter).toNanos();
    }
}
