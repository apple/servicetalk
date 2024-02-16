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
 * See the <a href="https://www.envoyproxy.io/docs/envoy/v1.29.0/api-v3/config/cluster/v3/outlier_detection.proto#envoy-v3-api-msg-config-cluster-v3-outlierdetection">Envoy docs</a>
 * for the official OutlierDetector configuration definition.
 */
public final class OutlierDetectorConfig {

    private final Duration ewmaHalfLife;
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
    private final int failurePercentageThreshold;
    private final int enforcingFailurePercentage;
    private final int failurePercentageMinimumHosts;
    private final int failurePercentageRequestVolume;
    private final Duration maxEjectionTime;
    private final Duration maxEjectionTimeJitter;

    OutlierDetectorConfig(final Duration ewmaHalfLife,
                          final int consecutive5xx, final Duration interval, final Duration baseEjectionTime,
                          final int maxEjectionPercentage, final int enforcingConsecutive5xx,
                          final int enforcingSuccessRate, final int successRateMinimumHosts,
                          final int successRateRequestVolume, final int successRateStdevFactor,
                          final int consecutiveGatewayFailure, final int enforcingConsecutiveGatewayFailure,
                          final int failurePercentageThreshold, final int enforcingFailurePercentage,
                          final int failurePercentageMinimumHosts,
                          final int failurePercentageRequestVolume, final Duration maxEjectionTime,
                          final Duration maxEjectionTimeJitter) {
        this.ewmaHalfLife = requireNonNull(ewmaHalfLife, "ewmaHalfLife");
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
        this.failurePercentageThreshold = failurePercentageThreshold;
        this.enforcingFailurePercentage = enforcingFailurePercentage;
        this.failurePercentageMinimumHosts = failurePercentageMinimumHosts;
        this.failurePercentageRequestVolume = failurePercentageRequestVolume;
        this.maxEjectionTime = requireNonNull(maxEjectionTime, "maxEjectionTime");
        this.maxEjectionTimeJitter = requireNonNull(maxEjectionTimeJitter, "maxEjectionTimeJitter");
    }

    /**
     * The Exponentially Weighted Moving Average (EWMA) half-life.
     * In the context of an exponentially weighted moving average, the half-life means the time during which
     * historical data has the same weight as a new sample.
     * @return the Exponentially Weighted Moving Average (EWMA) half-life.
     */
    public Duration ewmaHalfLife() {
        return ewmaHalfLife;
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
     * The threshold for consecutive gateway failures before the host is ejected.
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
     * A builder for {@link OutlierDetectorConfig} instances.
     */
    public static final class Builder {
        private Duration ewmaHalfLife = Duration.ofSeconds(10);
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

        private int failurePercentageThreshold = 85;

        private int enforcingFailurePercentage;


        private int failurePercentageMinimumHosts = 5;

        private int failurePercentageRequestVolume = 50;

        private Duration maxEjectionTime = Duration.ofSeconds(300);

        private Duration maxEjectionTimeJitter = Duration.ZERO;

        /**
         * Build the OutlierDetectorConfig.
         * @return the OutlierDetectorConfig.
         */
        public OutlierDetectorConfig build() {
            return new OutlierDetectorConfig(ewmaHalfLife, consecutive5xx,
                    interval, baseEjectionTime,
                    maxEjectionPercentage, enforcingConsecutive5xx,
                    enforcingSuccessRate, successRateMinimumHosts,
                    successRateRequestVolume, successRateStdevFactor,
                    consecutiveGatewayFailure, enforcingConsecutiveGatewayFailure,
                    failurePercentageThreshold, enforcingFailurePercentage,
                    failurePercentageMinimumHosts, failurePercentageRequestVolume,
                    maxEjectionTime, maxEjectionTimeJitter);
        }

        /**
         * Set the Exponentially Weighted Moving Average (EWMA) half-life.
         * In the context of an exponentially weighted moving average, the half-life means the time during which
         * historical data has the same weight as a new sample.
         * Defaults to 10 seconds.
         * @param ewmaHalfLife the half-life for latency data.
         * @return {@code this}
         */
        public Builder ewmaHalfLife(final Duration ewmaHalfLife) {
            requireNonNull(ewmaHalfLife, "ewmaHalfLife");
            ensureNonNegative(ewmaHalfLife.toNanos(), "ewmaHalfLife");
            this.ewmaHalfLife = ewmaHalfLife;
            return this;
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
    }

    static boolean enforcing(int enforcingPercentage) {
        if (enforcingPercentage <= 0) {
            return false;
        }
        if (enforcingPercentage >= 100) {
            return true;
        }
        return enforcingPercentage >= ThreadLocalRandom.current().nextInt(100) + 1;
    }
}
