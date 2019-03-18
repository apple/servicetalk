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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A set of utility methods for safe math operations to prevent overflow.
 */
public final class FlowControlUtil {

    private FlowControlUtil() {
        // no instances
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
     * Subtract {@code y} from {@code x} if {@code x} is positive.
     * @param x first value (may be negative).
     * @param y second value (should be positive).
     * @return the result of {@code x-y} if {@code x>0}, or {@code x}.
     */
    public static long subtractIfPositive(long x, long y) {
        return x <= 0 ? x : x - y;
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
     * Increment an {@code integer} referred by {@link AtomicIntegerFieldUpdater} atomically
     * and saturate to {@link Integer#MAX_VALUE} if overflow occurs.
     *
     * @param updater {@link AtomicIntegerFieldUpdater} used to atomically increment.
     * @param owner Owner class of {@link AtomicIntegerFieldUpdater}.
     * @param amount Amount to increment.
     * @param <T> Type of the owner.
     *
     * @return Value of {@code int} referred by {@link AtomicIntegerFieldUpdater} after the increment.
     */
    public static <T> int addWithOverflowProtection(AtomicIntegerFieldUpdater<T> updater, T owner, int amount) {
        return updater.accumulateAndGet(owner, amount, FlowControlUtil::addWithOverflowProtection);
    }

    /**
     * Increment a {@code long} referred by {@link AtomicLongFieldUpdater} atomically
     * and saturate to {@link Long#MAX_VALUE} if overflow occurs.
     *
     * @param updater {@link AtomicLongFieldUpdater} used to atomically increment.
     * @param owner Owner class of {@link AtomicLongFieldUpdater}.
     * @param amount Amount to increment.
     * @param <T> Type of the owner.
     *
     * @return Value of {@code long} referred by {@link AtomicLongFieldUpdater} after the increment.
     */
    public static <T> long addWithOverflowProtection(AtomicLongFieldUpdater<T> updater, T owner, long amount) {
        return updater.accumulateAndGet(owner, amount, FlowControlUtil::addWithOverflowProtection);
    }
}
