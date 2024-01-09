/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.IntBinaryOperator;
import java.util.function.LongSupplier;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;
import static java.lang.Math.ceil;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Latency tracker using exponential weighted moving average based on the work by Andreas Eckner and subsequent
 * modifications under PeakEWMA in <a href= "https://github.com/twitter/finagle"> finagle</a>.
 *
 * @see <a href="http://www.eckner.com/papers/Algorithms%20for%20Unevenly%20Spaced%20Time%20Series.pdf"> Eckner
 * (2019) Algorithms for Unevenly Spaced Time Series: Moving Averages and Other Rolling Operators (4.2 pp. 10)</a>
 */
final class DefaultLatencyTracker implements LatencyTracker {
    private static final AtomicIntegerFieldUpdater<DefaultLatencyTracker> pendingUpdater =
            newUpdater(DefaultLatencyTracker.class, "pending");
    private static final long MAX_MS_TO_NS = NANOSECONDS.convert(MAX_VALUE, MILLISECONDS);
    private static final long DEFAULT_CANCEL_PENALTY = 5L;
    private static final long DEFAULT_ERROR_PENALTY = 10L;
    /**
     * Mean lifetime, exponential decay.
     */
    private final double inv_tau;
    private final LongSupplier currentTimeSupplier;
    private final long cancelPenalty;
    private final long errorPenalty;

    /**
     * Last inserted value to compute weight.
     */
    private long lastTimeNanos;
    /**
     * Current weighted average.
     */
    private int ewma;
    private volatile int pending;

    DefaultLatencyTracker(final long halfLifeNanos, final LongSupplier currentTimeSupplier) {
        this(halfLifeNanos, currentTimeSupplier, DEFAULT_CANCEL_PENALTY, DEFAULT_ERROR_PENALTY);
    }

    DefaultLatencyTracker(final long halfLifeNanos, final LongSupplier currentTimeSupplier,
                          long cancelPenaly, long errorPenaly) {
        if (halfLifeNanos <= 0) {
            throw new IllegalArgumentException("halfLifeNanos: " + halfLifeNanos + " (expected >0)");
        }
        this.inv_tau = Math.pow((halfLifeNanos / log(2)), -1);
        this.currentTimeSupplier = currentTimeSupplier;
        this.lastTimeNanos = currentTimeSupplier.getAsLong();
        this.cancelPenalty = cancelPenaly;
        this.errorPenalty = errorPenaly;
    }

    @Override
    public long beforeStart() {
        pendingUpdater.incrementAndGet(this);
        return currentTimeSupplier.getAsLong();
    }

    @Override
    public void observeSuccess(final long startTimeNanos) {
        pendingUpdater.decrementAndGet(this);
        calculateAndStore((ewma, currentLatency) -> currentLatency, startTimeNanos);
    }

    @Override
    public void observeCancel(final long startTimeNanos) {
        pendingUpdater.decrementAndGet(this);
        calculateAndStore(this::cancelPenalty, startTimeNanos);
    }

    @Override
    public void observeError(final long startTimeNanos) {
        pendingUpdater.decrementAndGet(this);
        calculateAndStore(this::errorPenalty, startTimeNanos);
    }

    @Override
    public int score() {
        final int currentEWMA = calculateAndStore((ewma, lastTimeNanos) -> 0, 0);
        final int cPending = pendingUpdater.get(this);
        if (currentEWMA == 0) {
            // If EWMA has decayed to 0 (or isn't yet initialized) and there are no pending requests we return the
            // maximum score to increase the likelihood this entity is selected. If there are pending requests we
            // don't yet know the latency characteristics so we return the minimum score to decrease the
            // likelihood this entity is selected.
            return cPending == 0 ? 0 : MIN_VALUE;
        }

        // Add penalty for pending requests to account for "unaccounted" load.
        // Penalty is the observed latency if known, else an arbitrarily high value which makes entities for which
        // no latency data has yet been received (eg: request sent but not received), un-selectable.
        final int pendingPenalty = (int) min(MAX_VALUE, (long) cPending * currentEWMA);
        // Since we are measuring latencies and lower latencies are better, we turn the score as negative such that
        // lower the latency, higher the score.
        return MAX_VALUE - currentEWMA <= pendingPenalty ? MIN_VALUE : -(currentEWMA + pendingPenalty);
    }

    private int cancelPenalty(int currentEWMA, int currentLatency) {
        // There is no significance to the choice of this multiplier (other than it is half of the error penalty)
        // and it is selected to gather empirical evidence as the algorithm is evaluated.
        return applyPenalty(currentEWMA, currentLatency, cancelPenalty);
    }

    private int errorPenalty(int currentEWMA, int currentLatency) {
        // There is no significance to the choice of this multiplier (other than it is double of the cancel penalty)
        // and it is selected to gather empirical evidence as the algorithm is evaluated.
        return applyPenalty(currentEWMA, currentLatency, errorPenalty);
    }

    private static int applyPenalty(int currentEWMA, int currentLatency, long penalty) {
        // Relatively large latencies will have a bigger impact on the penalty, while smaller latencies (e.g. premature
        // cancel/error) rely on the penalty.
        return (int) min(MAX_VALUE, max(currentEWMA, currentLatency) * penalty);
    }

    private synchronized int calculateAndStore(final IntBinaryOperator latencyInitializer, long startTimeNanos) {
        final int nextEWMA;
        // We capture the current time inside the synchronized block to exploit the monotonic time source
        // properties which prevent the time duration from going negative. This will result in a latency penalty
        // as concurrency increases, but is a trade-off for simplicity.
        final long currentTimeNanos = currentTimeSupplier.getAsLong();
        // When modifying the EWMA and lastTime we read/write both values in a synchronized block as they are
        // tightly coupled in the EWMA formula below.
        final int currentEWMA = ewma;
        final int currentLatency = latencyInitializer.applyAsInt(ewma, nanoToMillis(currentTimeNanos - startTimeNanos));
        // Note the currentLatency cannot be <0 or else the EWMA equation properties are violated
        // (e.g. "degree of weighting decrease" is not in [0, 1]).
        assert currentLatency >= 0;

        // Peak EWMA from finagle for the score to be extremely sensitive to higher than normal latencies.
        if (currentLatency > currentEWMA) {
            nextEWMA = currentLatency;
        } else {
            final double tmp = (currentTimeNanos - lastTimeNanos) * inv_tau;
            final double w = exp(-tmp);
            nextEWMA = (int) ceil(currentEWMA * w + currentLatency * (1d - w));
        }
        lastTimeNanos = currentTimeNanos;
        ewma = nextEWMA;
        return nextEWMA;
    }

    private static int nanoToMillis(long nanos) {
        return (int) MILLISECONDS.convert(min(nanos, MAX_MS_TO_NS), NANOSECONDS);
    }
}
