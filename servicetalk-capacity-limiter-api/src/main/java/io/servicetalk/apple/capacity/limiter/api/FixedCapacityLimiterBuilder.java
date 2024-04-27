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

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A builder for fixed capacity {@link CapacityLimiter}.
 */
public final class FixedCapacityLimiterBuilder {

    private static final AtomicInteger SEQ_GEN = new AtomicInteger();
    private int capacity;
    @Nullable
    private String name;
    @Nullable
    private StateObserver observer;

    /**
     * Defines a name for this {@link CapacityLimiter}.
     * @param name the name to be used when building this {@link CapacityLimiter}.
     * @return {@code this}.
     */
    public FixedCapacityLimiterBuilder name(final String name) {
        this.name = requireNonNull(name);
        return this;
    }

    /**
     * Defines the fixed capacity for the {@link CapacityLimiter}.
     * Concurrent requests above this figure will be rejected. Requests with particular
     * {@link Classification#weight() weight} will be respected and the total capacity for them will be adjusted
     * accordingly.
     * @param capacity The max allowed concurrent requests that this {@link CapacityLimiter} should allow.
     * @return {@code this}.
     */
    public FixedCapacityLimiterBuilder capacity(final int capacity) {
        this.capacity = capacity;
        return this;
    }

    /**
     * A {@link StateObserver observer} to consume the current consumed capacity.
     * Useful for logging, metrics, or just debugging.
     * <p>
     * The rate of reporting limit and consumption to the observer is based on the rate of change to
     * this {@link CapacityLimiter}.
     * <p>
     * <strong>It's expected that this {@link StateObserver} is not going to block the thread that invokes it.</strong>
     * @param observer The {@link StateObserver} to inform about the current consumption of
     * this {@link CapacityLimiter}.
     * @return {@code this}.
     */
    public FixedCapacityLimiterBuilder stateObserver(final StateObserver observer) {
        this.observer = requireNonNull(observer);
        return this;
    }

    /**
     * Build a fixed capacity {@link CapacityLimiter} according to this configuration.
     * @return A new instance of {@link CapacityLimiter} according to this configuration.
     */
    public CapacityLimiter build() {
        return new FixedCapacityLimiter(name(), capacity, observer);
    }

    @Nonnull
    private String name() {
        return name == null ? FixedCapacityLimiter.class.getSimpleName() + "_" + SEQ_GEN.incrementAndGet() : name;
    }

    /**
     * A state observer for the fixed {@link CapacityLimiter} to monitor internal limit and consumption.
     */
    @FunctionalInterface
    public interface StateObserver {
        /**
         * Callback that gives access to internal state of the {@link CapacityLimiter} with fixed capacity.
         *
         * @param consumed The current consumption (portion of the capacity) of the limiter.
         * @deprecated Use {@link #observe(int, int)}.
         */
        @Deprecated // FIXME: 0.43 - remove deprecated method or change default impl
        void observe(int consumed);

        /**
         * Callback that gives access to internal state of the {@link CapacityLimiter} with fixed capacity.
         *
         * @param capacity The max allowed concurrent requests that {@link CapacityLimiter} should allow.
         * @param consumed The current consumption (portion of the capacity) of the limiter.
         */
        default void observe(int capacity, int consumed) {
            observe(consumed);
        }
    }
}
