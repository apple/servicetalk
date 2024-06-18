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

import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isPositive;
import static java.time.Duration.ofSeconds;

final class HealthCheckConfig {

    static final Duration DEFAULT_HEALTH_CHECK_INTERVAL = ofSeconds(5);
    static final Duration DEFAULT_HEALTH_CHECK_JITTER = ofSeconds(3);
    static final Duration DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL = ofSeconds(10);
    static final int DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD = 5; // higher than default for AutoRetryStrategy

    final Executor executor;
    final Duration healthCheckInterval;
    final Duration jitter;
    final int failedThreshold;
    final Duration resubscribeInterval;
    final Duration healthCheckResubscribeJitter;

    // Computed from the constructor values.
    final long healthCheckResubscribeLowerBound;
    final long healthCheckResubscribeUpperBound;

    HealthCheckConfig(final Executor executor, final Duration healthCheckInterval, final Duration healthCheckJitter,
                      final int failedThreshold, final Duration resubscribeInterval,
                      final Duration healthCheckResubscribeJitter) {
        this.executor = executor;
        this.healthCheckInterval = healthCheckInterval;
        this.jitter = healthCheckJitter;
        this.failedThreshold = failedThreshold;
        this.resubscribeInterval = resubscribeInterval;
        this.healthCheckResubscribeJitter = healthCheckResubscribeJitter;

        validateHealthCheckIntervals(resubscribeInterval, healthCheckResubscribeJitter);
        this.healthCheckResubscribeLowerBound = resubscribeInterval.minus(healthCheckResubscribeJitter).toNanos();
        this.healthCheckResubscribeUpperBound = resubscribeInterval.plus(healthCheckResubscribeJitter).toNanos();
    }

    static void validateHealthCheckIntervals(Duration interval, Duration jitter) {
        ensurePositive(interval, "interval");
        ensureNonNegative(jitter, "jitter");
        final Duration lowerBound = interval.minus(jitter);
        if (!isPositive(lowerBound)) {
            throw new IllegalArgumentException("interval (" + interval + ") minus jitter (" + jitter +
                    ") must be greater than 0, current=" + lowerBound);
        }
        final Duration upperBound = interval.plus(jitter);
        if (!isPositive(upperBound)) {
            throw new IllegalArgumentException("interval (" + interval + ") plus jitter (" + jitter +
                    ") must not overflow, current=" + upperBound);
        }
    }

    @Override
    public String toString() {
        return "HealthCheckConfig{" +
                "executor=" + executor +
                ", healthCheckInterval=" + healthCheckInterval +
                ", jitter=" + jitter +
                ", failedThreshold=" + failedThreshold +
                ", resubscribeInterval=" + resubscribeInterval +
                ", healthCheckResubscribeJitter=" + healthCheckResubscribeJitter +
                '}';
    }
}
