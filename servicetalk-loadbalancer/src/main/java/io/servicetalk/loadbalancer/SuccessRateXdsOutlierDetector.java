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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static io.servicetalk.loadbalancer.OutlierDetectorConfig.enforcing;

/**
 * An implementation of the xDS success rate outlier detector.
 * <p>
 * This outlier detector is a true outlier detector: it will analyze the success rate (successs / (successs + failures)
 * and then perform a statistical analysis to eject hosts that are more than the configured number of standard
 * deviations from the mean success rate.
 * <p>
 * @see <a href="https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/outlier#success-rate">Envoy
 * Outlier detection</a> documentation for more details.
 */
final class SuccessRateXdsOutlierDetector implements XdsOutlierDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuccessRateXdsOutlierDetector.class);

    // We use a sentinel value to mark values as 'skipped' so we don't need to create a dynamically sized
    // data structure for doubles which would require boxing.
    private static final double NOT_EVALUATED = Double.MAX_VALUE;

    public static final XdsOutlierDetector INSTANCE = new SuccessRateXdsOutlierDetector();

    private SuccessRateXdsOutlierDetector() {
    }

    @Override
    public void detectOutliers(OutlierDetectorConfig config, Collection<XdsHealthIndicator> indicators) {
        LOGGER.trace("Started outlier detection.");
        final double[] successRates = new double[indicators.size()];
        int i = 0;
        int enoughVolumeHosts = 0;
        int alreadyEjectedHosts = 0;
        for (XdsHealthIndicator indicator : indicators) {
            if (!indicator.isHealthy()) {
                successRates[i] = NOT_EVALUATED;
                alreadyEjectedHosts++;
            } else {
                long successes = indicator.getSuccesses();
                long failures = indicator.getFailures();
                long totalRequests = successes + failures;
                if (totalRequests >= config.successRateRequestVolume()) {
                    enoughVolumeHosts++;
                }
                successRates[i] = totalRequests > 0 ? (double) successes / (totalRequests) : 1d;
            }
            i++;
            indicator.resetCounters();
        }

        if (enoughVolumeHosts < config.successRateMinimumHosts()) {
            // not enough hosts with enough volume to do the analysis.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Not enough hosts  with sufficient volume to perform ejection: " +
                        "{} total hosts and {} had sufficient volume. Minimum {} required.",
                        indicators.size(), enoughVolumeHosts, config.successRateMinimumHosts());
            }
            return;
        }

        final double mean = mean(successRates);
        final double stdev = stdev(successRates, mean);
        final double requiredSuccessRate = mean - stdev * (config.successRateStdevFactor() / 1000d);
        int ejectedCount = 0;
        i = 0;
        for (XdsHealthIndicator indicator : indicators) {
            double successRate = successRates[i++];
            if (indicator.updateOutlierStatus(config, successRate == NOT_EVALUATED ||
                    successRate < requiredSuccessRate && enforcing(config.enforcingSuccessRate()))) {
                ejectedCount++;
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Finished host ejection. of {} total hosts {} hosts were already " +
                    "ejected and {} were newly ejected.", indicators.size(), alreadyEjectedHosts, ejectedCount);
        }
    }

    // Will only compute the mean of hosts that should be evaluated.
    private double mean(double[] values) {
        double result = 0d;
        int count = 0;
        for (double l : values) {
            if (l != NOT_EVALUATED) {
                result += l;
                count++;
            }
        }
        return count > 0d ? result / count : 1d;
    }

    // Will only compute the stdev of hosts that should be evaluated.
    private double stdev(double[] values, double mean) {
        double accumulator = 0;
        int count = 0;
        for (double value : values) {
            if (value != NOT_EVALUATED) {
                double diff = value - mean;
                accumulator += diff * diff;
                count++;
            }
        }
        double variance = count > 0 ? accumulator / count : 0d;
        return Math.sqrt(variance);
    }
}
