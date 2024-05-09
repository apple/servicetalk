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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensureBetweenZeroAndOneExclusive;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static io.servicetalk.utils.internal.NumberUtils.ensureRange;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;

/**
 * Builder for the {@link AimdCapacityLimiter} capacity limiter.
 */
public final class AimdCapacityLimiterBuilder {

    private static final int DEFAULT_INITIAL_LIMIT = 50;
    private static final int DEFAULT_MIN_LIMIT = 1;
    private static final int DEFAULT_MAX_LIMIT = MAX_VALUE;
    private static final float DEFAULT_ON_DROP = .8f;
    private static final float DEFAULT_ON_LIMIT = .5f;
    private static final float DEFAULT_INCREMENT = 1f;
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMillis(100);
    private static final AtomicInteger SEQ_GEN = new AtomicInteger();

    private int initial = DEFAULT_INITIAL_LIMIT;
    private int min = DEFAULT_MIN_LIMIT;
    private int max = DEFAULT_MAX_LIMIT;
    private float onDrop = DEFAULT_ON_DROP;
    private float onLimit = DEFAULT_ON_LIMIT;
    private float increment = DEFAULT_INCREMENT;
    private Duration cooldown = DEFAULT_COOLDOWN;
    @Nullable
    private StateObserver stateObserver;
    private LongSupplier timeSource = System::nanoTime;
    @Nullable
    private String name;

    AimdCapacityLimiterBuilder() {
    }

    /**
     * Defines a name for this {@link CapacityLimiter}.
     * @param name the name to be used when building this {@link CapacityLimiter}.
     * @return {@code this}.
     */
    public AimdCapacityLimiterBuilder name(final String name) {
        this.name = requireNonNull(name);
        return this;
    }

    /**
     * Define {@code min} and {@code max} concurrency limits for this {@link CapacityLimiter}.
     * The active concurrency will fluctuate between these limits starting from the {@code min} and never
     * going beyond {@code max}. AIMD will keep incrementing the limit by 1 everytime a successful response is
     * received, and will decrement by the {@code onDrop} {@link #limits(int, int, int)} ratio,
     * for every {@code dropped} request (i.e. rejected or timeout).
     * <p>
     * The limit translates to a concurrency figure, eg. how many requests can be in-flight simultaneously and
     * doesn't represent a constant rate (i.e. has no notion of time).*
     * <p>
     * The lower the {@code min} is, the slower the ramp up will be, and the bigger it is the more aggressive the
     * service will be, keep concurrently issuing {@code min} requests to meet this limit. The defaults are within
     * sane ranges, but depending on the number of clients hitting a service, you may want to decrease the
     * {@code min} even further.
     * <p>
     * Min must always be less than max, and ideally max should be greater by 10x.
     *
     * @param initial The initial concurrency allowed, helps with faster start.
     * @param min The minimum concurrency allowed, this can not be less than {@code 1} to allow progress.
     * @param max The maximum concurrency allowed.
     * @return {@code this}.
     */
    public AimdCapacityLimiterBuilder limits(final int initial, final int min, final int max) {
        if (min < 1) {
            throw new IllegalArgumentException("min: " + min + " (expected: >= 1)");
        }
        if (max <= min) {
            throw new IllegalArgumentException("min: " + min + ", max: " + max + " (expected: min < max)");
        }

        this.initial = ensureRange(initial, min, max, "initial");
        this.min = min;
        this.max = max;
        return this;
    }

    /**
     * Defines the backoff ratios for AIMD.
     * Ratios are used to alter the limit of the {@link CapacityLimiter} by the provided multiplier on different
     * conditions as identified by their name.
     *
     * <p>
     * The formula for the backoff ratio used is: {@code NewLimit = OldLimit * BackoffRatio}, always respecting the
     * {@link #min} and {@link #max} values.
     *
     * <p>
     * Both limits must be between 0 and 1 exclusively.
     *
     * @param onDrop The backoff ratio used to bring the limit down by that amount, when a request is dropped
     * either by a server response identified as a rejection, or by a local timeout.
     * @param onLimit The backoff ratio used to bring the limit down by that amount, when the maximum limit is
     * reached.
     * @return {@code this}.
     */
    public AimdCapacityLimiterBuilder backoffRatio(final float onDrop, final float onLimit) {
        this.onDrop = ensureBetweenZeroAndOneExclusive(onDrop, "onDrop");
        this.onLimit = ensureBetweenZeroAndOneExclusive(onLimit, "onLimit");
        return this;
    }

    /**
     * Defines the additive factor of this algorithm.
     * Tuning this preference allows to control the speed that the limit can grow within a certain
     * {@link #cooldown(Duration) cool-down period}.
     *
     * @param increment The incremental step of the limit during a successful response after a cool-down period.
     * @return {@code this}.
     */
    public AimdCapacityLimiterBuilder increment(final float increment) {
        this.increment = ensurePositive(increment, "increment");
        return this;
    }

    /**
     * Defines a period during which the additive part of the algorithm doesn't kick-in.
     * This period helps to allow the transport to adjust on the new limits before more adjustments happen. Tuning
     * this allows more stable limits rather than continuous increases and decreases.
     *
     * @param duration The period during which no more additive adjustments will take place.
     * @return {@code this}.
     */
    public AimdCapacityLimiterBuilder cooldown(final Duration duration) {
        this.cooldown = ensureNonNegative(duration, "cooldown");
        return this;
    }

    /**
     * A {@link StateObserver observer} to consume the current limit of this {@link CapacityLimiter} and its consumed
     * capacity, respectively. Useful to monitor the limit through logging or metrics, or just debugging.
     * <p>
     * The rate of reporting limit and consumption to the observer is based on the rate of change to
     * this {@link CapacityLimiter}.
     * <p>
     * <strong>It's expected that this {@link StateObserver} is not going to block the thread that invokes it.</strong>
     * @param observer The {@link StateObserver} to inform about the current capacity and consumption
     * of this {@link CapacityLimiter}.
     * @return {@code this}.
     */
    public AimdCapacityLimiterBuilder stateObserver(final StateObserver observer) {
        this.stateObserver = requireNonNull(observer);
        return this;
    }

    /**
     * For testing only.
     */
    AimdCapacityLimiterBuilder timeSource(final LongSupplier timeSource) {
        this.timeSource = requireNonNull(timeSource);
        return this;
    }

    /**
     * Builds an AIMD dynamic {@link CapacityLimiter} based on config options of this builder.
     *
     * @return A dynamic {@link CapacityLimiter} based on the options of {@code this} builder.
     */
    public CapacityLimiter build() {
        return new AimdCapacityLimiter(name(), min, max, initial, increment, onLimit, onDrop, cooldown, stateObserver,
                timeSource);
    }

    private String name() {
        return name == null ? AimdCapacityLimiter.class.getSimpleName() + '-' + SEQ_GEN.incrementAndGet() : name;
    }

    /**
     * A state observer for AIMD {@link CapacityLimiter} to monitor internal limit and consumption.
     */
    @FunctionalInterface
    public interface StateObserver {
        /**
         * Callback that gives access to internal state of the AIMD {@link CapacityLimiter}.
         *
         * @param limit The current limit (dynamically computed) of the limiter.
         * @param consumed The current consumption (portion of the limit) of the limiter.
         */
        void observe(int limit, int consumed);
    }
}
