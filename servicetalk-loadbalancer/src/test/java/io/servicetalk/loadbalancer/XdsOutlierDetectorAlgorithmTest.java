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

import io.servicetalk.client.api.RequestTracker;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static java.lang.Math.max;
import static java.time.Duration.ZERO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XdsOutlierDetectorAlgorithmTest {

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    @Nullable
    TestExecutor testExecutor;
    OutlierDetectorConfig config;
    XdsOutlierDetector<String, TestLoadBalancedConnection> outlierDetector;

    private OutlierDetectorConfig.Builder withAllEnforcing() {
        return new OutlierDetectorConfig.Builder()
                // set the jitters to zero to make time more predictable
                .ejectionTimeJitter(ZERO)
                // set enforcing rates to 100% so that we don't have to deal with statics
                .enforcingConsecutive5xx(100)
                .enforcingFailurePercentage(100)
                .enforcingSuccessRate(100);
    }

    @BeforeEach
    void initialize() {
        testExecutor = executor.executor();
        config = withAllEnforcing().build();
        outlierDetector = buildOutlierDetector();
    }

    private XdsOutlierDetector<String, TestLoadBalancedConnection> buildOutlierDetector() {
        return new XdsOutlierDetector<>(new NormalizedTimeSourceExecutor(testExecutor), config, "");
    }

    private LoadBalancerObserver.HostObserver observer() {
        return NoopLoadBalancerObserver.instance().hostObserver("");
    }

    @Test
    void healthChecksAreScheduled() {
        assertThat(testExecutor.scheduledTasksPending(), equalTo(1));
    }

    @Test
    void cancellation() {
        config = withAllEnforcing().maxEjectionPercentage(100).build();
        outlierDetector = buildOutlierDetector();
        HealthIndicator<String, TestLoadBalancedConnection> indicator1 =
                outlierDetector.newHealthIndicator("address-1", observer());
        HealthIndicator<String, TestLoadBalancedConnection> indicator2 =
                outlierDetector.newHealthIndicator("address-2", observer());
        eject(indicator1);
        eject(indicator2);
        assertFalse(indicator1.isHealthy());
        assertFalse(indicator2.isHealthy());
        outlierDetector.cancel();

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

    @Test
    void withoutOutlierDetectorsWeStillDecrementFailureMultiplier() {
        config = withAllEnforcing().maxEjectionPercentage(100)
                .enforcingFailurePercentage(0)
                .enforcingSuccessRate(0)
                .build();
        outlierDetector = buildOutlierDetector();

        HealthIndicator<String, TestLoadBalancedConnection> indicator1 =
                outlierDetector.newHealthIndicator("address-1", observer());
        eject(indicator1);
        assertFalse(indicator1.isHealthy());
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(indicator1.isHealthy());
        eject(indicator1);
        assertFalse(indicator1.isHealthy());
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 2 - 1, TimeUnit.NANOSECONDS);
        assertFalse(indicator1.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(indicator1.isHealthy());

        // now let two periods elapse so our failure multiplier will get decremented.
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        eject(indicator1);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(indicator1.isHealthy());
    }

    @Test
    void successRateDetectorStillEjectsWhenFailurePercentageDetectorIsEnabled() {
        // Both detectors are enabled. The outlier host has a poor success rate (0.2) but a failure percentage (80%)
        // below the 85% failure-percentage threshold, so only the success-rate detector can catch it. This guards
        // against the failure-percentage detector consuming/resetting the shared stats before success rate runs.
        config = withAllEnforcing()
                .enforcingConsecutive5xx(0)
                .maxEjectionPercentage(100)
                .failureDetectorInterval(Duration.ofSeconds(5), ZERO)
                .build();
        outlierDetector = buildOutlierDetector();

        List<HealthIndicator<String, TestLoadBalancedConnection>> indicators = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            indicators.add(outlierDetector.newHealthIndicator("address-" + i, observer()));
        }
        // 9 hosts with a perfect success rate.
        for (int i = 0; i < 9; i++) {
            record(indicators.get(i), 100, 0);
        }
        // 1 outlier: 20% success rate, 80% failure percentage (below the 85% failure-percentage threshold).
        HealthIndicator<String, TestLoadBalancedConnection> outlier = indicators.get(9);
        record(outlier, 20, 80);

        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : indicators) {
            assertTrue(indicator.isHealthy());
        }

        // Trigger an outlier-detection round.
        testExecutor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);

        assertFalse(outlier.isHealthy(), "success-rate outlier should have been ejected");
        for (int i = 0; i < 9; i++) {
            assertTrue(indicators.get(i).isHealthy(), "healthy host should not be ejected");
        }
        assertThat(outlierDetector.ejectedHostCount(), equalTo(1));
    }

    @Test
    void failurePercentageDetectorStillEjectsWhenSuccessRateDetectorIsEnabled() {
        config = withAllEnforcing()
                .enforcingConsecutive5xx(0)
                .maxEjectionPercentage(100)
                .failureDetectorInterval(Duration.ofSeconds(5), ZERO)
                .build();
        outlierDetector = buildOutlierDetector();

        List<HealthIndicator<String, TestLoadBalancedConnection>> indicators = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            HealthIndicator<String, TestLoadBalancedConnection> indicator =
                    outlierDetector.newHealthIndicator("address-" + i, observer());
            indicators.add(indicator);
            int successes = (i + 1) * 10;
            record(indicator, successes, 100 - successes);
        }

        testExecutor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);

        // Only address-0 (10% success / 90% failure) crosses the failure-percentage threshold.
        assertFalse(indicators.get(0).isHealthy(), "failure-percentage outlier should have been ejected");
        for (int i = 1; i < 10; i++) {
            assertTrue(indicators.get(i).isHealthy(), "host below failure-percentage threshold should stay healthy");
        }
        assertThat(outlierDetector.ejectedHostCount(), equalTo(1));
    }

    @Test
    void failureMultiplierDecrementsWhenDetectorsEnabledButVolumeInsufficient() {
        config = withAllEnforcing().maxEjectionPercentage(100).build();
        outlierDetector = buildOutlierDetector();

        HealthIndicator<String, TestLoadBalancedConnection> indicator1 =
                outlierDetector.newHealthIndicator("address-1", observer());
        eject(indicator1);
        assertFalse(indicator1.isHealthy());
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(indicator1.isHealthy());
        eject(indicator1);
        assertFalse(indicator1.isHealthy());
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 2 - 1, TimeUnit.NANOSECONDS);
        assertFalse(indicator1.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(indicator1.isHealthy());

        // now let two periods elapse so our failure multiplier will get decremented.
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        eject(indicator1);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(indicator1.isHealthy());
    }

    private void testEjectPercentage(int maxEjectPercentage) {
        config = withAllEnforcing().maxEjectionPercentage(maxEjectPercentage).build();
        outlierDetector = buildOutlierDetector();
        List<HealthIndicator<String, TestLoadBalancedConnection>> healthIndicators = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            healthIndicators.add(outlierDetector.newHealthIndicator("address-" + i, observer()));
        }

        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : healthIndicators) {
            eject(indicator);
        }

        int expectedFailed = max(1, maxEjectPercentage * healthIndicators.size() / 100);
        assertThat(healthIndicators.stream()
                .filter(indicator -> !indicator.isHealthy()).collect(Collectors.toList()), hasSize(expectedFailed));
    }

    private void record(HealthIndicator<String, TestLoadBalancedConnection> indicator, int successes, int failures) {
        for (int i = 0; i < successes; i++) {
            indicator.onRequestSuccess(indicator.beforeRequestStart());
        }
        for (int i = 0; i < failures; i++) {
            indicator.onRequestError(indicator.beforeRequestStart(),
                    RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        }
    }

    private void eject(HealthIndicator<String, TestLoadBalancedConnection> indicator) {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            if (!indicator.isHealthy()) {
                break;
            }
            long startTime = indicator.beforeRequestStart();
            indicator.onRequestError(startTime + 1, RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        }
    }
}
