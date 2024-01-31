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

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

/**
 * XDS outlier detector configuration.
 * <p>
 * See the <a href="https://www.envoyproxy.io/docs/envoy/v1.29.0/api-v3/config/cluster/v3/
 * outlier_detection.proto#envoy-v3-api-msg-config-cluster-v3-outlierdetection"> Envoy docs</a> for the official
 * OutlierDetector configuration definition.
 */
final class OutlierDetectorConfig {

    private final int consecutive5xx;
    private final Duration interval;
    private final Duration baseEjectionTime;
    private final int maxEjectionPercentage;
    private final int enforcingConsecutive5xx;
    private final int enforcingSuccessRate;
    private final int successRateMinimumHosts;
    private final int successRateRequestVolume;
    private final int successRateStdevFactor;
    private final int consecutiveGatewayFailure;
    private final int enforcingConsecutiveGatewayFailure;
    private final boolean splitExternalLocalOriginErrors;
    private final int consecutiveLocalOriginFailure;
    private final int enforcingConsecutiveLocalOriginFailure;
    private final int enforcingLocalOriginSuccessRate;
    private final int failurePercentageThreshold;
    private final int enforcingFailurePercentage;
    private final int enforcingFailurePercentageLocalOrigin;
    private final int failurePercentageMinimumHosts;
    private final int failurePercentageRequestVolume;
    private final Duration maxEjectionTime;
    private final Duration maxEjectionTimeJitter;
    private final boolean successfulActiveHealthCheckUnejectHost;

    OutlierDetectorConfig(final int consecutive5xx, final Duration interval, final Duration baseEjectionTime,
                          final int maxEjectionPercentage, final int enforcingConsecutive5xx,
                          final int enforcingSuccessRate, final int successRateMinimumHosts,
                          final int successRateRequestVolume, final int successRateStdevFactor,
                          final int consecutiveGatewayFailure, final int enforcingConsecutiveGatewayFailure,
                          final boolean splitExternalLocalOriginErrors, final int consecutiveLocalOriginFailure,
                          final int enforcingConsecutiveLocalOriginFailure, final int enforcingLocalOriginSuccessRate,
                          final int failurePercentageThreshold, final int enforcingFailurePercentage,
                          final int enforcingFailurePercentageLocalOrigin, final int failurePercentageMinimumHosts,
                          final int failurePercentageRequestVolume, final Duration maxEjectionTime,
                          final Duration maxEjectionTimeJitter, final boolean successfulActiveHealthCheckUnejectHost) {
        this.consecutive5xx = consecutive5xx;
        this.interval = requireNonNull(interval, "interval");
        this.baseEjectionTime = requireNonNull(baseEjectionTime, "baseEjectionTime");
        this.maxEjectionPercentage = maxEjectionPercentage;
        this.enforcingConsecutive5xx = enforcingConsecutive5xx;
        this.enforcingSuccessRate = enforcingSuccessRate;
        this.successRateMinimumHosts = successRateMinimumHosts;
        this.successRateRequestVolume = successRateRequestVolume;
        this.successRateStdevFactor = successRateStdevFactor;
        this.consecutiveGatewayFailure = consecutiveGatewayFailure;
        this.enforcingConsecutiveGatewayFailure = enforcingConsecutiveGatewayFailure;
        this.splitExternalLocalOriginErrors = splitExternalLocalOriginErrors;
        this.consecutiveLocalOriginFailure = consecutiveLocalOriginFailure;
        this.enforcingConsecutiveLocalOriginFailure = enforcingConsecutiveLocalOriginFailure;
        this.enforcingLocalOriginSuccessRate = enforcingLocalOriginSuccessRate;
        this.failurePercentageThreshold = failurePercentageThreshold;
        this.enforcingFailurePercentage = enforcingFailurePercentage;
        this.enforcingFailurePercentageLocalOrigin = enforcingFailurePercentageLocalOrigin;
        this.failurePercentageMinimumHosts = failurePercentageMinimumHosts;
        this.failurePercentageRequestVolume = failurePercentageRequestVolume;
        this.maxEjectionTime = requireNonNull(maxEjectionTime, "maxEjectionTime");
        this.maxEjectionTimeJitter = requireNonNull(maxEjectionTimeJitter, "maxEjectionTimeJitter");
        this.successfulActiveHealthCheckUnejectHost = successfulActiveHealthCheckUnejectHost;
    }

    /**
     * The number of consecutive failures before the attempt to suspect the host.
     * @return the number of consecutive failures before the attempt to suspect the host.
     */
    public int consecutive5xx() {
        return consecutive5xx;
    }

    /**
     * The interval on which to run failure percentage and success rate failure detectors.
     * @return the interval on which to run failure percentage and success rate failure detectors.
     */
    public Duration interval() {
        return interval;
    }

    /**
     * The base ejection time.
     * The base ejection time is multiplied by the number of consecutive times the host has been ejected to get the
     * total ejection time, capped by the {@link #maxEjectionTime()}.
     * @return the base ejection time.
     */
    public Duration baseEjectionTime() {
        return baseEjectionTime;
    }

    /**
     * The maximum percentage of hosts that can be ejected due to outlier detection.
     * @return the maximum percentage of hosts that can be ejected due to outlier detection.
     */
    public int maxEjectionPercentage() {
        return maxEjectionPercentage;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host reaches the
     * {@link #consecutive5xx()} threshold.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingConsecutive5xx() {
        return enforcingConsecutive5xx;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the success rate
     * outlier detectors threshold.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingSuccessRate() {
        return enforcingSuccessRate;
    }

    /**
     * The minimum number of hosts required to perform the success rate outlier detector analysis.
     * @return the minimum number of hosts required to perform the success rate outlier detector analysis.
     */
    public int successRateMinimumHosts() {
        return successRateMinimumHosts;
    }

    /**
     * The minimum number of requests in an outlier detector interval required to include it in the success rate
     * outlier detector analysis.
     * @return the minimum number of request required.
     */
    public int successRateRequestVolume() {
        return successRateRequestVolume;
    }

    /**
     * The value divided by 1000 and then multiplied against the success rate standard deviation which sets the
     * threshold for ejection in the success rate outlier detector.
     * @return the stdev factor divided by 1000 used to determine the statistical outliers.
     */
    public int successRateStdevFactor() {
        return successRateStdevFactor;
    }

    /**
     * The threshold for consecutive gateway failures before local the host is ejected.
     * @return the threshold for consecutive gateway failures before local the host is ejected.
     */
    public int consecutiveGatewayFailure() {
        return consecutiveGatewayFailure;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the consecutive gateway
     * failure threshold.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingConsecutiveGatewayFailure() {
        return enforcingConsecutiveGatewayFailure;
    }

    /**
     * Whether to split local origin and remote origin failures into separate failure detectors.
     * Note: this being true predicates the validity of many of the configuration parameters of this class.
     * @return true if local and remote origin failures should be split, false otherwise.
     */
    public boolean splitExternalLocalOriginErrors() {
        return splitExternalLocalOriginErrors;
    }

    /**
     * The threshold for locally originated consecutive failures before ejection occurs.
     * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
     * @return the threshold of consecutive locally originated failures for ejection.
     */
    public int consecutiveLocalOriginFailure() {
        return consecutiveLocalOriginFailure;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the consecutive local
     * origin failure threshold.
     * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingConsecutiveLocalOriginFailure() {
        return enforcingConsecutiveLocalOriginFailure;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the success rate
     * outlier detectors threshold for local origin failures.
     * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingLocalOriginSuccessRate() {
        return enforcingLocalOriginSuccessRate;
    }

    /**
     * The failure threshold in percentage for ejecting a host.
     * @return the failure threshold in percentage for ejecting a host.
     */
    public int failurePercentageThreshold() {
        return failurePercentageThreshold;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the failure percentage
     * outlier detectors threshold.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingFailurePercentage() {
        return enforcingFailurePercentage;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the failure percentage
     * outlier detectors threshold for locally originated failures.
     * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingFailurePercentageLocalOrigin() {
        return enforcingFailurePercentageLocalOrigin;
    }

    /**
     * The minimum number of hosts required to perform the failure percentage outlier detector analysis.
     * @return the minimum number of hosts required to perform the failure percentage outlier detector analysis.
     */
    public int failurePercentageMinimumHosts() {
        return failurePercentageMinimumHosts;
    }

    /**
     * The minimum number of requests in an outlier detector interval required to include it in the failure percentage
     * outlier detector analysis.
     * @return the minimum number of request required.
     */
    public int failurePercentageRequestVolume() {
        return failurePercentageRequestVolume;
    }

    /**
     * The maximum amount of time a host can be ejected regardless of the number of consecutive ejections.
     * @return the maximum amount of time a host can be ejected.
     */
    public Duration maxEjectionTime() {
        return maxEjectionTime;
    }

    /**
     * The maximum amount of jitter to add to the ejection time.
     * An additional amount of 'jitter' is added to the ejection time to prevent connection storms if multiple hosts
     * are ejected at the time.
     * @return the maximum amount of jitter to add to the ejection time.
     */
    public Duration maxEjectionTimeJitter() {
        return maxEjectionTimeJitter;
    }

    /**
     * Whether to un-eject a host that has had a successful active health check event to be revived regardless of the
     * remaining ejection time.
     * @return whether to un-eject a host regardless of remaining ejection time.
     */
    public boolean successfulActiveHealthCheckUnejectHost() {
        return successfulActiveHealthCheckUnejectHost;
    }

    /**
     * A builder for {@link OutlierDetectorConfig} instances.
     */
    public static class Builder {
        private int consecutive5xx = 5;

        private Duration interval = Duration.ofSeconds(10);

        private Duration baseEjectionTime = Duration.ofSeconds(30);

        private int maxEjectionPercentage = 10;

        private int enforcingConsecutive5xx = 100;

        private int enforcingSuccessRate = 100;

        private int successRateMinimumHosts = 5;

        private int successRateRequestVolume = 100;

        private int successRateStdevFactor = 1900;

        private int consecutiveGatewayFailure = 5;

        private int enforcingConsecutiveGatewayFailure;

        private boolean splitExternalLocalOriginErrors;

        private int consecutiveLocalOriginFailure = 5;

        private int enforcingConsecutiveLocalOriginFailure = 100;

        private int enforcingLocalOriginSuccessRate = 100;

        private int failurePercentageThreshold = 85;

        private int enforcingFailurePercentage;

        private int enforcingFailurePercentageLocalOrigin;

        private int failurePercentageMinimumHosts = 5;

        private int failurePercentageRequestVolume = 50;

        private Duration maxEjectionTime = Duration.ofSeconds(300);

        private Duration maxEjectionTimeJitter = Duration.ZERO;

        private boolean successfulActiveHealthCheckUnejectHost = true;

        OutlierDetectorConfig build() {
            return new OutlierDetectorConfig(consecutive5xx, interval, baseEjectionTime,
                    maxEjectionPercentage, enforcingConsecutive5xx,
                    enforcingSuccessRate, successRateMinimumHosts,
                    successRateRequestVolume, successRateStdevFactor,
                    consecutiveGatewayFailure, enforcingConsecutiveGatewayFailure,
                    splitExternalLocalOriginErrors, consecutiveLocalOriginFailure,
                    enforcingConsecutiveLocalOriginFailure, enforcingLocalOriginSuccessRate,
                    failurePercentageThreshold, enforcingFailurePercentage,
                    enforcingFailurePercentageLocalOrigin, failurePercentageMinimumHosts,
                    failurePercentageRequestVolume, maxEjectionTime,
                    maxEjectionTimeJitter,
                    successfulActiveHealthCheckUnejectHost);
        }

        /**
         * Set the threshold for consecutive failures before a host is ejected.
         * Defaults to 5.
         * @param consecutive5xx the threshold for consecutive failures before a host is ejected.
         * @return {@code this}
         */
        public Builder consecutive5xx(final int consecutive5xx) {
            ensurePositive(consecutive5xx, "consecutive5xx");
            this.consecutive5xx = consecutive5xx;
            return this;
        }

        /**
         * Set the interval on which to run failure percentage and success rate failure detectors.
         * Defaults to 10 seconds.
         * @param interval the interval on which to run failure percentage and success rate failure detectors.
         * @return {@code this}
         */
        public Builder interval(final Duration interval) {
            this.interval = requireNonNull(interval, "interval");
            ensurePositive(interval.toNanos(), "interval");
            return this;
        }

        /**
         * Set the base ejection time.
         * Defaults to 30 seconds.
         * @param baseEjectionTime the base ejection time.
         * @return {@code this}.
         */
        public Builder baseEjectionTime(final Duration baseEjectionTime) {
            this.baseEjectionTime = requireNonNull(baseEjectionTime, "baseEjectionTime");
            ensurePositive(baseEjectionTime.toNanos(), "baseEjectionTime");
            return this;
        }

        /**
         * Set the maximum percentage of hosts that can be ejected due to outlier detection.
         * Defaults to 10% but at least one host will be allowed to be ejected regardless of value.
         * @param maxEjectionPercentage the maximum percentage of hosts that can be ejected due to outlier detection.
         * @return {@code this}.
         */
        public Builder maxEjectionPercentage(final int maxEjectionPercentage) {
            ensureNonNegative(maxEjectionPercentage, "maxEjectionPercentage");
            this.maxEjectionPercentage = maxEjectionPercentage;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host reaches the
         * {@link #consecutive5xx()} threshold.
         * Defaults to 100%.
         * @param enforcingConsecutive5xx the probability the host will be marked as unhealthy.
         * @return {@code this}.
         */
        public Builder enforcingConsecutive5xx(final int enforcingConsecutive5xx) {
            ensureNonNegative(enforcingConsecutive5xx, "enforcingConsecutive5xx");
            this.enforcingConsecutive5xx = enforcingConsecutive5xx;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host exceeds the success
         * rate outlier detectors threshold.
         * Defaults to 100%.
         * @param enforcingSuccessRate the probability the host will be marked as unhealthy.
         * @return {@code this}.
         */
        public Builder enforcingSuccessRate(final int enforcingSuccessRate) {
            ensureNonNegative(enforcingSuccessRate, "enforcingSuccessRate");
            this.enforcingSuccessRate = enforcingSuccessRate;
            return this;
        }

        /**
         * Set the minimum number of hosts required to perform the success rate outlier detector analysis.
         * Defaults to 5.
         * @param successRateMinimumHosts the minimum number of hosts required to perform the success rate outlier
         *                                detector analysis.
         * @return {@code this}.
         */
        public Builder successRateMinimumHosts(final int successRateMinimumHosts) {
            ensureNonNegative(successRateMinimumHosts, "successRateMinimumHosts");
            this.successRateMinimumHosts = successRateMinimumHosts;
            return this;
        }

        /**
         * Set the minimum number of requests in an outlier detector interval required to include it in the success rate
         * outlier detector analysis.
         * Defaults to 100.
         * @param successRateRequestVolume the minimum number of requests in an outlier detector interval required to
         *                                 include it in the success rate outlier detector analysis.
         * @return {@code this}.
         */
        public Builder successRateRequestVolume(final int successRateRequestVolume) {
            ensurePositive(successRateRequestVolume, "successRateRequestVolume");
            this.successRateRequestVolume = successRateRequestVolume;
            return this;
        }

        /**
         * Set the value divided by 1000 and then multiplied against the success rate standard deviation which sets the
         * threshold for ejection in the success rate outlier detector.
         * Defaults to 1900.
         * @param successRateStdevFactor the value divided by 1000 and then multiplied against the success rate standard
         *                               deviation which sets the threshold for ejection in the success rate outlier
         *                               detector.
         * @return {@code this}.
         */
        public Builder successRateStdevFactor(final int successRateStdevFactor) {
            ensurePositive(successRateStdevFactor, "successRateStdevFactor");
            this.successRateStdevFactor = successRateStdevFactor;
            return this;
        }

        /**
         * Set the threshold for consecutive gateway failures before local the host is ejected.
         * Defaults to 5.
         * @param consecutiveGatewayFailure the threshold for consecutive gateway failures before local the host is
         *                                  ejected.
         * @return {@code this}.
         */
        public Builder consecutiveGatewayFailure(final int consecutiveGatewayFailure) {
            ensurePositive(consecutiveGatewayFailure, "consecutiveGatewayFailure");
            this.consecutiveGatewayFailure = consecutiveGatewayFailure;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host exceeds the consecutive
         * gateway failure threshold.
         * Defaults to 0.
         * @param enforcingConsecutiveGatewayFailure the probability in percentage that a host will be marked as
         *                                           unhealthy when a host exceeds the consecutive gateway failure
         *                                           threshold.
         * @return {@code this}.
         */
        public Builder enforcingConsecutiveGatewayFailure(final int enforcingConsecutiveGatewayFailure) {
            ensureNonNegative(enforcingConsecutiveGatewayFailure, "enforcingConsecutiveGatewayFailure");
            this.enforcingConsecutiveGatewayFailure = enforcingConsecutiveGatewayFailure;
            return this;
        }

        /**
         * Set whether to split local origin and remote origin failures into separate failure detectors.
         * Defaults to false.
         * Note: this being true predicates the validity of many of the configuration parameters of this class.
         * @param splitExternalLocalOriginErrors whether to split local origin and remote origin failures into separate
         *                                       failure detectors.
         * @return {@code this}.
         */
        public Builder splitExternalLocalOriginErrors(final boolean splitExternalLocalOriginErrors) {
            this.splitExternalLocalOriginErrors = splitExternalLocalOriginErrors;
            return this;
        }

        /**
         * Set the threshold for locally originated consecutive failures before ejection occurs.
         * Defaults to 5.
         * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
         * @param consecutiveLocalOriginFailure the threshold for locally originated consecutive failures before
         *                                      ejection occurs.
         * @return {@code this}.
         */
        public Builder consecutiveLocalOriginFailure(final int consecutiveLocalOriginFailure) {
            ensurePositive(consecutiveLocalOriginFailure, "consecutiveLocalOriginFailure");
            this.consecutiveLocalOriginFailure = consecutiveLocalOriginFailure;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host exceeds the consecutive
         * local origin failure threshold.
         * Defaults to 100%.
         * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
         * @param enforcingConsecutiveLocalOriginFailure the probability in percentage that a host will be marked as
         *                                               unhealthy when a host exceeds the consecutive local origin
         *                                               failure threshold.
         * @return {@code this}.
         */
        public Builder enforcingConsecutiveLocalOriginFailure(final int enforcingConsecutiveLocalOriginFailure) {
            ensureNonNegative(enforcingConsecutiveLocalOriginFailure, "enforcingConsecutiveLocalOriginFailure");
            this.enforcingConsecutiveLocalOriginFailure = enforcingConsecutiveLocalOriginFailure;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host exceeds the success
         * rate outlier detectors threshold for local origin failures.
         * Defaults to 100%.
         * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
         * @param enforcingLocalOriginSuccessRate the probability in percentage that a host will be marked as unhealthy
         *                                        when a host exceeds the success rate outlier detectors threshold for
         *                                        local origin failures.
         * @return {@code this}.
         */
        public Builder enforcingLocalOriginSuccessRate(final int enforcingLocalOriginSuccessRate) {
            ensureNonNegative(enforcingLocalOriginSuccessRate, "enforcingLocalOriginSuccessRate");
            this.enforcingLocalOriginSuccessRate = enforcingLocalOriginSuccessRate;
            return this;
        }

        /**
         * Set the failure threshold in percentage for ejecting a host.
         * Defaults to 85%.
         * @param failurePercentageThreshold the failure threshold in percentage for ejecting a host.
         * @return {@code this}.
         */
        public Builder failurePercentageThreshold(final int failurePercentageThreshold) {
            ensurePositive(failurePercentageThreshold, "failurePercentageThreshold");
            this.failurePercentageThreshold = failurePercentageThreshold;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host exceeds the failure
         * percentage outlier detectors threshold.
         * Defaults to 0%.
         * @param enforcingFailurePercentage the probability in percentage that a host will be marked as unhealthy when
         *                                   percentage outlier detectors threshold.
         * @return {@code this}.
         */
        public Builder enforcingFailurePercentage(final int enforcingFailurePercentage) {
            ensureNonNegative(enforcingFailurePercentage, "enforcingFailurePercentage");
            this.enforcingFailurePercentage = enforcingFailurePercentage;
            return this;
        }

        /**
         * Set the probability in percentage that a host will be marked as unhealthy when a host exceeds the failure
         * percentage outlier detectors threshold for locally originated failures.
         * Defaults to 0%.
         * Note: this value is only considered if {@code splitExternalLocalOriginErrors()} is true.
         * @param enforcingFailurePercentageLocalOrigin the probability in percentage that a host will be marked as
         *                                              unhealthy when a host exceeds the failure percentage outlier
         *                                              detectors threshold for locally originated failures.
         * @return {@code this}.
         */
        public Builder enforcingFailurePercentageLocalOrigin(final int enforcingFailurePercentageLocalOrigin) {
            ensureNonNegative(enforcingFailurePercentageLocalOrigin, "enforcingFailurePercentageLocalOrigin");
            this.enforcingFailurePercentageLocalOrigin = enforcingFailurePercentageLocalOrigin;
            return this;
        }

        /**
         * Set the minimum number of hosts required to perform the failure percentage outlier detector analysis.
         * Defaults to 5.
         * @param failurePercentageMinimumHosts the minimum number of hosts required to perform the failure percentage
         *                                      outlier detector analysis.
         * @return {@code this}.
         */
        public Builder failurePercentageMinimumHosts(final int failurePercentageMinimumHosts) {
            ensureNonNegative(failurePercentageMinimumHosts, "failurePercentageMinimumHosts");
            this.failurePercentageMinimumHosts = failurePercentageMinimumHosts;
            return this;
        }

        /**
         * Set the minimum number of requests in an outlier detector interval required to include it in the failure
         * percentage outlier detector analysis.
         * Defaults to 50.
         * @param failurePercentageRequestVolume the minimum number of requests in an outlier detector interval required
         *                                       to include it in the failure percentage outlier detector analysis.
         * @return {@code this}.
         */
        public Builder failurePercentageRequestVolume(final int failurePercentageRequestVolume) {
            ensurePositive(failurePercentageRequestVolume, "failurePercentageRequestVolume");
            this.failurePercentageRequestVolume = failurePercentageRequestVolume;
            return this;
        }

        /**
         * Set the maximum amount of time a host can be ejected regardless of the number of consecutive ejections.
         * Defaults to 300 seconds.
         * @param maxEjectionTime the maximum amount of time a host can be ejected regardless of the number of
         *                        consecutive ejections.
         * @return {@code this}.
         */
        public Builder maxEjectionTime(final Duration maxEjectionTime) {
            this.maxEjectionTime = requireNonNull(maxEjectionTime, "maxEjectionTime");
            ensurePositive(maxEjectionTime.toNanos(), "maxEjectionTime");
            return this;
        }

        /**
         * Set the maximum amount of jitter to add to the ejection time.
         * An additional amount of 'jitter' is added to the ejection time to prevent connection storms if multiple hosts
         * are ejected at the time.
         * Defaults to 0 seconds.
         * @param maxEjectionTimeJitter the maximum amount of jitter to add to the ejection time.
         * @return {@code this}.
         */
        public Builder maxEjectionTimeJitter(final Duration maxEjectionTimeJitter) {
            this.maxEjectionTimeJitter = requireNonNull(maxEjectionTimeJitter, "maxEjectionTimeJitter");
            ensureNonNegative(maxEjectionTimeJitter.toNanos(), "maxEjectionTimeJitter");
            return this;
        }

        /**
         * Set whether to un-eject a host that has had a successful active health check event to be revived regardless
         * of the remaining ejection time.
         * Defaults to true.
         * @param successfulActiveHealthCheckUnejectHost whether to un-eject a host that has had a successful active
         *                                               health check event to be revived regardless of the remaining
         *                                               ejection time.
         * @return {@code this}.
         */
        public Builder successfulActiveHealthCheckUnejectHost(final boolean successfulActiveHealthCheckUnejectHost) {
            this.successfulActiveHealthCheckUnejectHost = successfulActiveHealthCheckUnejectHost;
            return this;
        }
    }

    static boolean enforcing(int enforcingPercentage) {
        return enforcingPercentage >= 100 || ThreadLocalRandom.current().nextInt(100) <= enforcingPercentage;
    }
}
