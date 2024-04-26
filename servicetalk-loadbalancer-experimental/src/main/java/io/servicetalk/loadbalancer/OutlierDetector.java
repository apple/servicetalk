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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;

/**
 * The representation of a health checking system for use with load balancing.
 */
interface OutlierDetector<ResolvedAddress, C extends LoadBalancedConnection> extends Cancellable {

    /**
     * Construct a new {@link HealthIndicator}.
     * @note The most common use case will be to use the indicator to track a newly constructed {@link Host} but
     * it's possible that a currently live host may have its indicator replaced due to load balancing configuration
     * changes, so it's not safe to assume that the host that will use this tracker is exactly new.
     * @param address the resolved address of the destination.
     * @return new {@link HealthIndicator}.
     */
    HealthIndicator<ResolvedAddress, C> newHealthIndicator(ResolvedAddress address, HostObserver hostObserver);

    // TODO: implement for real.
    default Publisher<Boolean> healthStatusChanged() {
        return Publisher.never();
    }
}
