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
package io.servicetalk.capacity.limiter.api;

/**
 * Number utilities
 */
final class Preconditions {

    private Preconditions() {
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
     * Ensures the float is greater than the min specified.
     *
     * @param value the float value to validate
     * @param min the float min to validate against
     * @param field name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed float doesn't meet the requirements
     */
    public static float ensureGreaterThan(final float value, final float min, final String field) {
        if (value <= min) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: > " + min + ")");
        }
        return value;
    }

    /**
     * Ensures the float is between 0 and 1 (inclusive).
     *
     * @param value the float value to validate
     * @param field name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed float doesn't meet the requirements
     */
    public static float ensureBetweenZeroAndOne(final float value, final String field) {
        if (value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: 0.0f <= " + field + " <= 1.0f)");
        }
        return value;
    }

    /**
     * Ensures the int is between the provided range (inclusive).
     *
     * @param value the int value to validate
     * @param min the min int value to validate against (inclusive)
     * @param max the max int value to validate against (inclusive)
     * @param field name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed int doesn't meet the requirements
     */
    public static int ensureRange(final int value, final int min, final int max, final String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + ": " + value +
                    " (expected: " + min + " <= " + field + " <= " + max + ")");
        }
        return value;
    }

    /**
     * Ensures the float is between 0 and 1 (exclusive).
     *
     * @param value the float value to validate
     * @param field name of the variable
     * @return the passed value if all checks pass
     * @throws IllegalArgumentException if the passed float doesn't meet the requirements
     */
    public static float ensureBetweenZeroAndOneExclusive(final float value, final String field) {
        if (value <= 0.0f || value >= 1.0f) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: 0.0f < " + field + " < 1.0f)");
        }
        return value;
    }
}
