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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.concurrent.Cancellable;

/**
 * An abstraction used by a {@link Host} to interact with the {@link HealthChecker} currently monitoring
 * the host.
 * <p>
 * This abstraction serves as a sort of two-way channel between a host and the health check system: the
 * health check system can give the host information about it's perceived health and the host can give the
 * health check system information about request results.
 */
interface HealthIndicator extends RequestTracker, ScoreSupplier, Cancellable {

    /**
     * Whether the host is considered healthy by the HealthIndicator.
     *
     * @return true if the HealthIndicator considers the host healthy, false otherwise.
     */
    boolean isHealthy();

    /**
     * Get the current time in nanoseconds.
     * Note: this must not be a stateful API. Eg, it does not necessarily have a correlation with any other method call
     * and such shouldn't be used as a method of counting in the same way that {@link RequestTracker} is used.
     * @return the current time in nanoseconds.
     */
    long currentTimeNanos();

    /**
     * Callback to notify the parent {@link HealthChecker} that an attempt to connect to this host has failed.
     * @param startTimeNanos the time that the connection attempt was initiated.
     */
    void onConnectFailure(long startTimeNanos);
}
