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
package io.servicetalk.apple.capacity.limiter.api;

import java.time.Duration;

import static java.time.Duration.ZERO;

final class Preconditions {

    private Preconditions() {
        // No instances
    }

    static int checkPositive(String field, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: > 0)");
        }
        return value;
    }

    static float checkPositive(String field, float value) {
        if (value <= 0.0f) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: > 0.0f)");
        }
        return value;
    }

    static float checkGreaterThan(String field, float value, final float min) {
        if (value <= min) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: > " + min + ")");
        }
        return value;
    }

    static float checkBetweenZeroAndOne(String field, float value) {
        if (value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: 0.0f <= " + field + " <= 1.0f)");
        }
        return value;
    }

    static int checkRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + ": " + value +
                    " (expected: " + min + " <= " + field + " <= " + max + ")");
        }
        return value;
    }

    static float checkBetweenZeroAndOneExclusive(String field, float value) {
        if (value <= 0.0f || value >= 1.0f) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: 0.0f < " + field + " < 1.0f)");
        }
        return value;
    }

    static Duration checkZeroOrPositive(String field, Duration value) {
        if (ZERO.compareTo(value) > 0) {
            throw new IllegalArgumentException(field + ": " + value + " (expected: >= " + ZERO + ")");
        }
        return value;
    }
}
