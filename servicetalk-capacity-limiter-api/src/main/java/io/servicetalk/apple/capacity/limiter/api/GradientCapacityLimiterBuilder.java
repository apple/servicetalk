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

import io.servicetalk.apple.capacity.limiter.api.CapacityLimiter.Ticket;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_INITIAL_LIMIT;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_LIMIT_UPDATE_INTERVAL;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_LONG_LATENCY_TRACKER;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_MAX_GRADIENT;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_MAX_LIMIT;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_MIN_GRADIENT;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_MIN_LIMIT;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_ON_DROP;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_ON_LIMIT;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_SHORT_LATENCY_TRACKER;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_SUSPEND_DEC;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.DEFAULT_SUSPEND_INCR;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.GREEDY_HEADROOM;
import static io.servicetalk.apple.capacity.limiter.api.GradientCapacityLimiterProfiles.MIN_SAMPLING_DURATION;
import static io.servicetalk.apple.capacity.limiter.api.Preconditions.checkBetweenZeroAndOne;
import static io.servicetalk.apple.capacity.limiter.api.Preconditions.checkBetweenZeroAndOneExclusive;
import static io.servicetalk.apple.capacity.limiter.api.Preconditions.checkGreaterThan;
import static io.servicetalk.apple.capacity.limiter.api.Preconditions.checkPositive;
import static java.util.Objects.requireNonNull;

/**
 * Builder for the {@link GradientCapacityLimiter} capacity limiter.
 */
public final class GradientCapacityLimiterBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradientCapacityLimiter.class);

    private static final AtomicInteger SEQ_GEN = new AtomicInteger();

    private static final Observer LOGGING_OBSERVER = new Observer() {
        @Override
        public void onStateChange(final int limit, final int consumed) {
            LOGGER.debug("GradientCapacityLimiter: limit {} consumption {}", limit, consumed);
        }

        @Override
        public void onActiveRequestsDecr() {
        }

        @Override
        public void onActiveRequestsIncr() {
        }

        @Override
        public void onLimitChange(final double longRtt, final double shortRtt, final double gradient,
                                  final double oldLimit, final double newLimit) {
            LOGGER.debug("GradientCapacityLimiter: longRtt {} shortRtt {} gradient {} oldLimit {} newLimit {}",
                    longRtt, shortRtt, gradient, oldLimit, newLimit);
        }
    };

    @Nullable
    private String name;
    private int initial = DEFAULT_INITIAL_LIMIT;
    private int min = DEFAULT_MIN_LIMIT;
    private int max = DEFAULT_MAX_LIMIT;
    private float onDrop = DEFAULT_ON_DROP;
    private float onLimit = DEFAULT_ON_LIMIT;
    private float minGradient = DEFAULT_MIN_GRADIENT;
    private float maxGradient = DEFAULT_MAX_GRADIENT;
    private Observer observer = LOGGING_OBSERVER;
    private Duration limitUpdateInterval = DEFAULT_LIMIT_UPDATE_INTERVAL;
    private BiPredicate<Integer, Double> suspendLimitInc = DEFAULT_SUSPEND_INCR;
    private BiPredicate<Integer, Double> suspendLimitDec = DEFAULT_SUSPEND_DEC;
    private BiFunction<Double, Double, Double> headroom = GREEDY_HEADROOM;
    private LongSupplier timeSource = System::nanoTime;
    private LatencyTracker shortLatencyTracker = DEFAULT_SHORT_LATENCY_TRACKER;
    private LatencyTracker longLatencyTracker = DEFAULT_LONG_LATENCY_TRACKER;

    GradientCapacityLimiterBuilder() {
    }

    /**
     * Defines a name for this {@link CapacityLimiter}.
     * @param name the name to be used when building this {@link CapacityLimiter}.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder name(final String name) {
        this.name = requireNonNull(name);
        return this;
    }

    /**
     * Define {@code initial}, {@code min} and {@code max} concurrency limits for this {@link CapacityLimiter}.
     * The active concurrency will fluctuate between these limits starting from the {@code min} and never
     * going beyond {@code max}.
     * <p>
     * The limit translates to a concurrency figure, e.g. how many requests can be in-flight simultaneously and
     * doesn't represent a constant rate (i.e. has no notion of time).
     * <p>
     * The lower the {@code initial} or {@code min} are, the slower the ramp up will be, and the bigger it is the
     * more aggressive the client will be, keep concurrently issuing {@code min} requests to meet this limit.
     * <p>
     * Min must always be less than max, and ideally max should be greater by 10x.
     *
     * @param initial The initial concurrency allowed, helps with faster start.
     * @param min The minimum concurrency allowed.
     * @param max The maximum concurrency allowed.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder limits(final int initial, final int min, final int max) {
        checkPositive("min", min);
        if (initial < min || initial > max) {
            throw new IllegalArgumentException("initial: " + initial + " (expected: min <= initial <= max)");
        }
        if (max <= min) {
            throw new IllegalArgumentException("min: " + min + ", max: " + max + " (expected: min < max)");
        }

        this.initial = initial;
        this.min = min;
        this.max = max;
        return this;
    }

    /**
     * Defines the backoff ratios when certain conditions are met.
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
    public GradientCapacityLimiterBuilder backoffRatio(final float onDrop, final float onLimit) {
        checkBetweenZeroAndOneExclusive("onDrop", onDrop);
        checkBetweenZeroAndOneExclusive("onLimit", onLimit);

        this.onDrop = onDrop;
        this.onLimit = onLimit;
        return this;
    }

    /**
     * A function used to positively influence the new limit when the detected gradient is positive (i.e. &gt; 1.0).
     * This can be used to grow the limit faster when the algorithm is being very conservative. Through testing
     * a good headroom function can be the square root of the gradient (i.e. {@link Math#sqrt(double)}).
     * The input of the function is the newly calculated gradient, and the previous limit in this order.
     *
     * @param headroom An extra padding for the new limit if the algorithm is being very conservative.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder headroom(final BiFunction<Double, Double, Double> headroom) {
        requireNonNull(headroom);
        this.headroom = headroom;
        return this;
    }

    /**
     * Sets the {@link LatencyTracker} for the purposes of tracking the latency changes over a shorter period of time
     * (recent) as required for the Gradient algorithm to deduce changes when compared against a longer period of
     * time (past). The tracker will observe (provided) all latencies observed from all requests that go through the
     * {@link CapacityLimiter}, it's up to the {@link LatencyTracker} which latencies to consider and how big or small
     * the trackable window will be.
     * @param shortLatencyTracker The {@link LatencyTracker} to be used to track the recent latency changes
     * (short window).
     * @return {@code this}.
     */
    GradientCapacityLimiterBuilder shortLatencyTracker(final LatencyTracker shortLatencyTracker) {
        this.shortLatencyTracker = requireNonNull(shortLatencyTracker);
        return this;
    }

    /**
     * Sets the {@link LatencyTracker} for the purposes of tracking the latency changes over a longer period of time
     * (past) as required for the Gradient algorithm to deduce changes when compared against a shorter period of
     * time (recent). The tracker will observe (provided) all latencies observed from all requests that go through the
     * {@link CapacityLimiter}, it's up to the {@link LatencyTracker} which latencies to consider and how big or small
     * the trackable window will be.
     * @param longLatencyTracker The {@link LatencyTracker} to be used to track the history of latencies
     * so far (long exposed window).
     * @return {@code this}.
     */
    GradientCapacityLimiterBuilder longLatencyTracker(final LatencyTracker longLatencyTracker) {
        this.longLatencyTracker = requireNonNull(longLatencyTracker);
        return this;
    }

    /**
     * How often a new sampled RTT must be collected to update the concurrency limit.
     * This interval is part of the normal
     * {@link CapacityLimiter#tryAcquire(Classification, ContextMap)} flow, thus no external
     * {@link java.util.concurrent.Executor} is used. The more traffic goes through the more accurate it will be.
     * As a result this is not a <b>hard</b> deadline, but rather an at-least figure.
     * <p>
     * There is a hard min duration applied of {@code 50ms} that can't be overridden. Any input less than that value
     * will result in a {@link IllegalArgumentException}. That minimum interval was determined experimentally to
     * avoid extreme adjustments of the limit without a cool off period.
     *
     * @param duration The duration between sampling an RTT value.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder limitUpdateInterval(final Duration duration) {
        requireNonNull(duration);
        if (duration.toMillis() < MIN_SAMPLING_DURATION.toMillis()) {
            throw new IllegalArgumentException("Sampling interval " + duration + " (expected > 50ms).");
        }
        this.limitUpdateInterval = duration;
        return this;
    }

    /**
     * Defines the min gradient allowance per {@link #limitUpdateInterval(Duration) sampling-interval}.
     * This helps push the limit upwards by not allowing quick drops, and it tends to maintain that higher limit.
     *
     * @param minGradient The minimum allowed gradient per {@link #limitUpdateInterval(Duration) sampling
     * interval}.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder minGradient(final float minGradient) {
        checkBetweenZeroAndOne("minGradient", minGradient);
        this.minGradient = minGradient;
        return this;
    }

    /**
     * Defines the max gradient allowance per {@link #limitUpdateInterval(Duration) sampling-interval}.
     * This helps limit how fast the limit can grow, allowing the peer to adjust to the change, before the
     * limit grows out of control causing start-stop reactions (saw-tooth patterns in traffic).
     *
     * @param maxPositiveGradient The maximum allowed gradient per {@link #limitUpdateInterval(Duration) sampling
     * interval}.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder maxGradient(final float maxPositiveGradient) {
        checkGreaterThan("maxGradient", maxPositiveGradient, 1.0f);
        this.maxGradient = maxPositiveGradient;
        return this;
    }

    /**
     * A {@link Observer observer} to consume the current state of this {@link CapacityLimiter} when
     * state changes occur. Useful to monitor the limit through logging or metrics, or just debugging.
     * <p>
     * <strong>It's expected that this {@link Observer} is not going to block the thread that invokes it.</strong>
     * @param observer The {@link Observer} to inform about the current capacity and consumption
     * of this {@link CapacityLimiter}.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder observer(final Observer observer) {
        this.observer = requireNonNull(observer);
        return this;
    }

    /**
     * A function to suspend the increase of the limit when that is not consumed
     * (e.g. rate of requests isn't crossing it).
     * This helps prevent the limit growing to infinite, and provides faster reaction when a reduction happens.
     * Additionally, this works as a "blast-radius" concept, effectively limiting spontaneous traffic surges.
     * @param suspendLimitInc The {@link BiPredicate} that should return {@code true} when the limit
     * should halt increasing based on current consumption (first argument as {@link Integer}) and current limit
     * (second argument as {@link Double}). Helper {@link GradientCapacityLimiterUtils#blastRadius(int) API}
     * to offer blast-radius type predicates.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder suspendLimitIncrease(final BiPredicate<Integer, Double> suspendLimitInc) {
        this.suspendLimitInc = requireNonNull(suspendLimitInc);
        return this;
    }

    /**
     * A function to suspend the decrease of the limit when that is not consumed
     * (e.g. rate of requests isn't crossing it).
     * When the monitored RTT is considerably noisy (deviations &gt; 500ms) the limit tends to decrease faster,
     * even when there is no significant utilization of it going on. This helps prevent the decrease,
     * until the provided {@link Predicate} toggles it.
     * @param suspendLimitDec The {@link BiPredicate} that should return {@code true} when the limit
     * should halt decreasing based on current consumption (first argument as {@link Integer}) and current limit
     * (second argument as {@link Double}). Helper {@link GradientCapacityLimiterUtils#occupancyFactor(float) API}
     * to offer occupancy-factor type predicates.
     * @return {@code this}.
     */
    public GradientCapacityLimiterBuilder suspendLimitDecrease(final BiPredicate<Integer, Double> suspendLimitDec) {
        this.suspendLimitDec = requireNonNull(suspendLimitDec);
        return this;
    }

    // For testing only
    GradientCapacityLimiterBuilder timeSource(final LongSupplier timeSource) {
        this.timeSource = requireNonNull(timeSource);
        return this;
    }

    /**
     * Builds a Gradient dynamic {@link CapacityLimiter} based on config options of this builder.
     *
     * @return A dynamic {@link CapacityLimiter} based on the options of {@code this} builder.
     */
    public CapacityLimiter build() {
        return new GradientCapacityLimiter(name(), min, max, initial, onLimit, onDrop, shortLatencyTracker,
                longLatencyTracker, limitUpdateInterval, minGradient, maxGradient, observer,
                suspendLimitInc, suspendLimitDec, headroom, timeSource);
    }

    @Nonnull
    private String name() {
        return name == null ? GradientCapacityLimiter.class.getSimpleName() + "_" + SEQ_GEN.incrementAndGet() : name;
    }

    /**
     * A state observer for Gradient {@link CapacityLimiter} to monitor internal state changes.
     */
    public interface Observer {
        /**
         * Callback that gives access to internal state of the Gradient {@link CapacityLimiter}.
         * Useful to capture all consumption changes along with the limit in use, but can be very noisy,
         * since consumption changes twice in the lifecycle of a {@link Ticket}.
         * <p>
         * The rate of reporting to the observer is based on the rate of change to this
         * {@link CapacityLimiter}.
         * @param limit The current limit (dynamically computed) of the limiter.
         * @param consumed The current consumption (portion of the limit) of the limiter.
         * @deprecated alternative for consumed available through {@link #onActiveRequestsIncr}
         * and {@link #onActiveRequestsDecr}, similarly alternative for limit changes available through
         * {@link #onLimitChange(double, double, double, double, double)}.
         */
        @Deprecated
        void onStateChange(int limit, int consumed);

        /**
         * Callback that informs when the active requests increased by 1.
         */
        void onActiveRequestsIncr();

        /**
         * Callback that informs when the active requests decreased by 1.
         */
        void onActiveRequestsDecr();

        /**
         * A limit observer callback, to consume the trigger state of this {@link CapacityLimiter} when a limit change
         * happens. Useful to monitor the limit changes and their triggers.
         * <p>
         * The rate of reporting to the observer is based on the rate of change to this
         * {@link CapacityLimiter} and the {@link #limitUpdateInterval(Duration) sampling interval}.
         * @param longRtt The exponential moving average stat of request response times.
         * @param shortRtt The sampled response time that triggered the limit change.
         * @param gradient The response time gradient (delta) between the long exposed stat (see. longRtt)
         * and the sampled response time (see. shortRtt).
         * @param oldLimit The previous limit of the limiter.
         * @param newLimit The current limit of the limiter.
         */
        void onLimitChange(double longRtt, double shortRtt, double gradient, double oldLimit, double newLimit);
    }
}
