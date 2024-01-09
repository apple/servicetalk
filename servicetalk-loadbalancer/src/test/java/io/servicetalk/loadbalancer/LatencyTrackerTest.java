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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import static io.servicetalk.loadbalancer.LatencyTracker.newTracker;
import static java.lang.System.nanoTime;
import static java.time.Duration.ofSeconds;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LatencyTrackerTest {

    @Test
    void test() {
        final LongUnaryOperator nextValueProvider = mock(LongUnaryOperator.class);
        when(nextValueProvider.applyAsLong(anyLong())).thenAnswer(__ -> ofSeconds(1).toNanos());
        final LongSupplier currentTimeSupplier = new TestClock(nextValueProvider);

        final LatencyTracker latencyTracker = newTracker(Duration.ofSeconds(1), currentTimeSupplier);
        Assertions.assertEquals(0, latencyTracker.score());

        // upon success score
        long start = latencyTracker.beforeStart();
        latencyTracker.observeSuccess(start);
        Assertions.assertEquals(-500, latencyTracker.score());

        // error penalty
        start = latencyTracker.beforeStart();
        latencyTracker.observeError(start);
        Assertions.assertEquals(-5000, latencyTracker.score());

        // decay
        when(nextValueProvider.applyAsLong(anyLong())).thenAnswer(__ -> ofSeconds(20).toNanos());
        Assertions.assertEquals(-1, latencyTracker.score());
    }

    final class TestClock implements LongSupplier {
        private final LongUnaryOperator nextValueProvider;
        private long lastValue = nanoTime();

        TestClock(final LongUnaryOperator nextValueProvider) {
            this.nextValueProvider = nextValueProvider;
        }

        @Override
        public long getAsLong() {
            lastValue += nextValueProvider.applyAsLong(lastValue);
            return lastValue;
        }
    }
}
