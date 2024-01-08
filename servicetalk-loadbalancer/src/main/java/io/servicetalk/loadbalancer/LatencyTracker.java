/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ScoreSupplier;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A tracker of latency of an action over time.
 */
interface LatencyTracker extends ScoreSupplier {

    /**
     * Invoked before each start of the action for which latency is to be tracked.
     *
     * @return Current time in nanoseconds.
     */
    long beforeStart();

    /**
     * Records a successful completion of the action for which latency is to be tracked.
     *
     * @param beforeStartTimeNs return value from {@link #beforeStart()}.
     */
    void observeSuccess(long beforeStartTimeNs);

    /**
     * Records cancellation of the action for which latency is to be tracked.
     *
     * @param beforeStartTimeNs return value from {@link #beforeStart()}.
     */
    void observeCancel(long beforeStartTimeNs);

    /**
     * Records a failed completion of the action for which latency is to be tracked.
     *
     * @param beforeStartTimeNs return value from {@link #beforeStart()}.
     */
    void observeError(long beforeStartTimeNs);

    /**
     * Create a latency tracker.
     *
     * @param measurementHalfLife The half-life decay hint period for the tracker.
     * This is used to help decay bad scoring over time.
     * @param currentTimeSupplier A wall-time supplier.
     */
    static LatencyTracker newTracker(final Duration measurementHalfLife, final LongSupplier currentTimeSupplier) {
        return new DefaultLatencyTracker(measurementHalfLife.toNanos(), currentTimeSupplier);
    }
}
