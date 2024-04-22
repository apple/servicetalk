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

import io.servicetalk.loadbalancer.LoadBalancingPolicy;
import io.servicetalk.loadbalancer.OutlierDetectorConfig;
import io.servicetalk.loadbalancer.P2CLoadBalancingPolicy;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

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
    private static final String PROP_LOAD_BALANCING_POLICY = "clientExperimentalLoadBalancerPolicy";
    private static final String PROP_EWMA_HALF_LIFE_MS = "experimentalLoadBalancerEwmaHalfLifeMs";
    private static final String PROP_CONSECUTIVE_5XX = "experimentalLoadBalancerConsecutive5xx";
    private static final String PROP_INTERVAL_MS = "experimentalLoadBalancerIntervalMs";
    private static final String PROP_BASE_EJECTION_TIME_MS = "experimentalLoadBalancerBaseEjectionTimeMs";
    private static final String PROP_MAX_EJECTION_PERCENT = "experimentalLoadBalancerMaxEjectionPercent";
    private static final String PROP_ENFORCING_CONSECUTIVE_5XX = "experimentalLoadBalancerEnforcingConsecutive5xx";
    private static final String PROP_ENFORCING_SUCCESS_RATE = "experimentalLoadBalancerEnforcingSuccessRate";
    private static final String PROP_SUCCESS_RATE_MIN_HOSTS = "experimentalLoadBalancerSuccessRateMinimumHosts";
    private static final String PROP_SUCCESS_RATE_REQUEST_VOL = "experimentalLoadBalancerSuccessRateRequestVolume";
    private static final String PROP_SUCCESS_RATE_STDEV_FACTOR = "experimentalLoadBalancerSuccessRateStdevFactor";
    private static final String PROP_FAILURE_PERCENTAGE_THRESHOLD =
            "experimentalLoadBalancerFailurePercentageThreshold";
    private static final String PROP_ENFORCING_FAILURE_PERCENTAGE =
            "experimentalLoadBalancerEnforcingFailurePercentage";
    private static final String PROP_FAILURE_PERCENTAGE_MIN_HOSTS =
            "experimentalLoadBalancerFailurePercentageMinimumHosts";
    private static final String PROP_FAILURE_PERCENTAGE_REQUEST_VOL =
            "experimentalLoadBalancerFailurePercentageRequestVolume";
    private static final String PROP_MAX_EJECTION_TIME_MS = "experimentalLoadBalancerMaxEjectionTimeMs";
    private static final String PROP_EJECTION_TIME_JITTER_MS = "experimentalLoadBalancerEjectionTimeJitterMs";

    static final DefaultLoadBalancerProviderConfig INSTANCE = new DefaultLoadBalancerProviderConfig();

    private final Properties properties;

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

    private final Map<String, String> fields;

    private DefaultLoadBalancerProviderConfig() {
        this(System.getProperties());
    }

    private DefaultLoadBalancerProviderConfig(Properties properties) {
        this.properties = requireNonNull(properties, "properties");
        rawClientsEnabledFor = getString(PROP_CLIENTS_ENABLED_FOR, "");
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

        fields = populateEventFields();
    }

    private Map<String, String> populateEventFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("clientsEnabledFor", String.valueOf(clientsEnabledFor));
        fields.put("failedConnectionsThreshold", String.valueOf(failedConnectionsThreshold));
        fields.put("loadBalancingPolicy", String.valueOf(lbPolicy));
        fields.put("ewmaHalfLifeMillis", String.valueOf(ewmaHalfLife.toMillis()));
        fields.put("consecutive5xx", String.valueOf(consecutive5xx));
        fields.put("intervalMillis", String.valueOf(interval.toMillis()));
        fields.put("baseEjectionTimeMillis", String.valueOf(baseEjectionTime.toMillis()));
        fields.put("ejectionTimeJitterMillis", String.valueOf(ejectionTimeJitter.toMillis()));
        fields.put("maxEjectionPercentage", String.valueOf(maxEjectionPercentage));
        fields.put("enforcingConsecutive5xx", String.valueOf(enforcingConsecutive5xx));
        fields.put("enforcingSuccessRate", String.valueOf(enforcingSuccessRate));
        fields.put("successRateMinimumHosts", String.valueOf(successRateMinimumHosts));
        fields.put("successRateRequestVolume", String.valueOf(successRateRequestVolume));
        fields.put("successRateStdevFactor", String.valueOf(successRateStdevFactor));
        fields.put("failurePercentageThreshold", String.valueOf(failurePercentageThreshold));
        fields.put("enforcingFailurePercentage", String.valueOf(enforcingFailurePercentage));
        fields.put("failurePercentageMinimumHosts", String.valueOf(failurePercentageMinimumHosts));
        fields.put("failurePercentageRequestVolume", String.valueOf(failurePercentageRequestVolume));
        fields.put("maxEjectionTimeMillis", String.valueOf(maxEjectionTime.toMillis()));
        return fields;
    }

    // TODO: what can we do with this?
    Map<String, String> getEventFields() {
        return fields;
    }

    private LBPolicy getLBPolicy() {
        return getString(PROP_LOAD_BALANCING_POLICY, "p2c")
                .equalsIgnoreCase(LBPolicy.P2C.name()) ? LBPolicy.P2C : LBPolicy.RoundRobin;
    }

    @Nullable
    private Set<String> getClientsEnabledFor(String propertyValue) {
        propertyValue = propertyValue.trim();
        final Set<String> result = new HashSet<>();
        // if enabled for all there is no need to parse.
        if (!"all".equals(propertyValue)) {
            for (String serviceName : propertyValue.split(",")) {
                serviceName = serviceName.trim();
                if (!serviceName.isEmpty()) {
                    result.add(serviceName);
                }
            }
        }
        return result;
    }

    <U, C extends LoadBalancedConnection> LoadBalancingPolicy<U, C> getLoadBalancingPolicy() {
        if (lbPolicy == lbPolicy.P2C) {
            return new P2CLoadBalancingPolicy.Builder().build();
        } else {
            return new RoundRobinLoadBalancingPolicy.Builder().build();
        }
    }

    boolean enabledForServiceName(String serviceName) {
        return clientsEnabledFor == null || clientsEnabledFor.contains(serviceName);
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
                "clientsEnabledFor=" + rawClientsEnabledFor  +
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

    private String getString(String name, String defaultValue) {
        return properties.getProperty(PROPERTY_PREFIX + name, defaultValue);
    }

    private long getLong(String name, long defaultValue) {
        String propertyValue = properties.getProperty(PROPERTY_PREFIX + name);
        if (propertyValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(propertyValue.trim());
        } catch (Exception ex) {
            LOGGER.warn("Exception parsing property {} with value {} into integral value. Using default.",
                    name, propertyValue, ex);
            return defaultValue;
        }
    }

    private int getInt(String name, int defaultValue) {
        long value = getLong(name, defaultValue);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer overflow for value " + name + ": " + value);
        }
        return (int) value;
    }
}
