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
import io.servicetalk.concurrent.api.TestExecutor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

final class XdsOutlierDetectorTest {

    private final TestExecutor executor = new TestExecutor();
    OutlierDetectorConfig config = new OutlierDetectorConfig.Builder()
            .failureDetectorInterval(Duration.ofSeconds(5), Duration.ZERO)
            .ejectionTimeJitter(Duration.ZERO)
            .baseEjectionTime(Duration.ofSeconds(2))
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
        consecutiveFailureEject(healthIndicator);
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
        consecutiveFailureEject(indicator1);
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(1));
        assertThat(indicator1.isHealthy(), equalTo(false));
        consecutiveFailureEject(indicator2);
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(1));
        assertThat(indicator2.isHealthy(), equalTo(true));

        // revive indicator1
        executor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertThat(indicator1.isHealthy(), equalTo(true));

        // eject indicator2 and then indicator1. They should only require one bad request to eject again.
        indicator2.onRequestError(indicator2.beforeConnectStart(), RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        assertThat(indicator2.isHealthy(), equalTo(false));
        // should be allowed to be ejected
        indicator1.onRequestError(indicator1.beforeConnectStart(), RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        assertThat(indicator1.isHealthy(), equalTo(true));
    }

    @Test
    void hostRevival() {
        init();
        HealthIndicator<String, TestLoadBalancedConnection> indicator = xdsOutlierDetector.newHealthIndicator(
                "addr-1", NoopLoadBalancerObserver.instance().hostObserver("addr-1"));
        consecutiveFailureEject(indicator);
        assertThat(indicator.isHealthy(), equalTo(false));
        executor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertThat(indicator.isHealthy(), equalTo(true));
    }

    @Test
    void consecutiveFailuresTriggersHealthChangeSignal() {
        config = new OutlierDetectorConfig.Builder(config)
                // make it longer than the failure detector interval
                .baseEjectionTime(config.failureDetectorInterval().multipliedBy(2))
                .build();
        init();
        AtomicInteger healthChanges = new AtomicInteger();
        xdsOutlierDetector.healthStatusChanged().forEach(ignored -> healthChanges.incrementAndGet());
        HealthIndicator<String, TestLoadBalancedConnection> indicator = xdsOutlierDetector.newHealthIndicator(
                "addr-1", NoopLoadBalancerObserver.instance().hostObserver("addr-1"));
        consecutiveFailureEject(indicator);
        assertThat(healthChanges.get(), equalTo(0));
        assertThat(indicator.isHealthy(), equalTo(false));
        executor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);
        assertThat(indicator.isHealthy(), equalTo(false));
        assertThat(healthChanges.get(), equalTo(1));

        // We should revive after another interval
        executor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);
        assertThat(indicator.isHealthy(), equalTo(true));
        assertThat(healthChanges.get(), equalTo(2));
    }

    private void consecutiveFailureEject(HealthIndicator<String, TestLoadBalancedConnection> indicator) {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            indicator.onRequestError(indicator.beforeConnectStart(),
                    RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        }
    }

    @Test
    void idleHostsAreExcludedFromSuccessRateStatistics() {
        // Only the success rate detector should run and it should only consider hosts with sufficient volume.
        config = new OutlierDetectorConfig.Builder(config)
                .enforcingConsecutive5xx(0)
                .enforcingFailurePercentage(0)
                .enforcingSuccessRate(100)
                .successRateMinimumHosts(3)
                .successRateRequestVolume(10)
                .maxEjectionPercentage(100)
                .build();
        init();

        List<HealthIndicator<String, TestLoadBalancedConnection>> healthy = newIndicators("healthy-", 3);
        HealthIndicator<String, TestLoadBalancedConnection> borderline = newIndicator("borderline");
        List<HealthIndicator<String, TestLoadBalancedConnection>> idle = newIndicators("idle-", 3);

        // Three hosts have a perfect success rate at sufficient volume.
        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : healthy) {
            record(indicator, 10, 0);
        }
        // The borderline host has an 80% success rate at sufficient volume. It is within 1.9 stdev of the
        // mean when only real-traffic hosts are considered, so it must not be ejected. If the idle hosts were
        // counted at a fabricated 1.0 success rate, the mean would rise and the stdev shrink, ejecting it.
        record(borderline, 8, 2);
        // The idle hosts receive no requests at all.

        executor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);

        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : healthy) {
            assertThat(indicator.isHealthy(), equalTo(true));
        }
        assertThat(borderline.isHealthy(), equalTo(true));
        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : idle) {
            assertThat(indicator.isHealthy(), equalTo(true));
        }
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(0));
    }

    @Test
    void lowVolumeHostsAreNotEjectedBySuccessRate() {
        config = new OutlierDetectorConfig.Builder(config)
                .enforcingConsecutive5xx(0)
                .enforcingFailurePercentage(0)
                .enforcingSuccessRate(100)
                .successRateMinimumHosts(3)
                .successRateRequestVolume(10)
                .maxEjectionPercentage(100)
                .build();
        init();

        List<HealthIndicator<String, TestLoadBalancedConnection>> healthy = newIndicators("healthy-", 5);
        HealthIndicator<String, TestLoadBalancedConnection> lowVolume = newIndicator("low-volume");

        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : healthy) {
            record(indicator, 10, 0);
        }
        // A host with a terrible success rate but below the request volume threshold is not eligible for ejection.
        record(lowVolume, 0, 3);

        executor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);

        assertThat(lowVolume.isHealthy(), equalTo(true));
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(0));
    }

    @Test
    void lowVolumeHostsAreNotEjectedByFailurePercentage() {
        // Only the failure percentage detector should run and it should only consider hosts with sufficient volume.
        config = new OutlierDetectorConfig.Builder(config)
                .enforcingConsecutive5xx(0)
                .enforcingSuccessRate(0)
                .enforcingFailurePercentage(100)
                .failurePercentageMinimumHosts(3)
                .failurePercentageRequestVolume(10)
                .maxEjectionPercentage(100)
                .build();
        init();

        List<HealthIndicator<String, TestLoadBalancedConnection>> healthy = newIndicators("healthy-", 3);
        HealthIndicator<String, TestLoadBalancedConnection> highVolumeBad = newIndicator("high-volume-bad");
        HealthIndicator<String, TestLoadBalancedConnection> lowVolumeBad = newIndicator("low-volume-bad");

        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : healthy) {
            record(indicator, 10, 0);
        }
        // A host above the failure threshold with sufficient volume must still be ejected.
        record(highVolumeBad, 1, 9);
        // A host at 100% failure but below the request volume threshold is not eligible for ejection.
        record(lowVolumeBad, 0, 3);

        executor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);

        for (HealthIndicator<String, TestLoadBalancedConnection> indicator : healthy) {
            assertThat(indicator.isHealthy(), equalTo(true));
        }
        assertThat(highVolumeBad.isHealthy(), equalTo(false));
        assertThat(lowVolumeBad.isHealthy(), equalTo(true));
        assertThat(xdsOutlierDetector.ejectedHostCount(), equalTo(1));
    }

    private List<HealthIndicator<String, TestLoadBalancedConnection>> newIndicators(String prefix, int count) {
        List<HealthIndicator<String, TestLoadBalancedConnection>> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(newIndicator(prefix + i));
        }
        return result;
    }

    private HealthIndicator<String, TestLoadBalancedConnection> newIndicator(String address) {
        return xdsOutlierDetector.newHealthIndicator(
                address, NoopLoadBalancerObserver.instance().hostObserver(address));
    }

    private static void record(HealthIndicator<String, TestLoadBalancedConnection> indicator,
                               int successes, int failures) {
        // Spread the failures evenly across the successes rather than emitting them in a trailing block, so
        // these tests never build an unintended consecutive-failure streak that a future change could let the
        // consecutive-5xx detector act on.
        final int total = successes + failures;
        for (int i = 0; i < total; i++) {
            if ((i + 1) * failures / total > i * failures / total) {
                indicator.onRequestError(indicator.beforeRequestStart(),
                        RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
            } else {
                indicator.onRequestSuccess(indicator.beforeRequestStart());
            }
        }
    }
}
