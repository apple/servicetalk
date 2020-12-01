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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class TimeUtils {
    private TimeUtils() {
    }

    // Replace with TimeUnit#toChronoUnit for JDK9+
    static ChronoUnit toChronoUnit(TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
            default:
                throw new AssertionError();
        }
    }

    // Replace with TimeUnit#convert in JDK11
    static long convert(TimeUnit unit, Duration duration) {
        switch (unit) {
            case NANOSECONDS:
                return duration.toNanos();
            case MICROSECONDS:
                return MICROSECONDS.convert(duration.toNanos(), NANOSECONDS);
            case MILLISECONDS:
                return duration.toMillis();
            case SECONDS:
                return duration.getSeconds();
            case MINUTES:
                return duration.toMinutes();
            case HOURS:
                return duration.toHours();
            case DAYS:
                return duration.toDays();
            default:
                throw new AssertionError();
        }
    }
}
