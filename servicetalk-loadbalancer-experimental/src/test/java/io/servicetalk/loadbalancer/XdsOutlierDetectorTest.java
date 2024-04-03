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

import io.servicetalk.concurrent.api.TestExecutor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class XdsOutlierDetectorTest {

    private final TestExecutor executor = new TestExecutor();
    OutlierDetectorConfig config = new OutlierDetectorConfig.Builder()
            .failureDetectorInterval(Duration.ofSeconds(5), Duration.ZERO)
            .ejectionTimeJitter(Duration.ZERO)
            .build();

    @Nullable
    XdsOutlierDetector<String, TestLoadBalancedConnection> xdsOutlierDetector;

    private void init() {
        xdsOutlierDetector = new XdsOutlierDetector<>(
            new NormalizedTimeSourceExecutor(executor), config, "lb-description", exn -> {
                // just rethrow and it should surface to the tests.
                throw new RuntimeException("Unexpected exception", exn);
        });
    }

    @Test
    void outlierDetectorCancellation() {
        init();
        HealthIndicator<String, TestLoadBalancedConnection> indicator = xdsOutlierDetector.newHealthIndicator(
                "addr-1", NoopLoadBalancerObserver.instance().hostObserver("addr-1"));
        xdsOutlierDetector.cancel();
        assertThat(indicator.isHealthy(), equalTo(true));
    }

    @Test
    void cancellationOfEvictedHealthIndicatorMarksHostUnejected() {
        init();
        HealthIndicator<String, TestLoadBalancedConnection> healthIndicator = xdsOutlierDetector.newHealthIndicator(
                "addr-1", NoopLoadBalancerObserver.instance().hostObserver("addr-1"));
        eject(healthIndicator);
        assertThat(healthIndicator.isHealthy(), equalTo(false));
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(1));
        healthIndicator.cancel();
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(0));
    }

    @Test
    void maxHostRemovalIsHonored() {
        config = new OutlierDetectorConfig.Builder(config)
                .maxEjectionPercentage(50)
                .build();
        init();

        HealthIndicator<String, TestLoadBalancedConnection> indicator1 = xdsOutlierDetector.newHealthIndicator(
                "addr-1", NoopLoadBalancerObserver.instance().hostObserver("addr-1"));
        HealthIndicator<String, TestLoadBalancedConnection> indicator2 = xdsOutlierDetector.newHealthIndicator(
                "addr-2", NoopLoadBalancerObserver.instance().hostObserver("addr-2"));
        eject(indicator1);
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(1));
        assertThat(indicator1.isHealthy(), equalTo(false));
        eject(indicator2);
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(1));
        assertThat(indicator2.isHealthy(), equalTo(true));

        // revive indicator1
        executor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertThat(indicator1.isHealthy(), equalTo(true));

        // eject indicator2 and then indicator1. They should only require one bad request to eject again.
        indicator2.onRequestError(indicator2.beforeConnectStart(), ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        assertThat(indicator2.isHealthy(), equalTo(false));
        // should be allowed to be ejected
        indicator1.onRequestError(indicator1.beforeConnectStart(), ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        assertThat(indicator1.isHealthy(), equalTo(true));
    }

    @Test
    void hostRevival() {
        init();
        HealthIndicator<String, TestLoadBalancedConnection> indicator = xdsOutlierDetector.newHealthIndicator(
                "addr-1", NoopLoadBalancerObserver.instance().hostObserver("addr-1"));
        eject(indicator);
        assertThat(indicator.isHealthy(), equalTo(false));
        executor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertThat(indicator.isHealthy(), equalTo(true));
    }

    private void eject(HealthIndicator<String, TestLoadBalancedConnection> indicator) {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            indicator.onRequestError(indicator.beforeConnectStart(), ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        }
    }
}
