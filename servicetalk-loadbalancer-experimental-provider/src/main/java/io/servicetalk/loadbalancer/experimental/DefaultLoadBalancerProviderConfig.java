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
package io.servicetalk.loadbalancer.experimental;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.loadbalancer.LoadBalancerPolicies;
import io.servicetalk.loadbalancer.LoadBalancingPolicy;
import io.servicetalk.loadbalancer.OutlierDetectorConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

final class DefaultLoadBalancerProviderConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpLoadBalancerProvider.class);

    private enum LBPolicy {
        P2C,
        RoundRobin
    }

    // This prefix should be applied to all individual properties.
    private static final String PROPERTY_PREFIX = "io.servicetalk.loadbalancer.experimental.";

    private static final String PROP_CLIENTS_ENABLED_FOR = "clientsEnabledFor";

    private static final String PROP_FAILED_CONNECTIONS_THRESHOLD = "healthCheckFailedConnectionsThreshold";
    private static final String PROP_LOAD_BALANCING_POLICY = "policy";
    private static final String PROP_EWMA_HALF_LIFE_MS = "ewmaHalfLifeMs";
    private static final String PROP_CONSECUTIVE_5XX = "consecutive5xx";
    private static final String PROP_INTERVAL_MS = "intervalMs";
    private static final String PROP_BASE_EJECTION_TIME_MS = "baseEjectionTimeMs";
    private static final String PROP_MAX_EJECTION_PERCENT = "maxEjectionPercent";
    private static final String PROP_ENFORCING_CONSECUTIVE_5XX = "enforcingConsecutive5xx";
    private static final String PROP_ENFORCING_SUCCESS_RATE = "enforcingSuccessRate";
    private static final String PROP_SUCCESS_RATE_MIN_HOSTS = "successRateMinimumHosts";
    private static final String PROP_SUCCESS_RATE_REQUEST_VOL = "successRateRequestVolume";
    private static final String PROP_SUCCESS_RATE_STDEV_FACTOR = "successRateStdevFactor";
    private static final String PROP_FAILURE_PERCENTAGE_THRESHOLD = "failurePercentageThreshold";
    private static final String PROP_ENFORCING_FAILURE_PERCENTAGE = "enforcingFailurePercentage";
    private static final String PROP_FAILURE_PERCENTAGE_MIN_HOSTS = "failurePercentageMinimumHosts";
    private static final String PROP_FAILURE_PERCENTAGE_REQUEST_VOL = "failurePercentageRequestVolume";
    private static final String PROP_MAX_EJECTION_TIME_MS = "maxEjectionTimeMs";
    private static final String PROP_EJECTION_TIME_JITTER_MS = "ejectionTimeJitterMs";

    static final DefaultLoadBalancerProviderConfig INSTANCE = new DefaultLoadBalancerProviderConfig();

    private final String rawClientsEnabledFor;
    private final Set<String> clientsEnabledFor;
    private final int failedConnectionsThreshold;
    private final LBPolicy lbPolicy;
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
    private final int failurePercentageThreshold;
    private final int enforcingFailurePercentage;
    private final int failurePercentageMinimumHosts;
    private final int failurePercentageRequestVolume;
    private final Duration maxEjectionTime;
    private final Duration ejectionTimeJitter;

    private DefaultLoadBalancerProviderConfig() {
        rawClientsEnabledFor = getString(PROP_CLIENTS_ENABLED_FOR, "").trim();
        clientsEnabledFor = getClientsEnabledFor(rawClientsEnabledFor);
        failedConnectionsThreshold = getInt(PROP_FAILED_CONNECTIONS_THRESHOLD, 5 /*ST default*/);
        lbPolicy = getLBPolicy();
        ewmaHalfLife = ofMillis(getLong(PROP_EWMA_HALF_LIFE_MS, ofSeconds(10).toMillis()));
        consecutive5xx = getInt(PROP_CONSECUTIVE_5XX, 5);
        interval = ofMillis(getLong(PROP_INTERVAL_MS, ofSeconds(10).toMillis()));
        baseEjectionTime = ofMillis(getLong(PROP_BASE_EJECTION_TIME_MS, ofSeconds(30).toMillis()));
        maxEjectionPercentage = getInt(PROP_MAX_EJECTION_PERCENT, 20);
        enforcingConsecutive5xx = getInt(PROP_ENFORCING_CONSECUTIVE_5XX, 100);
        enforcingSuccessRate = getInt(PROP_ENFORCING_SUCCESS_RATE, 100);
        successRateMinimumHosts = getInt(PROP_SUCCESS_RATE_MIN_HOSTS, 5);
        successRateRequestVolume = getInt(PROP_SUCCESS_RATE_REQUEST_VOL, 100);
        successRateStdevFactor = getInt(PROP_SUCCESS_RATE_STDEV_FACTOR, 1900);
        failurePercentageThreshold = getInt(PROP_FAILURE_PERCENTAGE_THRESHOLD, 85);
        enforcingFailurePercentage = getInt(PROP_ENFORCING_FAILURE_PERCENTAGE, 0);
        failurePercentageMinimumHosts = getInt(PROP_FAILURE_PERCENTAGE_MIN_HOSTS, 5);
        failurePercentageRequestVolume = getInt(PROP_FAILURE_PERCENTAGE_REQUEST_VOL, 50);
        maxEjectionTime = ofMillis(getLong(PROP_MAX_EJECTION_TIME_MS, ofSeconds(90).toMillis()));
        ejectionTimeJitter = ofMillis(getLong(PROP_EJECTION_TIME_JITTER_MS, ZERO.toMillis()));
    }

    private LBPolicy getLBPolicy() {
        final String configuredLbName = getString(PROP_LOAD_BALANCING_POLICY, LBPolicy.P2C.name());
        if (configuredLbName.equalsIgnoreCase(LBPolicy.P2C.name())) {
            return LBPolicy.P2C;
        } else if (configuredLbName.equalsIgnoreCase(LBPolicy.RoundRobin.name())) {
            return LBPolicy.RoundRobin;
        } else {
            LOGGER.warn("Unrecognized load balancer policy name: {}. Defaulting to P2C.", configuredLbName);
            return LBPolicy.P2C;
        }
    }

    <U, C extends LoadBalancedConnection> LoadBalancingPolicy<U, C> getLoadBalancingPolicy() {
        return lbPolicy == LBPolicy.P2C ?
                LoadBalancerPolicies.p2c().build() : LoadBalancerPolicies.roundRobin().build();
    }

    boolean enabledForServiceName(String serviceName) {
        return "*".equals(rawClientsEnabledFor) || clientsEnabledFor.contains(serviceName);
    }

    OutlierDetectorConfig outlierDetectorConfig() {
        return new OutlierDetectorConfig.Builder()
                .failedConnectionsThreshold(failedConnectionsThreshold)
                .ewmaHalfLife(ewmaHalfLife)
                .consecutive5xx(consecutive5xx)
                .failureDetectorInterval(interval)
                .baseEjectionTime(baseEjectionTime)
                .ejectionTimeJitter(ejectionTimeJitter)
                .maxEjectionPercentage(maxEjectionPercentage)
                .enforcingConsecutive5xx(enforcingConsecutive5xx)
                .enforcingSuccessRate(enforcingSuccessRate)
                .successRateMinimumHosts(successRateMinimumHosts)
                .successRateRequestVolume(successRateRequestVolume)
                .successRateStdevFactor(successRateStdevFactor)
                .failurePercentageThreshold(failurePercentageThreshold)
                .enforcingFailurePercentage(enforcingFailurePercentage)
                .failurePercentageMinimumHosts(failurePercentageMinimumHosts)
                .failurePercentageRequestVolume(failurePercentageRequestVolume)
                .maxEjectionTime(maxEjectionTime)
                .build();
    }

    @Override
    public String toString() {
        return "ExperimentalOutlierDetectorConfig{" +
                "clientsEnabledFor=" + rawClientsEnabledFor +
                ", failedConnectionsThreshold=" + failedConnectionsThreshold +
                ", lbPolicy=" + lbPolicy +
                ", ewmaHalfLife=" + ewmaHalfLife +
                ", consecutive5xx=" + consecutive5xx +
                ", interval=" + interval +
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

    private static String getString(String name, String defaultValue) {
        return System.getProperty(PROPERTY_PREFIX + name, defaultValue);
    }

    private static long getLong(String name, long defaultValue) {
        String propertyValue = System.getProperty(PROPERTY_PREFIX + name);
        if (propertyValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(propertyValue.trim());
        } catch (Exception ex) {
            LOGGER.warn("Exception parsing property {} with value {} to an integral value. Using the default of {}.",
                    name, propertyValue, defaultValue, ex);
            return defaultValue;
        }
    }

    private static int getInt(String name, int defaultValue) {
        long value = getLong(name, defaultValue);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer overflow for value " + name + ": " + value);
        }
        return (int) value;
    }

    private static Set<String> getClientsEnabledFor(String propertyValue) {
        final Set<String> result = new HashSet<>();
        // if enabled for all there is no need to parse.
        if (!"*".equals(propertyValue)) {
            for (String serviceName : propertyValue.split(",")) {
                String trimmed = serviceName.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
