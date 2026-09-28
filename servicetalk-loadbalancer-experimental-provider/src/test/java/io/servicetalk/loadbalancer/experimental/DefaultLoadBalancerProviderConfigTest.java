/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.loadbalancer.OutlierDetectorConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultLoadBalancerProviderConfigTest {

    private static final String BASE_EJECTION_TIME_MS = "io.servicetalk.loadbalancer.experimental.baseEjectionTimeMs";
    private static final String MAX_EJECTION_TIME_MS = "io.servicetalk.loadbalancer.experimental.maxEjectionTimeMs";
    private static final String FAILED_CONNECTIONS_THRESHOLD =
            "io.servicetalk.loadbalancer.experimental.failedConnectionsThreshold";
    private static final String FAILURE_DETECTOR_INTERVAL_MS =
            "io.servicetalk.loadbalancer.experimental.failureDetectorIntervalMs";
    private static final String MAX_EJECTION_PERCENTAGE =
            "io.servicetalk.loadbalancer.experimental.maxEjectionPercentage";
    private static final String DEPRECATED_FAILED_CONNECTIONS_THRESHOLD =
            "io.servicetalk.loadbalancer.experimental.healthCheckFailedConnectionsThreshold";
    private static final String DEPRECATED_INTERVAL_MS = "io.servicetalk.loadbalancer.experimental.intervalMs";
    private static final String DEPRECATED_MAX_EJECTION_PERCENT =
            "io.servicetalk.loadbalancer.experimental.maxEjectionPercent";

    @AfterEach
    void clearProperties() {
        System.clearProperty(BASE_EJECTION_TIME_MS);
        System.clearProperty(MAX_EJECTION_TIME_MS);
        System.clearProperty(FAILED_CONNECTIONS_THRESHOLD);
        System.clearProperty(FAILURE_DETECTOR_INTERVAL_MS);
        System.clearProperty(MAX_EJECTION_PERCENTAGE);
        System.clearProperty(DEPRECATED_FAILED_CONNECTIONS_THRESHOLD);
        System.clearProperty(DEPRECATED_INTERVAL_MS);
        System.clearProperty(DEPRECATED_MAX_EJECTION_PERCENT);
    }

    @Test
    void defaultsMatchOutlierDetectorConfigDefaults() {
        assertThat(DefaultLoadBalancerProviderConfig.instance().outlierDetectorConfig().toString(),
                equalTo(new OutlierDetectorConfig.Builder().build().toString()));
    }

    @Test
    void unsetMaxEjectionTimeIsDerivedFromBaseEjectionTime() {
        System.setProperty(BASE_EJECTION_TIME_MS, "400000");
        assertThat(DefaultLoadBalancerProviderConfig.instance().outlierDetectorConfig().maxEjectionTime(),
                equalTo(ofSeconds(400)));
    }

    @Test
    void maxEjectionTimeLessThanBaseEjectionTimeIsRejected() {
        System.setProperty(BASE_EJECTION_TIME_MS, "30000");
        System.setProperty(MAX_EJECTION_TIME_MS, "10000");
        DefaultLoadBalancerProviderConfig config = DefaultLoadBalancerProviderConfig.instance();
        assertThrows(IllegalArgumentException.class, config::outlierDetectorConfig);
    }

    @Test
    void deprecatedNamesAreHonored() {
        System.setProperty(DEPRECATED_FAILED_CONNECTIONS_THRESHOLD, "7");
        System.setProperty(DEPRECATED_INTERVAL_MS, "2000");
        System.setProperty(DEPRECATED_MAX_EJECTION_PERCENT, "40");
        OutlierDetectorConfig config = DefaultLoadBalancerProviderConfig.instance().outlierDetectorConfig();
        assertThat(config.failedConnectionsThreshold(), equalTo(7));
        assertThat(config.failureDetectorInterval(), equalTo(ofMillis(2000)));
        assertThat(config.maxEjectionPercentage(), equalTo(40));
    }

    @Test
    void currentNamesTakePrecedenceOverDeprecatedNames() {
        System.setProperty(DEPRECATED_FAILED_CONNECTIONS_THRESHOLD, "7");
        System.setProperty(DEPRECATED_INTERVAL_MS, "2000");
        System.setProperty(DEPRECATED_MAX_EJECTION_PERCENT, "40");
        System.setProperty(FAILED_CONNECTIONS_THRESHOLD, "8");
        System.setProperty(FAILURE_DETECTOR_INTERVAL_MS, "3000");
        System.setProperty(MAX_EJECTION_PERCENTAGE, "50");
        OutlierDetectorConfig config = DefaultLoadBalancerProviderConfig.instance().outlierDetectorConfig();
        assertThat(config.failedConnectionsThreshold(), equalTo(8));
        assertThat(config.failureDetectorInterval(), equalTo(ofMillis(3000)));
        assertThat(config.maxEjectionPercentage(), equalTo(50));
    }
}
