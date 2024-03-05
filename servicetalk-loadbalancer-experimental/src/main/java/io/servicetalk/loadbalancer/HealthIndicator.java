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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.concurrent.Cancellable;

/**
 * An abstraction used by a {@link Host} to interact with the {@link OutlierDetector} currently monitoring
 * the host.
 * <p>
 * This abstraction serves as a sort of two-way channel between a host and the health check system: the
 * health check system can give the host information about it's perceived health and the host can give the
 * health check system information about request results.
 */
interface HealthIndicator<ResolvedAddress, C extends LoadBalancedConnection> extends
        RequestTracker, ConnectTracker, ScoreSupplier, Cancellable {

    /**
     * Whether the host is considered healthy by the HealthIndicator.
     *
     * @return true if the HealthIndicator considers the host healthy, false otherwise.
     */
    boolean isHealthy();

    /**
     * Set the {@link Host} associated with this health indicator.
     * @param host which will be associated with this health indicator.
     */
    void setHost(Host<ResolvedAddress, C> host);

    /**
     * Get the {@link Host} associated with this health indicator.
     * @return the {@link Host} associated with this health indicator.
     */
    Host<ResolvedAddress, C> host();
}
