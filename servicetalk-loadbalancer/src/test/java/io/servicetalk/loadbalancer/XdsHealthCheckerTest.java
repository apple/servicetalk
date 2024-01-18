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

import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static java.lang.Math.max;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XdsHealthCheckerTest {

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    @Nullable
    TestExecutor testExecutor;
    OutlierDetectorConfig config;
    XdsHealthChecker<String> healthChecker;

    private OutlierDetectorConfig.Builder withAllEnforcing() {
        return new OutlierDetectorConfig.Builder()
                // set enforcing rates to 100% so that we don't have to deal with statics
                .enforcingConsecutive5xx(100)
                .enforcingFailurePercentage(100)
                .enforcingSuccessRate(100)
                .enforcingConsecutiveGatewayFailure(100)
                .enforcingConsecutiveLocalOriginFailure(100)
                .enforcingFailurePercentageLocalOrigin(100)
                .enforcingLocalOriginSuccessRate(100);
    }

    @BeforeEach
    void initialize() {
        testExecutor = executor.executor();
        config = withAllEnforcing().build();
        healthChecker = buildHealthChecker();
    }

    private XdsHealthChecker<String> buildHealthChecker() {
        LoadBalancerObserver<String> observer = NoopLoadBalancerObserver.instance();
        return new XdsHealthChecker<>(new NormalizedTimeSourceExecutor(testExecutor), observer.hostObserver(), config);
    }

    @Test
    void healthChecksAreScheduled() {
        assertThat(testExecutor.scheduledTasksPending(), equalTo(1));
    }

    @Test
    void cancellation() {
        config = withAllEnforcing().maxEjectionPercentage(100).build();
        healthChecker = buildHealthChecker();
        HealthIndicator indicator1 = healthChecker.newHealthIndicator("address-1");
        HealthIndicator indicator2 = healthChecker.newHealthIndicator("address-2");
        eject(indicator1);
        eject(indicator2);
        assertFalse(indicator1.isHealthy());
        assertFalse(indicator2.isHealthy());
        healthChecker.cancel();

        // Because they were cancelled both indicators should now consider themselves healthy.
        assertTrue(indicator1.isHealthy());
        assertTrue(indicator2.isHealthy());
    }

    @Test
    void maxEjectionPercentage() {
        testEjectPercentage(0);
        testEjectPercentage(25);
        testEjectPercentage(50);
        testEjectPercentage(75);
        testEjectPercentage(100);
    }

    private void testEjectPercentage(int maxEjectPercentage) {
        config = withAllEnforcing().maxEjectionPercentage(maxEjectPercentage).build();
        healthChecker = buildHealthChecker();
        List<HealthIndicator> healthIndicators = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            healthIndicators.add(healthChecker.newHealthIndicator("address-" + i));
        }

        for (HealthIndicator indicator : healthIndicators) {
            eject(indicator);
        }

        int expectedFailed = max(1, maxEjectPercentage * healthIndicators.size() / 100);
        assertThat(healthIndicators.stream()
                .filter(indicator -> !indicator.isHealthy()).collect(Collectors.toList()), hasSize(expectedFailed));
    }

    private void eject(HealthIndicator indicator) {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            // start to kill off our indicators.
            long startTime = indicator.beforeStart();
            indicator.onError(startTime + 1);
        }
    }
}
