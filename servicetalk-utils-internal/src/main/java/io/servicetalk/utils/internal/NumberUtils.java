/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

/**
 * Helper utilities for {@link Number}s.
 */
public final class NumberUtils {

    private NumberUtils() {
        // No instances
    }

    /**
     * Ensures the int is positive, excluding zero.
     *
     * @param value the int value to validate
     * @param name name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed int is not greater than zero
     */
    public static int ensurePositive(final int value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + ": " + value + " (expected > 0)");
        }
        return value;
    }

    /**
     * Ensures the float is positive, excluding zero.
     *
     * @param value the float value to validate
     * @param name name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed float is not greater than zero
     */
    public static float ensurePositive(final float value, final String name) {
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + ": " + value + " (expected > 0.0)");
        }
        return value;
    }

    /**
     * Ensures the long is positive, excluding zero.
     *
     * @param value the long value to validate
     * @param name name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed long is not greater than zero
     */
    public static long ensurePositive(final long value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + ": " + value + " (expected > 0)");
        }
        return value;
    }

    /**
     * Ensures the int is non-negative.
     *
     * @param value the int value to validate
     * @param name name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed int is less than zero
     */
    public static int ensureNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + ": " + value + " (expected >= 0)");
        }
        return value;
    }

    /**
     * Ensures the long is non-negative.
     *
     * @param value the long value to validate
     * @param name name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed long is less than zero
     */
    public static long ensureNonNegative(final long value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + ": " + value + " (expected >= 0)");
        }
        return value;
    }
}
