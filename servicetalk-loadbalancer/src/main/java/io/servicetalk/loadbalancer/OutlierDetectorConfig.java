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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.utils.internal.DurationUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_JITTER;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.HealthCheckConfig.validateHealthCheckIntervals;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * XDS outlier detector configuration.
 * <p>
 * See the <a href="https://www.envoyproxy.io/docs/envoy/v1.29.0/api-v3/config/cluster/v3/outlier_detection.proto#envoy-v3-api-msg-config-cluster-v3-outlierdetection">Envoy docs</a>
 * for the official OutlierDetector configuration definition.
 */
public final class OutlierDetectorConfig {

    static final OutlierDetectorConfig DEFAULT_CONFIG = new Builder().build();

    // ServiceTalk specific settings
    private final Duration ewmaHalfLife;
    private final int ewmaCancellationPenalty;
    private final int ewmaErrorPenalty;
    private final int concurrentRequestPenalty;
    private final boolean cancellationIsError;
    private final int failedConnectionsThreshold;
    private final Duration failureDetectorIntervalJitter;
    private final Duration serviceDiscoveryResubscribeInterval;
    private final Duration serviceDiscoveryResubscribeJitter;

    // xDS defined settings
    private final int consecutive5xx;
    private final Duration failureDetectorInterval;
    private final Duration baseEjectionTime;
    private final Duration ejectionTimeJitter;
    private final int maxEjectionPercentage;
    private final int enforcingConsecutive5xx;
    private final int enforcingSuccessRate;
    private final int successRateMinimumHosts;
    private final int successRateRequestVolume;
    private final int successRateStdevFactor;
    private final int failurePercentageThreshold;
    private final int enforcingFailurePercentage;
    private final int failurePercentageMinimumHosts;
    private final int failurePercentageRequestVolume;
    private final Duration maxEjectionTime;

    private OutlierDetectorConfig(final Duration ewmaHalfLife, final int ewmaCancellationPenalty, final int ewmaErrorPenalty,
                          final int concurrentRequestPenalty, final boolean cancellationIsError, int failedConnectionsThreshold,
                          final Duration failureDetectorIntervalJitter,
                          final Duration serviceDiscoveryResubscribeInterval, final Duration serviceDiscoveryResubscribeJitter,
                          // true xDS settings
                          final int consecutive5xx, final Duration failureDetectorInterval, final Duration baseEjectionTime,
                          final int maxEjectionPercentage, final int enforcingConsecutive5xx,
                          final int enforcingSuccessRate, final int successRateMinimumHosts,
                          final int successRateRequestVolume, final int successRateStdevFactor,
                          final int failurePercentageThreshold, final int enforcingFailurePercentage,
                          final int failurePercentageMinimumHosts, final int failurePercentageRequestVolume,
                          final Duration maxEjectionTime, final Duration ejectionTimeJitter) {
        this.ewmaHalfLife = ewmaHalfLife;
        this.ewmaCancellationPenalty = ensureNonNegative(ewmaCancellationPenalty, "ewmaCancellationPenalty");
        this.ewmaErrorPenalty = ensureNonNegative(ewmaErrorPenalty, "ewmaErrorPenalty");
        this.concurrentRequestPenalty = ensureNonNegative(concurrentRequestPenalty, "concurrentRequestPenalty");
        this.cancellationIsError = cancellationIsError;
        this.failedConnectionsThreshold = failedConnectionsThreshold;
        this.failureDetectorIntervalJitter = failureDetectorIntervalJitter;
        this.serviceDiscoveryResubscribeInterval = serviceDiscoveryResubscribeInterval;
        this.serviceDiscoveryResubscribeJitter = serviceDiscoveryResubscribeJitter;
        // xDS settings.
        this.consecutive5xx = consecutive5xx;
        this.failureDetectorInterval = failureDetectorInterval;
        this.baseEjectionTime = baseEjectionTime;
        this.ejectionTimeJitter = ejectionTimeJitter;
        this.maxEjectionPercentage = maxEjectionPercentage;
        this.enforcingConsecutive5xx = enforcingConsecutive5xx;
        this.enforcingSuccessRate = enforcingSuccessRate;
        this.successRateMinimumHosts = successRateMinimumHosts;
        this.successRateRequestVolume = successRateRequestVolume;
        this.successRateStdevFactor = successRateStdevFactor;
        this.failurePercentageThreshold = failurePercentageThreshold;
        this.enforcingFailurePercentage = enforcingFailurePercentage;
        this.failurePercentageMinimumHosts = failurePercentageMinimumHosts;
        this.failurePercentageRequestVolume = failurePercentageRequestVolume;
        this.maxEjectionTime = maxEjectionTime;
    }

    /**
     * The Exponentially Weighted Moving Average (EWMA) half-life.
     * <p>
     * In the context of an exponentially weighted moving average, the half-life means the time during which
     * historical data has the same weight as a new sample.
     *
     * @return the Exponentially Weighted Moving Average (EWMA) half-life.
     */
    public Duration ewmaHalfLife() {
        return ewmaHalfLife;
    }

    /**
     * The penalty factor for local cancellation of requests.
     * <p>
     * The latency of the cancelled request is multiplied by the provided penalty before incorporating it into the EWMA.
     *
     * @return the penalty factor for local cancellation of requests.
     */
    public int ewmaCancellationPenalty() {
        return ewmaCancellationPenalty;
    }

    /**
     * Determines whether a cancellation is considered to be an error.
     *
     * @return whether a cancellation is considered to be an error.
     */
    public boolean cancellationIsError() {
        return cancellationIsError;
    }

    /**
     * The penalty factor for requests that were classified as an error.
     * <p>
     * The latency of the failed request is multiplied by the provided penalty before incorporating it into the EWMA.
     *
     * @return the penalty factor for requests that were classified as an error.
     */
    public int ewmaErrorPenalty() {
        return ewmaErrorPenalty;
    }

    /**
     * The penalty factory to apply to concurrent requests.
     * <p>
     * The EWMA penalty to apply to endpoints when there are concurrent requests. By penalizing endpoints with
     * concurrent load the traffic distribution will be smoother for algorithms that consider load metrics.
     *
     * @return the penalty factory to use for concurrent load.
     */
    public int concurrentRequestPenalty() {
        return concurrentRequestPenalty;
    }

    /**
     * The threshold for consecutive connection failures to a host.
     *
     * @return the threshold for consecutive connection failures to a host.
     * @see Builder#failedConnectionsThreshold(int)
     */
    public int failedConnectionsThreshold() {
        return failedConnectionsThreshold;
    }

    /**
     * The jitter used along with the configured interval to determine duration between outlier detector checks.
     *
     * @return the jitter used along with the configured interval to determine duration between outlier detector checks.
     * @see #failureDetectorInterval()
     * @see Builder#failureDetectorInterval(Duration, Duration)
     */
    public Duration failureDetectorIntervalJitter() {
        return failureDetectorIntervalJitter;
    }

    /**
     * The interval between service discovery resubscribes.
     *
     * @return the interval between service discovery resubscribes.
     * @see #serviceDiscoveryResubscribeJitter()
     * @see Builder#serviceDiscoveryResubscribeInterval(Duration, Duration)
     */
    public Duration serviceDiscoveryResubscribeInterval() {
        return serviceDiscoveryResubscribeInterval;
    }

    /**
     * The jitter to use along with the service discovery resubscribe interval.
     *
     * @return the jitter to use along with the service discovery resubscribe interval.
     * @see #serviceDiscoveryResubscribeInterval()
     * @see Builder#serviceDiscoveryResubscribeInterval(Duration, Duration)
     */
    public Duration serviceDiscoveryResubscribeJitter() {
        return serviceDiscoveryResubscribeJitter;
    }

    /**
     * The number of consecutive failures before the attempt to suspect the host.
     *
     * @return the number of consecutive failures before the attempt to suspect the host.
     */
    public int consecutive5xx() {
        return consecutive5xx;
    }

    /**
     * The interval on which to run failure detectors.
     * <p>
     * Failure percentage and success rate outlier detectors perform periodic scans to detect outliers. Active
     * revival mechanisms such as the layer-4 connectivity detector also use this interval to perform their periodic
     * health check to see if a host can be considered revived.
     *
     * @return the interval on which to run failure percentage and success rate failure detectors.
     */
    public Duration failureDetectorInterval() {
        return failureDetectorInterval;
    }

    /**
     * The base ejection time.
     * <p>
     * The base ejection time is multiplied by the number of consecutive times the host has been ejected to get the
     * total ejection time, capped by the {@link #maxEjectionTime()}.
     *
     * @return the base ejection time.
     * @see #ejectionTimeJitter()
     */
    public Duration baseEjectionTime() {
        return baseEjectionTime;
    }

    /**
     * The maximum percentage of hosts that can be ejected due to outlier detection.
     *
     * @return the maximum percentage of hosts that can be ejected due to outlier detection.
     */
    public int maxEjectionPercentage() {
        return maxEjectionPercentage;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host reaches the
     * {@link #consecutive5xx()} threshold.
     *
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingConsecutive5xx() {
        return enforcingConsecutive5xx;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the success rate
     * outlier detectors threshold.
     *
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingSuccessRate() {
        return enforcingSuccessRate;
    }

    /**
     * The minimum number of hosts required to perform the success rate outlier detector analysis.
     *
     * @return the minimum number of hosts required to perform the success rate outlier detector analysis.
     */
    public int successRateMinimumHosts() {
        return successRateMinimumHosts;
    }

    /**
     * The minimum number of requests in an outlier detector interval required to include it in the success rate
     * outlier detector analysis.
     *
     * @return the minimum number of request required.
     */
    public int successRateRequestVolume() {
        return successRateRequestVolume;
    }

    /**
     * The value divided by 1000 and then multiplied against the success rate standard deviation which sets the
     * threshold for ejection in the success rate outlier detector.
     *
     * @return the stdev factor divided by 1000 used to determine the statistical outliers.
     */
    public int successRateStdevFactor() {
        return successRateStdevFactor;
    }

    /**
     * The failure threshold in percentage for ejecting a host.
     *
     * @return the failure threshold in percentage for ejecting a host.
     */
    public int failurePercentageThreshold() {
        return failurePercentageThreshold;
    }

    /**
     * The probability in percentage that a host will be marked as unhealthy when a host exceeds the failure percentage
     * outlier detectors threshold.
     *
     * @return the probability with which the host should be marked as unhealthy.
     */
    public int enforcingFailurePercentage() {
        return enforcingFailurePercentage;
    }

    /**
     * The minimum number of hosts required to perform the failure percentage outlier detector analysis.
     *
     * @return the minimum number of hosts required to perform the failure percentage outlier detector analysis.
     */
    public int failurePercentageMinimumHosts() {
        return failurePercentageMinimumHosts;
    }

    /**
     * The minimum number of requests in an outlier detector interval required to include it in the failure percentage
     * outlier detector analysis.
     *
     * @return the minimum number of request required.
     */
    public int failurePercentageRequestVolume() {
        return failurePercentageRequestVolume;
    }

    /**
     * The maximum amount of time a host can be ejected regardless of the number of consecutive ejections.
     *
     * @return the maximum amount of time a host can be ejected.
     */
    public Duration maxEjectionTime() {
        return maxEjectionTime;
    }

    /**
     * The amount of jitter to add to the ejection time.
     * <p>
     * An additional amount of 'jitter' is added to the ejection time to prevent connection storms if multiple hosts
     * are ejected at the time.
     *
     * @return the amount of jitter to add to the ejection time.
     * @see #baseEjectionTime()
     */
    public Duration ejectionTimeJitter() {
        return ejectionTimeJitter;
    }

    @Override
    public String toString() {
        return "OutlierDetectorConfig{" +
                "ewmaHalfLife=" + ewmaHalfLife +
                ", ewmaCancellationPenalty=" + ewmaCancellationPenalty +
                ", ewmaErrorPenalty=" + ewmaErrorPenalty +
                ", cancellationIsError=" + cancellationIsError +
                ", failedConnectionsThreshold=" + failedConnectionsThreshold +
                ", failureDetectorIntervalJitter=" + failureDetectorIntervalJitter +
                ", serviceDiscoveryResubscribeInterval=" + serviceDiscoveryResubscribeInterval +
                ", serviceDiscoveryResubscribeJitter=" + serviceDiscoveryResubscribeJitter +
                ", consecutive5xx=" + consecutive5xx +
                ", failureDetectorInterval=" + failureDetectorInterval +
                ", baseEjectionTime=" + baseEjectionTime +
                ", ejectionTimeJitter=" + ejectionTimeJitter +
                ", maxEjectionPercentage=" + maxEjectionPercentage +
                ", enforcingConsecutive5xx=" + enforcingConsecutive5xx +
                ", enforcingSuccessRate=" + enforcingSuccessRate +
                ", successRateMinimumHosts=" + successRateMinimumHosts +
                ", successRateRequestVolume=" + successRateRequestVolume +
                ", successRateStdevFactor=" + successRateStdevFactor +
                ", failurePercentageThreshold=" + failurePercentageThreshold +
                ", enforcingFailurePercentage=" + enforcingFailurePercentage +
                ", failurePercentageMinimumHosts=" + failurePercentageMinimumHosts +
                ", failurePercentageRequestVolume=" + failurePercentageRequestVolume +
                ", maxEjectionTime=" + maxEjectionTime +
                '}';
    }

    /**
     * A builder for {@link OutlierDetectorConfig} instances.
     */
    public static final class Builder {

        // ServiceTalk specific settings.
        static final Duration DEFAULT_EWMA_HALF_LIFE = Duration.ofSeconds(10);
        static final int DEFAULT_CANCEL_PENALTY = 5;
        static final int DEFAULT_ERROR_PENALTY = 10;
        static final int DEFAULT_CONCURRENT_REQUEST_PENALTY = 1;
        private boolean cancellationIsError = true;

        // Default xDS outlier detector settings.
        private static final int DEFAULT_CONSECUTIVE_5XX = 5;
        private static final Duration DEFAULT_FAILURE_DETECTOR_INTERVAL = ofSeconds(10);
        private static final Duration DEFAULT_BASE_EJECTION_TIME = ofSeconds(30);
        private static final int DEFAULT_MAX_EJECTION_PERCENTAGE = 10;
        private static final int DEFAULT_ENFORCING_CONSECUTIVE_5XX = 100;
        private static final int DEFAULT_ENFORCING_SUCCESS_RATE = 100;
        private static final int DEFAULT_SUCCESS_RATE_MINIMUM_HOSTS = 5;
        private static final int DEFAULT_SUCCESS_RATE_REQUEST_VOLUME = 100;
        private static final int DEFAULT_SUCCESS_RATE_STDEV_FACTOR = 1900;
        private static final int DEFAULT_FAILURE_PERCENTAGE_THRESHOLD = 85;
        private static final int DEFAULT_ENFORCING_FAILURE_PERCENTAGE = 0;
        private static final int DEFAULT_FAILURE_PERCENTAGE_MINIMUM_HOSTS = 5;
        private static final int DEFAULT_FAILURE_PERCENTAGE_REQUEST_VOLUME = 50;
        private static final Duration DEFAULT_MAX_EJECTION_TIME = ofSeconds(300);

        // Non-xDS builder settings
        private Duration ewmaHalfLife = DEFAULT_EWMA_HALF_LIFE;
        private int ewmaCancellationPenalty = DEFAULT_CANCEL_PENALTY;
        private int ewmaErrorPenalty = DEFAULT_ERROR_PENALTY;
        private int concurrentRequestPenalty = DEFAULT_CONCURRENT_REQUEST_PENALTY;
        private int failedConnectionsThreshold = DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
        private Duration intervalJitter = DEFAULT_HEALTH_CHECK_JITTER;
        private Duration serviceDiscoveryResubscribeInterval = DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
        private Duration serviceDiscoveryResubscribeJitter = DEFAULT_HEALTH_CHECK_JITTER;

        // True xDS settings
        private int consecutive5xx = DEFAULT_CONSECUTIVE_5XX;

        // Note that this value is shared by both the L4 and xDS health checking systems, but they had different
        // default values (5s for L4 and 10s for xDS). We've decided to use the xDS default since it is viable for
        // both whereas choosing 5s would necessitate changing a lot of related xDS settings such as min request
        // volume, etc.
        private Duration failureDetectorInterval = DEFAULT_FAILURE_DETECTOR_INTERVAL;

        private Duration baseEjectionTime = DEFAULT_BASE_EJECTION_TIME;
        private int maxEjectionPercentage = DEFAULT_MAX_EJECTION_PERCENTAGE;
        private int enforcingConsecutive5xx = DEFAULT_ENFORCING_CONSECUTIVE_5XX;
        private int enforcingSuccessRate = DEFAULT_ENFORCING_SUCCESS_RATE;
        private int successRateMinimumHosts = DEFAULT_SUCCESS_RATE_MINIMUM_HOSTS;
        private int successRateRequestVolume = DEFAULT_SUCCESS_RATE_REQUEST_VOLUME;
        private int successRateStdevFactor = DEFAULT_SUCCESS_RATE_STDEV_FACTOR;
        private int failurePercentageThreshold = DEFAULT_FAILURE_PERCENTAGE_THRESHOLD;
        private int enforcingFailurePercentage = DEFAULT_ENFORCING_FAILURE_PERCENTAGE;
        private int failurePercentageMinimumHosts = DEFAULT_FAILURE_PERCENTAGE_MINIMUM_HOSTS;
        private int failurePercentageRequestVolume = DEFAULT_FAILURE_PERCENTAGE_REQUEST_VOLUME;
        private Duration maxEjectionTime = DEFAULT_MAX_EJECTION_TIME;

        // Note that xDS defines its default jitter as 0 seconds.
        private Duration ejectionTimeJitter = DEFAULT_HEALTH_CHECK_JITTER;

        /**
         * Construct a new builder initialized with the values of an existing {@link OutlierDetectorConfig}.
         * @param outlierDetectorConfig the configuration to use as the initial values for this builder.
         */
        Builder(final OutlierDetectorConfig outlierDetectorConfig) {
            this.ewmaHalfLife = outlierDetectorConfig.ewmaHalfLife;
            this.failedConnectionsThreshold = outlierDetectorConfig.failedConnectionsThreshold;
            this.intervalJitter = outlierDetectorConfig.failureDetectorIntervalJitter;
            this.serviceDiscoveryResubscribeInterval = outlierDetectorConfig.serviceDiscoveryResubscribeInterval;
            this.serviceDiscoveryResubscribeJitter = outlierDetectorConfig.serviceDiscoveryResubscribeJitter;
            this.consecutive5xx = outlierDetectorConfig.consecutive5xx;
            this.failureDetectorInterval = outlierDetectorConfig.failureDetectorInterval;
            this.baseEjectionTime = outlierDetectorConfig.baseEjectionTime;
            this.maxEjectionPercentage = outlierDetectorConfig.maxEjectionPercentage;
            this.enforcingConsecutive5xx = outlierDetectorConfig.enforcingConsecutive5xx;
            this.enforcingSuccessRate = outlierDetectorConfig.enforcingSuccessRate;
            this.successRateMinimumHosts = outlierDetectorConfig.successRateMinimumHosts;
            this.successRateRequestVolume = outlierDetectorConfig.successRateRequestVolume;
            this.successRateStdevFactor = outlierDetectorConfig.successRateStdevFactor;
            this.failurePercentageThreshold = outlierDetectorConfig.failurePercentageThreshold;
            this.enforcingFailurePercentage = outlierDetectorConfig.enforcingFailurePercentage;
            this.failurePercentageMinimumHosts = outlierDetectorConfig.failurePercentageMinimumHosts;
            this.failurePercentageRequestVolume = outlierDetectorConfig.failurePercentageRequestVolume;
            this.maxEjectionTime = outlierDetectorConfig.maxEjectionTime;
            this.ejectionTimeJitter = outlierDetectorConfig.ejectionTimeJitter;
        }

        /**
         * Construct a new builder using the default initial values.
         */
        public Builder() {
            // uses the defaults
        }

        /**
         * Build the OutlierDetectorConfig.
         *
         * @return the OutlierDetectorConfig.
         */
        public OutlierDetectorConfig build() {
            return new OutlierDetectorConfig(ewmaHalfLife, ewmaCancellationPenalty, ewmaErrorPenalty,
                    concurrentRequestPenalty, cancellationIsError, failedConnectionsThreshold, intervalJitter,
                    serviceDiscoveryResubscribeInterval, serviceDiscoveryResubscribeJitter,
                    // xDS settings
                    consecutive5xx, failureDetectorInterval, baseEjectionTime,
                    maxEjectionPercentage, enforcingConsecutive5xx,
                    enforcingSuccessRate, successRateMinimumHosts,
                    successRateRequestVolume, successRateStdevFactor,
                    failurePercentageThreshold, enforcingFailurePercentage,
                    failurePercentageMinimumHosts, failurePercentageRequestVolume,
                    maxEjectionTime, ejectionTimeJitter);
        }

        /**
         * Set the Exponentially Weighted Moving Average (EWMA) half-life.
         * <p>
         * In the context of an exponentially weighted moving average, the half-life means the time during which
         * historical data has the same weight as a new sample.
         * <p>
         * Defaults to 10 seconds.
         *
         * @param ewmaHalfLife the half-life for latency data.
         * @return {@code this}
         */
        public Builder ewmaHalfLife(final Duration ewmaHalfLife) {
            this.ewmaHalfLife = DurationUtils.ensureNonNegative(ewmaHalfLife, "ewmaHalfLife");
            return this;
        }

        /**
         * Set the penalty factor for local cancellation of requests.
         * <p>
         * The latency of the cancelled request is multiplied by the provided penalty before incorporating it into the
         * EWMA.
         * <p>
         * Defaults to {@value DEFAULT_CANCEL_PENALTY}.
         *
         * @param ewmaCancellationPenalty the penalty factor for local cancellation of requests.
         * @return {@code this}
         */
        public Builder ewmaCancellationPenalty(final int ewmaCancellationPenalty) {
            this.ewmaCancellationPenalty = ensureNonNegative(ewmaCancellationPenalty, "ewmaCancellationPenalty");
            return this;
        }

        /**
         * Set the penalty factor for requests that were classified as an error.
         * <p>
         * The latency of the failed request is multiplied by the provided penalty before incorporating it into the
         * EWMA.
         * <p>
         * Defaults to {@value DEFAULT_ERROR_PENALTY}.
         *
         * @param ewmaErrorPenalty the penalty factor for requests that were classified as an error.
         * @return {@code this}
         * @see OutlierDetectorConfig#ewmaErrorPenalty()
         */
        public Builder ewmaErrorPenalty(final int ewmaErrorPenalty) {
            this.ewmaErrorPenalty = ensureNonNegative(ewmaErrorPenalty, "ewmaErrorPenalty");
            return this;
        }

        /**
         * Set whether a cancellation is considered to be an error by the outlier detector.
         *
         * @param cancellationIsError whether a cancellation is considered to be an error by the outlier detector.
         * @return {@code this}
         */
        public Builder cancellationIsError(final boolean cancellationIsError) {
            this.cancellationIsError = cancellationIsError;
            return this;
        }

        /**
         * Set the penalty factory to apply to concurrent requests.
         * <p>
         * The EWMA penalty to apply to endpoints when there are concurrent requests. By penalizing endpoints with
         * concurrent load the traffic distribution will be more fair for algorithms that consider load metrics.
         * Larger penalties will favor a more even request distribution while lower penalties will bias traffic toward
         * endpoints with better performance. A value of 0 disables the penalty, 1 is an intermediate value, and larger
         * values such as 10 or more will strongly favor fairness over performance.
         * <p>
         * Defaults to {@value DEFAULT_CONCURRENT_REQUEST_PENALTY}.
         *
         * @param ewmaConcurrentRequestPenalty the penalty factory to apply for concurrent load.
         * @return {@code this}
         */
        public Builder ewmaConcurrentRequestPenalty(final int ewmaConcurrentRequestPenalty) {
            this.concurrentRequestPenalty = ensureNonNegative(
                    ewmaConcurrentRequestPenalty, "ewmaConcurrentRequestPenalty");
            return this;
        }

        /**
         * Configure an interval for re-subscribing to the original events stream in case all existing hosts become
         * unhealthy.
         * <p>
         * In situations when there is a latency between {@link ServiceDiscoverer} propagating the updated state and all
         * known hosts become unhealthy, which could happen due to intermediate caching layers, re-subscribing to the
         * events stream can help to exit from a dead state.
         * <p>
         * Note: setting the interval to {@code Duration.ofNanos(Long.MAX_VALUE)} will effectively disable health check
         * resubscribes.
         *
         * @param interval interval at which re-subscribes will be scheduled.
         * @param jitter the amount of jitter to apply to each re-subscribe {@code interval}.
         * @return {@code this}.
         */
        public Builder serviceDiscoveryResubscribeInterval(final Duration interval, final Duration jitter) {
            validateHealthCheckIntervals(interval, jitter);
            this.serviceDiscoveryResubscribeInterval = interval;
            this.serviceDiscoveryResubscribeJitter = jitter;
            return this;
        }

        /**
         * Configure a threshold for consecutive connection failures to a host.
         * <p>
         * When the {@link LoadBalancer} consecutively fails to open connections in the amount greater or equal to the
         * specified value, the host will be marked as unhealthy and connection establishment will take place in the
         * background repeatedly on the {@link #failureDetectorInterval()} (with jitter
         * {@link #failureDetectorIntervalJitter()}) until a connection is established. During that time, the host will
         * not take part in load balancing selection.
         * <p>
         * Use a negative value of the argument to disable health checking.
         *
         * @param failedConnectionsThreshold number of consecutive connection failures to consider a host unhealthy and
         *                                   eligible for background health checking. Use negative value to disable the
         *                                   health checking mechanism.
         * @return {@code this}.
         */
        public Builder failedConnectionsThreshold(int failedConnectionsThreshold) {
            this.failedConnectionsThreshold = failedConnectionsThreshold;
            if (failedConnectionsThreshold == 0) {
                throw new IllegalArgumentException("Not valid value: 0 (expected: positive or negative)");
            }
            return this;
        }

        /**
         * Set the threshold for consecutive failures before a host is ejected.
         * <p>
         * Defaults to {@value DEFAULT_CONSECUTIVE_5XX}.
         *
         * @param consecutive5xx the threshold for consecutive failures before a host is ejected.
         * @return {@code this}
         */
        public Builder consecutive5xx(final int consecutive5xx) {
            ensurePositive(consecutive5xx, "consecutive5xx");
            this.consecutive5xx = consecutive5xx;
            return this;
        }

        /**
         * Set the failure detector interval on which the outlier detector will perform periodic tasks.
         * <p>
         * These tasks can include detection of outlier or the active revival checks.
         * <p>
         * This method will also use either the default jitter or the provided interval, whichever is smaller.
         * <p>
         * Defaults to 10 second interval with 3 second jitter.
         *
         * @param interval the interval on which to run failure percentage and success rate failure detectors.
         * @return {@code this}
         */
        public Builder failureDetectorInterval(final Duration interval) {
            return failureDetectorInterval(interval, interval.compareTo(DEFAULT_HEALTH_CHECK_INTERVAL) < 0 ?
                    interval.dividedBy(2) : DEFAULT_HEALTH_CHECK_JITTER);
        }

        /**
         * Set the interval on which to run failure percentage and success rate failure detectors.
         * <p>
         * These tasks can include detection of outlier or the active revival checks.
         * <p>
         * Defaults to 10 second interval with 3 second jitter.
         *
         * @param interval the interval on which to run failure percentage and success rate failure detectors.
         * @param jitter the jitter of the time interval. The next interval will have a duration of
         *               [interval - jitter, interval + jitter].
         * @return {@code this}
         */
        public Builder failureDetectorInterval(final Duration interval, final Duration jitter) {
            validateHealthCheckIntervals(interval, jitter);
            this.failureDetectorInterval = interval;
            this.intervalJitter = jitter;
            return this;
        }

        /**
         * Set the base ejection time.
         * <p>
         * Defaults to 30 seconds.
         *
         * @param baseEjectionTime the base ejection time.
         * @return {@code this}.
         * @see #ejectionTimeJitter(Duration)
         */
        public Builder baseEjectionTime(final Duration baseEjectionTime) {
            this.baseEjectionTime = DurationUtils.ensurePositive(baseEjectionTime, "baseEjectionTime");
            return this;
        }

        /**
         * Set the ejection time jitter.
         * <p>
         * Defaults to 3 seconds.
         *
         * @param ejectionTimeJitter the jitter to add to the calculated ejection time.
         * @return {@code this}.
         * @see #baseEjectionTime(Duration)
         */
        public Builder ejectionTimeJitter(final Duration ejectionTimeJitter) {
            this.ejectionTimeJitter = DurationUtils.ensureNonNegative(ejectionTimeJitter, "ejectionTimeJitter");
            return this;
        }

        /**
         * Set the maximum percentage of hosts that can be ejected due to outlier detection.
         * <p>
         * Defaults to {@value DEFAULT_MAX_EJECTION_PERCENTAGE} percent but at least one host will be allowed to be
         * ejected regardless of value.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_ENFORCING_CONSECUTIVE_5XX} percent.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_ENFORCING_SUCCESS_RATE} percent.
         *
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
         * Defaults to {@value DEFAULT_SUCCESS_RATE_MINIMUM_HOSTS}.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_SUCCESS_RATE_REQUEST_VOLUME}.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_SUCCESS_RATE_STDEV_FACTOR}.
         *
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
         * Set the failure threshold in percentage for ejecting a host.
         * <p>
         * Defaults to {@value DEFAULT_FAILURE_PERCENTAGE_THRESHOLD} percent.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_ENFORCING_FAILURE_PERCENTAGE} percent.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_FAILURE_PERCENTAGE_MINIMUM_HOSTS}.
         *
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
         * <p>
         * Defaults to {@value DEFAULT_FAILURE_PERCENTAGE_REQUEST_VOLUME}.
         *
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
         * <p>
         * Defaults to a max ejection time of 300 seconds and 0 seconds jitter.
         *
         * @param maxEjectionTime the maximum amount of time a host can be ejected regardless of the number of
         *                        consecutive ejections.
         * @return {@code this}.
         */
        public Builder maxEjectionTime(final Duration maxEjectionTime) {
            this.maxEjectionTime = DurationUtils.ensureNonNegative(maxEjectionTime, "maxEjectionTime");
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

    static boolean allDisabled(OutlierDetectorConfig outlierDetectorConfig) {
        return outlierDetectorConfig.failedConnectionsThreshold() < 0 &&
                xDSDisabled(outlierDetectorConfig);
    }

    static boolean xDSDisabled(OutlierDetectorConfig outlierDetectorConfig) {
        return outlierDetectorConfig.enforcingConsecutive5xx() == 0 &&
                outlierDetectorConfig.enforcingSuccessRate() == 0 &&
                outlierDetectorConfig.enforcingFailurePercentage() == 0;
    }
}
