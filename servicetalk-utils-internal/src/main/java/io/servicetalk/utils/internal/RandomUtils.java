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
package io.servicetalk.utils.internal;

import static java.util.concurrent.ThreadLocalRandom.current;

/**
 * Internal random utilities.
 */
public final class RandomUtils {

    private RandomUtils() {
        // no instances
    }

    /**
     * Generate a random long between 0 and the upper bound, both inclusive.
     * @param upperBound the inclusive upper bound.
     * @return a random long between 0 and the upper bound, both inclusive.
     */
    public static long nextLongInclusive(final long upperBound) {
        return nextLongInclusive(0, upperBound);
    }

    /**
     * Generate a random long between the specified lower and upper bound, both inclusive.
     * @param lowerBound the inclusive lower bound.
     * @param upperBound the inclusive upper bound.
     * @return a random long between the specified lower and upper bound, both inclusive.
     */
    public static long nextLongInclusive(final long lowerBound, final long upperBound) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Lower bound cannot be greater than upper bound.");
        }
        if (upperBound == Long.MAX_VALUE) {
            if (lowerBound == Long.MIN_VALUE) {
                // full range possible which is the simply `nextLong()`.
                return current().nextLong();
            } else {
                // be inclusive of MAX_VALUE by shifting lower bound 1 lower and adding 1 to the result.
                return current().nextLong(lowerBound - 1, Long.MAX_VALUE) + 1;
            }
        } else {
            return current().nextLong(lowerBound, upperBound + 1);
        }
    }
}
