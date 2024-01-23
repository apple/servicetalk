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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
        config = new OutlierDetectorConfig.Builder()
                .maxEjectionTimeJitter(Duration.ZERO)
                .maxEjectionTime(Duration.ofSeconds(MAX_EJECTION_SECONDS))
                .baseEjectionTime(Duration.ofSeconds(1))
                .build();
        initIndicator();
    }

    private void initIndicator() {
        healthIndicator = new TestIndicator(config);
    }

    @Test
    void consecutive5xx() {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            healthIndicator.onError(healthIndicator.beforeStart() + 1);
        }
        assertFalse(healthIndicator.isHealthy());
    }

    @Test
    void nonConsecutive5xxDoesntTripIndicator() {
        for (int i = 0; i < config.consecutive5xx() * 10; i++) {
            if ((i % 2) == 0) {
                healthIndicator.onError(healthIndicator.beforeStart() + 1);
            } else {
                healthIndicator.onSuccess(healthIndicator.beforeStart() + 1);
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
        // be evicted for three * baseEjectionTime.
        ejectIndicator(false);
        // now see how long it was ejected
        ejectIndicator(true);
        testExecutor.advanceTimeBy(config.baseEjectionTime().toNanos() * 3 - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void failureMultiplierOverflow() {
        // make sure out configuration is actually correct
        assertEquals(Duration.ofSeconds(1), config.baseEjectionTime());
        assertEquals(Duration.ofSeconds(MAX_EJECTION_SECONDS), config.maxEjectionTime());
        assertEquals(Duration.ZERO, config.maxEjectionTimeJitter());

        // Eject as many times in a row to get the ejection time maxed out.
        for (long i = 0; i < MAX_EJECTION_SECONDS; i++) {
            ejectIndicator(true);
            // ensure the indicator is ejected until the very last nanosecond.
            testExecutor.advanceTimeBy(Duration.ofSeconds(i + 1).toNanos() - 1, TimeUnit.NANOSECONDS);
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
        // delay for the next failure which should be 2x the base.
        for (int i = 0; i < 8; i++) {
            ejectIndicator(false);
        }
        ejectIndicator(true);
        testExecutor.advanceTimeBy(Duration.ofSeconds(2).toNanos() - 1, TimeUnit.NANOSECONDS);
        assertFalse(healthIndicator.isHealthy());
        testExecutor.advanceTimeBy(1, TimeUnit.NANOSECONDS);
        // now we should be healthy again
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void cancellationWillConsiderAHostRevived() {
        for (int i = 0; i < config.consecutive5xx(); i++) {
            healthIndicator.onError(healthIndicator.beforeStart() + 1);
        }
        assertFalse(healthIndicator.isHealthy());
        healthIndicator.cancel();
        assertEquals(1, healthIndicator.revivalCount);
        assertTrue(healthIndicator.cancelled);
    }

    private void ejectIndicator(boolean isOutlier) {
        sequentialExecutor.execute(() -> healthIndicator.updateOutlierStatus(config, isOutlier));
    }

    private class TestIndicator extends XdsHealthIndicator<String> {

        private final OutlierDetectorConfig config;
        boolean cancelled;
        int ejectionCount;
        int revivalCount;
        boolean mayEjectHost = true;

        TestIndicator(final OutlierDetectorConfig config) {
            super(sequentialExecutor, new NormalizedTimeSourceExecutor(testExecutor), "address",
                    NoopLoadBalancerObserver.<String>instance().hostObserver());
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
