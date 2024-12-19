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

import java.util.Collection;

/**
 * Logic that can detect outliers and attempts to mark them as an outlier so that the load balancer
 * can try to route traffic to more healthy hosts.
 */
interface XdsOutlierDetectorAlgorithm<ResolvedAddress, C extends LoadBalancedConnection> {
    /**
     * Analyze and potentially eject outlier hosts.
     * @param config the current {@link OutlierDetectorConfig} to use.
     * @param indicators an ordered list of {@link HealthIndicator} instances to collect stats from.
     */
    void detectOutliers(OutlierDetectorConfig config,
                        Collection<? extends XdsHealthIndicator<ResolvedAddress, C>> indicators);
}
