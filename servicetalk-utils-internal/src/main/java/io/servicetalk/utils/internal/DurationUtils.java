/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.utils.internal;

import java.time.Duration;

import static java.time.Duration.ZERO;
import static java.util.Objects.requireNonNull;

/**
 * Helper utilities for {@link Duration}.
 */
public final class DurationUtils {

    private DurationUtils() {
        // No instances
    }

    /**
     * Checks if the duration is positive, excluding zero.
     *
     * @param duration the {@link Duration} to validate
     * @return {@code true} if the passed duration is greater than {@link Duration#ZERO}, {@code false} otherwise
     */
    public static boolean isPositive(final Duration duration) {
        return ZERO.compareTo(duration) < 0;
    }

    /**
     * Ensures the duration is positive, excluding zero.
     *
     * @param duration the {@link Duration} to validate
     * @param name name of the {@link Duration} variable
     * @return the passed duration if all checks pass
     * @throws NullPointerException if the passed duration is {@code null}
     * @throws IllegalArgumentException if the passed duration is not greater than {@link Duration#ZERO}
     */
    public static Duration ensurePositive(final Duration duration, final String name) {
        if (!isPositive(requireNonNull(duration, name))) {
            throw new IllegalArgumentException(name + ": " + duration + " (expected > 0)");
        }
        return duration;
    }
}
