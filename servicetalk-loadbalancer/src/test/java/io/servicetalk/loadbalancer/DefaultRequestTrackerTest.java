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
import java.util.function.LongUnaryOperator;

import static java.lang.System.nanoTime;
import static java.time.Duration.ofSeconds;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultRequestTrackerTest {

    @Test
    void test() {
        final LongUnaryOperator nextValueProvider = mock(LongUnaryOperator.class);
        when(nextValueProvider.applyAsLong(anyLong())).thenAnswer(__ -> ofSeconds(1).toNanos());
        final DefaultRequestTracker requestTracker = new TestRequestTracker(Duration.ofSeconds(1), nextValueProvider);
        Assertions.assertEquals(0, requestTracker.score());

        // upon success score
        requestTracker.onSuccess(requestTracker.beforeStart());
        Assertions.assertEquals(-500, requestTracker.score());

        // error penalty
        requestTracker.onError(requestTracker.beforeStart(), ErrorClass.LOCAL_ORIGIN_CONNECT_FAILED, null);
        Assertions.assertEquals(-5000, requestTracker.score());

        // cancellation penalty
        requestTracker.onError(requestTracker.beforeStart(), ErrorClass.CANCELLED, null);
        Assertions.assertEquals(-12_500, requestTracker.score());

        // decay
        when(nextValueProvider.applyAsLong(anyLong())).thenAnswer(__ -> ofSeconds(20).toNanos());
        Assertions.assertEquals(-1, requestTracker.score());
    }

    static final class TestRequestTracker extends DefaultRequestTracker {
        private final LongUnaryOperator nextValueProvider;
        private long lastValue = nanoTime();

        TestRequestTracker(Duration measurementHalfLife, final LongUnaryOperator nextValueProvider) {
            super(measurementHalfLife.toNanos(), DEFAULT_CANCEL_PENALTY, DEFAULT_ERROR_PENALTY);
            this.nextValueProvider = nextValueProvider;
        }

        @Override
        protected long currentTimeNanos() {
            lastValue += nextValueProvider.applyAsLong(lastValue);
            return lastValue;
        }
    }
}
