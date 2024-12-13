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

import io.servicetalk.client.api.ScoreSupplier;

import java.util.concurrent.locks.StampedLock;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.lang.Math.ceil;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Latency tracker using exponential weighted moving average based on the work by Andreas Eckner and subsequent
 * modifications under PeakEWMA in <a href= "https://github.com/twitter/finagle"> finagle</a>.
 *
 * @see <a href="http://www.eckner.com/papers/Algorithms%20for%20Unevenly%20Spaced%20Time%20Series.pdf"> Eckner
 * (2019) Algorithms for Unevenly Spaced Time Series: Moving Averages and Other Rolling Operators (4.2 pp. 10)</a>
 */
abstract class DefaultRequestTracker implements RequestTracker, ScoreSupplier {
    private static final long MAX_MS_TO_NS = NANOSECONDS.convert(Integer.MAX_VALUE, MILLISECONDS);

    private final StampedLock lock = new StampedLock();
    /**
     * Mean lifetime, exponential decay. inverted tau
     */
    private final double invTau;
    private final int cancelPenalty;
    private final int errorPenalty;
    private final int concurrentRequestPenalty;

    /**
     * Last inserted value to compute weight.
     */
    private long lastTimeNanos;
    /**
     * Current weighted average.
     */
    private int ewma;
    private int concurrentCount;
    private long concurrentStamp = Long.MIN_VALUE;

    DefaultRequestTracker(final long halfLifeNanos, final int cancelPenalty, final int errorPenalty,
                          final int concurrentRequestPenalty) {
        ensureNonNegative(halfLifeNanos, "halfLifeNanos");
        this.invTau = halfLifeNanos == 0 ? Double.MAX_VALUE : Math.pow((halfLifeNanos / log(2)), -1);
        this.cancelPenalty = cancelPenalty;
        this.errorPenalty = errorPenalty;
        this.concurrentRequestPenalty = concurrentRequestPenalty;
    }

    /**
     * The current time in nanoseconds.
     * @return the current time in nanoseconds.
     */
    abstract long currentTimeNanos();

    @Override
    public final long beforeRequestStart() {
        final long stamp = lock.writeLock();
        try {
            long timestamp = currentTimeNanos();
            concurrentCount++;
            if (concurrentStamp == Long.MIN_VALUE) {
                // only update the concurrent timestamp if it doesn't already have a value.
                concurrentStamp = timestamp;
            }
            return timestamp;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void onRequestSuccess(final long startTimeNanos) {
        onComplete(startTimeNanos, 0);
    }

    @Override
    public void onRequestError(final long startTimeNanos, ErrorClass errorClass) {
        onComplete(startTimeNanos, errorClass == ErrorClass.CANCELLED ? cancelPenalty : errorPenalty);
    }

    private void onComplete(final long startTimeNanos, int penalty) {
        final long stamp = lock.writeLock();
        try {
            concurrentCount--;
            // Unconditionally clear the timestamp because we don't know which request set it. This is an acceptable
            // 'error' since otherwise we need to keep a collection of start timestamps.
            concurrentStamp = Long.MIN_VALUE;
            updateEwma(penalty, startTimeNanos);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public final int score() {
        final long lastTimeNanos;
        final int concurrentCount;
        final long concurrentStamp;
        int currentEWMA;
        // read all the relevant state using the read lock
        final long stamp = lock.readLock();
        try {
            currentEWMA = ewma;
            lastTimeNanos = this.lastTimeNanos;
            concurrentCount = this.concurrentCount;
            concurrentStamp = this.concurrentStamp;
        } finally {
            lock.unlockRead(stamp);
        }
        // It's fine to get this after releasing the lock since it will still happen after whatever last
        // wrote the value to `lastTimeNanos`.
        final long currentTimeNanos = currentTimeNanos();

        if (currentEWMA != 0) {
            // need to apply the exponential decay.
            final double tmp = (currentTimeNanos - lastTimeNanos) * invTau;
            final double w = exp(-tmp);
            currentEWMA = (int) ceil(currentEWMA * w);
        }

        if (currentEWMA == 0) {
            // If EWMA has decayed to 0 (or isn't yet initialized) and there are no concurrent requests we return the
            // maximum score to increase the likelihood this entity is selected. If there are concurrent requests we
            // don't yet know the latency characteristics so we return the minimum score to decrease the
            // likelihood this entity is selected.
            return concurrentCount == 0 ? 0 : Integer.MIN_VALUE;
        }

        if (concurrentCount > 0 && concurrentStamp != Long.MIN_VALUE) {
            // If we have a request concurrent we should consider how long it has been concurrent so that sudden
            // interruptions don't have to wait for timeouts before our scores can be adjusted.
            currentEWMA = Integer.max(currentEWMA, nanoToMillis(currentTimeNanos - concurrentStamp));
        }

        // Add penalty for concurrent requests to account for "unaccounted" load.
        // Penalty is the observed latency if known, else an arbitrarily high value which makes entities for which
        // no latency data has yet been received (eg: request sent but not received), un-selectable.
        int concurrentPenalty = safeMultiply(concurrentCount, safeMultiply(currentEWMA, concurrentRequestPenalty));
        // Since we are measuring latencies and lower latencies are better, we turn the score as negative such that
        // lower the latency, higher the score.
        return Integer.MAX_VALUE - currentEWMA <= concurrentPenalty ?
                Integer.MIN_VALUE : -(currentEWMA + concurrentPenalty);
    }

    private static int applyPenalty(int currentEWMA, int currentLatency, int penalty) {
        // Relatively large latencies will have a bigger impact on the penalty, while smaller latencies (e.g. premature
        // cancel/error) rely on the penalty.
        return (int) Long.min(Integer.MAX_VALUE, Long.max(currentEWMA, currentLatency) * penalty);
    }

    private void updateEwma(int penalty, long startTimeNanos) {
        assert lock.isWriteLocked();
        // We capture the current time while holding the lock to exploit the monotonic time source
        // properties which prevent the time duration from going negative. This will result in a latency penalty
        // as concurrency increases, but is a trade-off for simplicity.
        final long currentTimeNanos = currentTimeNanos();
        // When modifying the EWMA and lastTime we read/write both values while holding the lock as they are
        // tightly coupled in the EWMA formula below.
        final int currentEWMA = ewma;
        final int currentLatency;
        if (penalty > 0) {
            currentLatency = applyPenalty(currentEWMA, nanoToMillis(currentTimeNanos - startTimeNanos), penalty);
        } else {
            currentLatency = nanoToMillis(currentTimeNanos - startTimeNanos);
        }
        assert currentLatency >= 0;

        // Peak EWMA from finagle for the score to be extremely sensitive to higher than normal latencies.
        final int nextEWMA;
        nextEWMA = currentLatency > currentEWMA ? currentLatency :
                calculateDecay(currentTimeNanos, lastTimeNanos, currentEWMA, currentLatency, invTau);
        lastTimeNanos = currentTimeNanos;
        ewma = nextEWMA;
    }

    private static int calculateDecay(long currentTimeNanos, long lastTimeNanos,
                                      long currentEWMA, int currentLatency, double invTau) {
        final double tmp = (currentTimeNanos - lastTimeNanos) * invTau;
        final double w = exp(-tmp);
        return (int) ceil(currentEWMA * w + currentLatency * (1d - w));
    }

    private static int nanoToMillis(long nanos) {
        return (int) Long.min(Integer.MAX_VALUE, MILLISECONDS.convert(Long.min(nanos, MAX_MS_TO_NS), NANOSECONDS));
    }

    private static int safeMultiply(int a, int b) {
        long result = ((long) a) * b;
        return (int) Long.min(Integer.MAX_VALUE, result);
    }
}
