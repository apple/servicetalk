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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XdsHealthIndicatorTest {

    private static final long MAX_EJECTION_SECONDS = 10L;

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    private final SequentialExecutor sequentialExecutor = new SequentialExecutor(ex -> {
        throw new RuntimeException(ex); });

    private TestExecutor testExecutor;
    private OutlierDetectorConfig config;
    private TestIndicator healthIndicator;

    @BeforeEach
    void initialize() {
        testExecutor = executor.executor();
        config = baseBuilder().build();
        initIndicator();
    }

    private OutlierDetectorConfig.Builder baseBuilder() {
        return new OutlierDetectorConfig.Builder()
                .maxEjectionTime(ofSeconds(MAX_EJECTION_SECONDS))
                .baseEjectionTime(ofSeconds(1))
                // The failure multiplier only decays after a full interval has elapsed since revival (the grace
                // period). Pin the interval to the base ejection time with no jitter so the decay boundaries are
                // deterministic in these tests.
                .failureDetectorInterval(ofSeconds(1), ZERO)
                .ejectionTimeJitter(ZERO);
    }

    private void initIndicator() {
        healthIndicator = new TestIndicator(config);
    }

    @Test
    void consecutive5xx() {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            healthIndicator.onRequestError(healthIndicator.beforeRequestStart() + 1,
                    RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        }
        assertFalse(healthIndicator.isHealthy());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationStatus(boolean cancellationIsError) {
        config = baseBuilder().cancellationIsError(cancellationIsError).build();
        initIndicator();
        for (int i = 0; i < config.consecutive5xx(); i++) {
            healthIndicator.onRequestError(healthIndicator.beforeRequestStart() + 1,
                    RequestTracker.ErrorClass.CANCELLED);
        }
        assertThat(healthIndicator.isHealthy(), equalTo(!cancellationIsError));
        assertEquals(cancellationIsError ? config.consecutive5xx() : 0L, healthIndicator.getFailures());
    }

    @Test
    void nonConsecutive5xxDoesntTripIndicator() {
        for (int i = 0; i < config.consecutive5xx() * 10; i++) {
            if ((i % 2) == 0) {
                healthIndicator.onRequestError(healthIndicator.beforeRequestStart() + 1,
                        RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
            } else {
                healthIndicator.onRequestSuccess(healthIndicator.beforeRequestStart() + 1);
            }
        }
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void wontEjectWithoutHelperSayingItsOkayToDoSo() {
        healthIndicator.mayEjectHost = false;
        ejectIndicator(true);
        assertTrue(healthIndicator.isHealthy());

        // how try to eject if the helper allows
        healthIndicator.mayEjectHost = true;
        ejectIndicator(true);
        assertFalse(healthIndicator.isHealthy());
    }

    @Test
    void hostRevival() {
        ejectIndicator(true);
        assertEquals(1, healthIndicator.ejectionCount);
        assertFalse(healthIndicator.isHealthy());

        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());
        assertEquals(1, healthIndicator.revivalCount);
    }

    @Test
    void outlierOnTheExpiringIntervalRevivesButDoesNotReEjectThatRound() {
        ejectIndicator(true);
        assertEquals(1, healthIndicator.ejectionCount);
        assertFalse(healthIndicator.isHealthy());

        // Let the ejection time elapse, then run a round that still judges the host an outlier. updateOutlierStatus
        // revives it and returns without re-evaluating the verdict this round -- the counters still hold the
        // pre-revival stats, so re-ejecting on them would double-count. The host becomes healthy and the ejection
        // count is unchanged; it can only be re-ejected on a subsequent round (on fresh data).
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        ejectIndicator(true);
        assertTrue(healthIndicator.isHealthy());
        assertEquals(1, healthIndicator.revivalCount);
        assertEquals(1, healthIndicator.ejectionCount);

        // A following round can eject the now-healthy host again.
        ejectIndicator(true);
        assertFalse(healthIndicator.isHealthy());
        assertEquals(2, healthIndicator.ejectionCount);
    }

    @Test
    void failureMultiplier() {
        ejectIndicator(true);
        assertEquals(1, healthIndicator.ejectionCount);
        assertFalse(healthIndicator.isHealthy());

        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());

        // Now the ejection time should grow by 2x since it was ejected twice in a row.
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 2 - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());

        // one more failure in a row to get our multiplier to 3.
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 3 - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());

        // Give it a healthy round and the multiplier should go down to two. This means the next eviction will
        // be evicted for three * baseEjectionTime. The decrement only happens once a full interval has elapsed
        // since the host was revived (the grace period), so advance one interval before the healthy round.
        testExecutor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);
        ejectIndicator(false);
        // now see how long it was ejected
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 3 - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void failureMultiplierDoesNotDecayWithinTheGracePeriodAfterRevival() {
        // Eject twice in a row so the multiplier grows to 2.
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos(), TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 2, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());

        // A healthy round within the grace period (before a full interval has elapsed since revival) must NOT
        // decay the multiplier, so the next ejection still lasts 3x base (1 + the un-decayed multiplier of 2).
        ejectIndicator(false);
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 3 - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void failureMultiplierDecayIsAnchoredAtScheduledEjectionEndNotRevivalTime() {
        // Eject once: multiplier 0 -> 1, ejected until t == baseEjectionTime.
        ejectIndicator(true);
        assertFalse(healthIndicator.isHealthy());

        // Advance well past the ejection end WITHOUT calling isHealthy() (which would revive eagerly), then run a
        // detection round so the host is revived lazily by updateOutlierStatus rather than by a selector.
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() + config.failureDetectorInterval().toNanos(),
                TimeUnit.NANOSECONDS);
        ejectIndicator(false);
        assertTrue(healthIndicator.isHealthy());
        assertEquals(1, healthIndicator.revivalCount);

        // The very next healthy round decays the multiplier even though no time has passed since the lazy revival:
        // the grace period is measured from the scheduled ejection end, not from when the revival was processed. So
        // the next ejection lasts only 1x base (multiplier decayed 1 -> 0).
        ejectIndicator(false);
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void failureMultiplierOverflow() {
        // make sure out configuration is actually correct
        assertEquals(ofSeconds(1), config.baseEjectionTime());
        assertEquals(ofSeconds(MAX_EJECTION_SECONDS), config.maxEjectionTime());
        assertEquals(ZERO, config.ejectionTimeJitter());

        // Eject as many times in a row to get the ejection time maxed out.
        for (long i = 0; i < MAX_EJECTION_SECONDS; i++) {
            ejectIndicator(true);
            // ensure the indicator is ejected until the very last nanosecond.
            testExecutor.advanceTimeBy(ofSeconds(i + 1).toNanos() - 1, TimeUnit.NANOSECONDS);
            assertFalse(healthIndicator.isHealthy());
            testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
            // now we should be healthy again
            assertTrue(healthIndicator.isHealthy());
        }

        // Eject again and we should still be unhealthy only until maxEjectionTime.
        ejectIndicator(true);
        assertFalse(healthIndicator.isHealthy());
        // ensure the indicator is ejected until the very last nanosecond.
        testExecutor.advanceTimeBy(config.maxEjectionTime().toNanos() - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        // now we should be healthy again
        assertTrue(healthIndicator.isHealthy());

        // now set it healthy 8 times in a row to decrement the multiplier and make sure we get the right
        // delay for the next failure which should be 2x the base. Advance one interval first so the grace period
        // since revival has elapsed; after that every healthy round decrements.
        testExecutor.advanceTimeBy(config.failureDetectorInterval().toNanos(), TimeUnit.NANOSECONDS);
        for (int i = 0; i < 8; i++) {
            ejectIndicator(false);
        }
        ejectIndicator(true);
        testExecutor.advanceTimeBy(ofSeconds(2).toNanos() - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        // now we should be healthy again
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void cancellationWillConsiderAHostRevived() {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            healthIndicator.onRequestError(healthIndicator.beforeRequestStart() + 1,
                    RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED);
        }
        assertFalse(healthIndicator.isHealthy());
        healthIndicator.cancel();
        assertEquals(1, healthIndicator.revivalCount);
        assertTrue(healthIndicator.cancelled);
    }

    private void ejectIndicator(boolean isOutlier) {
        sequentialExecutor.execute(() -> healthIndicator.updateOutlierStatus(config, isOutlier));
    }

    private class TestIndicator extends XdsHealthIndicator<String, TestLoadBalancedConnection> {

        private final OutlierDetectorConfig config;
        boolean cancelled;
        int ejectionCount;
        int revivalCount;
        boolean mayEjectHost = true;

        TestIndicator(final OutlierDetectorConfig config) {
            super(sequentialExecutor, new NormalizedTimeSourceExecutor(testExecutor), ofSeconds(10),
                    config.ewmaCancellationPenalty(), config.ewmaErrorPenalty(), config.concurrentRequestPenalty(),
                    config.cancellationIsError(),
                    "address", "description", NoopLoadBalancerObserver.<String>instance().hostObserver("address"));
            this.config = config;
        }

        @Override
        protected OutlierDetectorConfig currentConfig() {
            return config;
        }

        @Override
        protected boolean tryEjectHost() {
            if (mayEjectHost) {
                ejectionCount++;
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void hostRevived() {
            revivalCount++;
        }

        @Override
        protected void doCancel() {
            assert !cancelled;
            cancelled = true;
        }
    }
}
