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

import java.util.List;

/**
 * Logic that identifies outlier hosts based on the request stats collected since the last detection round.
 * <p>
 * Multiple algorithms can be active simultaneously (e.g. success-rate and failure-percentage). To match the XDS
 * model where every dimension is evaluated against the same accumulated dataset, algorithms are pure: they only read
 * the current stats and mark outliers. They must not mutate host state, reset the stats counters, or eject hosts. The
 * caller ({@link XdsOutlierDetector}) collects all algorithms' verdicts, applies the combined result to each host
 * exactly once per round, and resets the counters afterwards.
 */
@FunctionalInterface
interface XdsOutlierDetectorAlgorithm<ResolvedAddress, C extends LoadBalancedConnection> {
    /**
     * Analyze the current stats and mark outlier hosts.
     * <p>
     * Implementations read stats from {@code indicators} but must not reset them or change host status. For each host
     * this algorithm considers an outlier, set the corresponding entry in {@code outliers} to {@code true}. Entries
     * must only ever be set to {@code true} (never back to {@code false}) because verdicts from all active algorithms
     * are combined via logical OR. Hosts that are already unhealthy are handled by the caller and should be left out
     * of the verdict (but may be excluded from statistical analysis as appropriate).
     * @param config the current {@link OutlierDetectorConfig} to use.
     * @param indicators an ordered list of {@link XdsHealthIndicator} instances to collect stats from.
     * @param outliers a mutable array, indexed in the same order as {@code indicators}, into which this algorithm ORs
     *                 its outlier verdict.
     */
    void detectOutliers(OutlierDetectorConfig config,
                        List<? extends XdsHealthIndicator<ResolvedAddress, C>> indicators,
                        boolean[] outliers);
}
