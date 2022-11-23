/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

/**
 * A set of utility methods for safe math operations to prevent overflow.
 */
public final class FlowControlUtils {

    private FlowControlUtils() {
        // no instances
    }

    /**
     * If {@code x} is {@code >=-1} this method behaves the same as {@link #addWithOverflowProtection(long, long)}.
     * If {@code x} is {@code <-1} then {@code x} is returned.
     * @param x first value (may be negative).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Long#MAX_VALUE} if overflow occurs, or {@code x} if {@code x<-1}.
     */
    public static long addWithOverflowProtectionIfGtEqNegativeOne(long x, long y) {
        return x < -1 ? x : addWithOverflowProtection(x, y);
    }

    /**
     * If {@code x} is non-negative this method behaves the same as {@link #addWithOverflowProtection(long, long)}.
     * If {@code x} is negative then {@code x} is returned.
     * @param x first value (may be negative).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Long#MAX_VALUE} if overflow occurs, or {@code x} if {@code x} is
     * negative.
     */
    public static long addWithOverflowProtectionIfNotNegative(long x, long y) {
        return x < 0 ? x : addWithOverflowProtection(x, y);
    }

    /**
     * If {@code x} is non-negative this method behaves the same as {@link #addWithOverflowProtection(int, int)}.
     * If {@code x} is negative then {@code x} is returned.
     * @param x first value (may be negative).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Integer#MAX_VALUE} if overflow occurs, or {@code x} if {@code x} is
     * negative.
     */
    public static int addWithOverflowProtectionIfNotNegative(int x, int y) {
        return x < 0 ? x : addWithOverflowProtection(x, y);
    }

    /**
     * If {@code x} is positive this method behaves the same as {@link #addWithOverflowProtection(int, int)}.
     * If {@code x} is negative or zero then {@code x} is returned.
     * @param x first value (may be negative).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Integer#MAX_VALUE} if overflow occurs, or {@code x} if {@code x} is
     * negative.
     */
    public static int addWithOverflowProtectionIfPositive(int x, int y) {
        return x <= 0 ? x : addWithOverflowProtection(x, y);
    }

    /**
     * If {@code x} is positive this method behaves the same as {@link #addWithOverflowProtection(long, long)}.
     * If {@code x} is negative or zero then {@code x} is returned.
     * @param x first value (may be negative).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Long#MAX_VALUE} if overflow occurs, or {@code x} if {@code x} is
     * negative.
     */
    public static long addWithOverflowProtectionIfPositive(long x, long y) {
        return x <= 0 ? x : addWithOverflowProtection(x, y);
    }

    /**
     * Adds two positive longs and returns {@link Long#MAX_VALUE} if overflow occurs.
     * @param x first value (should be positive).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Long#MAX_VALUE} if overflow occurs.
     */
    public static long addWithOverflowProtection(long x, final long y) {
        //noinspection SuspiciousNameCombination
        x += y;
        return x >= 0 ? x : Long.MAX_VALUE;
    }

    /**
     * Adds two positive ints and returns {@link Integer#MAX_VALUE} if overflow occurs.
     * @param x first value (should be positive).
     * @param y second value (should be positive).
     * @return The result of {@code x+y} or {@link Integer#MAX_VALUE} if overflow occurs.
     */
    public static int addWithOverflowProtection(int x, final int y) {
        //noinspection SuspiciousNameCombination
        x += y;
        return x >= 0 ? x : Integer.MAX_VALUE;
    }

    /**
     * Add two longs and prevent [under|over]flow which is defined as if both {@code x} and {@code y} have the same sign
     * but the result of {@code x + y} has a different sign.
     * @param x first value.
     * @param y second value.
     * @return
     * <ul>
     *     <li>{@code x + y} if no overflow</li>
     *     <li>{@link Long#MAX_VALUE} if overflow in the positive direction</li>
     *     <li>{@link Long#MIN_VALUE} if otherwise in the negative direction</li>
     * </ul>
     */
    public static long addWithUnderOverflowProtection(final long x, final long y) {
        final long sum = x + y;
        // if overflow, sign extended right shift, then flip lower 63 bits (non-sign bits) to get 2s complement min/max.
        return ((x ^ sum) & (y ^ sum)) < 0 ? ((x >> 63) ^ Long.MAX_VALUE) : sum;
    }

    /**
     * Add two longs and prevent [under|over]flow which is defined as if both {@code x} and {@code y} have the same sign
     * but the result of {@code x + y} has a different sign.
     * @param x first value.
     * @param y second value.
     * @return
     * <ul>
     *     <li>{@code x + y} if no overflow</li>
     *     <li>{@link Integer#MAX_VALUE} if overflow in the positive direction</li>
     *     <li>{@link Integer#MIN_VALUE} if otherwise in the negative direction</li>
     * </ul>
     */
    public static int addWithUnderOverflowProtection(final int x, final int y) {
        final int sum = x + y;
        // if overflow, sign extended right shift, then flip lower 63 bits (non-sign bits) to get 2s complement min/max.
        return ((x ^ sum) & (y ^ sum)) < 0 ? ((x >> 31) ^ Integer.MAX_VALUE) : sum;
    }
}
