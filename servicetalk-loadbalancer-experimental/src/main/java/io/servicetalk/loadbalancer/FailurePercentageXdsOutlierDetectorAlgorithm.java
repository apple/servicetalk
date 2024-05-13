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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static io.servicetalk.loadbalancer.OutlierDetectorConfig.enforcing;

final class FailurePercentageXdsOutlierDetectorAlgorithm<ResolvedAddress, C extends LoadBalancedConnection>
        implements XdsOutlierDetectorAlgorithm<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailurePercentageXdsOutlierDetectorAlgorithm.class);

    // We use a sentinel value to mark values as 'skipped' so we don't need to create a dynamically sized
    // data structure for doubles which would require boxing.
    private static final long NOT_EVALUATED = Long.MAX_VALUE;

    @Override
    public void detectOutliers(final OutlierDetectorConfig config,
                               final Collection<XdsHealthIndicator<ResolvedAddress, C>> indicators) {
        final long[] failurePercentages = new long[indicators.size()];
        int i = 0;
        int enoughVolumeHosts = 0;
        int alreadyEjectedHosts = 0;
        for (XdsHealthIndicator<?, ?> indicator : indicators) {
            if (!indicator.isHealthy()) {
                failurePercentages[i] = NOT_EVALUATED;
                alreadyEjectedHosts++;
            } else {
                long successes = indicator.getSuccesses();
                long failures = indicator.getFailures();
                long totalRequests = successes + failures;
                if (totalRequests >= config.failurePercentageRequestVolume()) {
                    enoughVolumeHosts++;
                }
                failurePercentages[i] = totalRequests == 0L ? 0 : failures * 100L / totalRequests;
            }
            i++;
            indicator.resetCounters();
        }

        if (enoughVolumeHosts < config.failurePercentageMinimumHosts()) {
            // not enough hosts with enough volume to do the analysis.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Not enough hosts with sufficient volume to perform ejection: " +
                                "{} total hosts and {} had sufficient volume. Minimum {} required.",
                        indicators.size(), enoughVolumeHosts, config.failurePercentageMinimumHosts());
            }
            return;
        }

        final double failurePercentageThreshold = config.failurePercentageThreshold();
        int ejectedCount = 0;
        i = 0;
        for (XdsHealthIndicator<?, ?> indicator : indicators) {
            long failurePercentage = failurePercentages[i++];
            if (indicator.updateOutlierStatus(config, failurePercentage == NOT_EVALUATED ||
                    failurePercentage >= failurePercentageThreshold &&
                            enforcing(config.enforcingFailurePercentage()))) {
                ejectedCount++;
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Finished host ejection. of {} total hosts {} hosts were already " +
                    "ejected and {} were newly ejected.", indicators.size(), alreadyEjectedHosts, ejectedCount);
        }
    }
}
