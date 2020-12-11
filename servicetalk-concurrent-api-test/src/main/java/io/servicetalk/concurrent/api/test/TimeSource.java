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
package io.servicetalk.concurrent.api.test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.api.test.TimeUtils.convert;
import static io.servicetalk.concurrent.api.test.TimeUtils.toChronoUnit;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A source of time that can be represented as a {@code long}.
 * <p>
 * If the underlying time source is monotonic, utility methods are provided to determine if specific duration of time
 * has elapsed (aka expired). This process requires saving a start time, and then using a fixed expiration duration:
 *
 * <pre>
 * TimeSource timeSource = timeSource();
 * long startTime = timeSource.get();
 * doWork();
 * boolean expired = timeSource.isExpired(startTime, 100, NANOSECONDS);
 * &#47;&#47; If expired is true then at least 100 nanoseconds have passed since startTime
 * </pre>
 */
@FunctionalInterface
interface TimeSource {
    /**
     * Get the current time. The units are determined by {@link #currentTimeUnits()}.
     * @return The current time. The units are determined by {@link #currentTimeUnits()}.
     */
    long currentTime();

    /**
     * Get the units for {@link #currentTime()}.
     * @return The units for {@link #currentTime()}.
     */
    default TimeUnit currentTimeUnits() {
        return NANOSECONDS;
    }

    /**
     * Determine if at least {@code duration} ticks have passed since {@code startTime} time.
     * <p>
     * If this {@link TimeSource} is not monotonic, and time goes "backwards" the behavior of this method is undefined.
     * <p>
     * Differences between {@code startTime} and {@link #currentTime()} that span greater than 2<sup>63</sup> (~292
     * years if {@link #currentTimeUnits()} is {@link TimeUnit#NANOSECONDS}) will not correctly compute elapsed time due
     * to numerical overflow.
     * @param startTime a past value of {@link #currentTime()} which represents the start time stamp.
     * @param duration How much time is permitted to pass before {@code startTime} is considered
     * expired. Units are {@link #currentTime()}. Must be {@code >= 0}.
     * @return {@code true} if at least {@code duration} ticks have passed since {@code startTime} time.
     */
    default boolean isExpired(long startTime, long duration) {
        assert duration >= 0;
        return currentTime() - startTime >= duration;
    }

    /**
     * Determine if at least {@code duration} ticks have passed since {@code startTime} time.
     * <p>
     * If this {@link TimeSource} is not monotonic, and time goes "backwards" the behavior of this method is undefined.
     * <p>
     * Differences between {@code startTime} and {@link #currentTime()} that span greater than 2<sup>63</sup> (~292
     * years if {@link #currentTimeUnits()} is {@link TimeUnit#NANOSECONDS}) will not correctly compute elapsed time due
     * to numerical overflow.
     * @param startTime a past value of {@link #currentTime()} which represents the start time stamp.
     * @param duration How much time is permitted to pass before {@code startTime} is considered
     * expired. Must be {@code >= 0}.
     * @param durationUnit The units for {@code duration}.
     * @return {@code true} if at least {@code duration} ticks have passed since {@code startTime} time.
     */
    default boolean isExpired(long startTime, long duration, TimeUnit durationUnit) {
        assert duration >= 0;
        return currentTime() - startTime >= currentTimeUnits().convert(duration, durationUnit);
    }

    /**
     * Determine if at least {@code expireDuration} ticks have passed since {@code startTime} time.
     * <p>
     * If this {@link TimeSource} is not monotonic, and time goes "backwards" the behavior of this method is undefined.
     * <p>
     * Differences between {@code startTime} and {@link #currentTime()} that span greater than 2<sup>63</sup> (~292
     * years if {@link #currentTimeUnits()} is {@link TimeUnit#NANOSECONDS}) will not correctly compute elapsed time due
     * to numerical overflow.
     * @param startTime a past value of {@link #currentTime()} which represents the start time stamp.
     * @param duration How much time is permitted to pass before {@code startTime} is considered
     * expired. Must be {@code >= 0}.
     * @return {@code true} if at least {@code expireDuration} ticks have passed since {@code startTime} time.
     */
    default boolean isExpired(long startTime, Duration duration) {
        assert !duration.isNegative();
        return currentTime() - startTime >= convert(currentTimeUnits(), duration);
    }

    /**
     * Calculate the amount of time that has passed since {@code startTime}.
     * <p>
     * Differences between {@code startTime} and {@link #currentTime()} that span greater than 2<sup>63</sup> (~292
     * years if {@link #currentTimeUnits()} is {@link TimeUnit#NANOSECONDS}) will not correctly compute elapsed time due
     * to numerical overflow.
     * @param startTime a past value of {@link #currentTime()} which represents the start time stamp.
     * @return the amount of time that has passed since {@code startTime}.
     */
    default Duration duration(long startTime) {
        return Duration.of(currentTime() - startTime, toChronoUnit(currentTimeUnits()));
    }
}
