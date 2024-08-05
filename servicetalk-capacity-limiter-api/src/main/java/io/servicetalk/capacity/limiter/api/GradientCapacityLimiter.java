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
/*
 * Copyright © 2018 Netflix, Inc. and the Netflix Concurrency Limits authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicetalk.capacity.limiter.api;

import io.servicetalk.capacity.limiter.api.GradientCapacityLimiterBuilder.Observer;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static java.lang.Double.isNaN;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Gradient is a dynamic concurrency limit algorithm used for clients.
 * <p>
 * Gradient's basic concept is that it tracks two Round Trip Time (RTT) figures, one of long period and another one
 * of a shorter period. These two figures are then compared, and a gradient value is produced, representing the
 * change between the two. That gradient value can in-turn be used to signify load; i.e. a positive gradient can
 * mean that the RTTs are decreasing, whereas a negative value means that RTTs are increasing.
 * This figure can be used to deduce a new limit (lower or higher accordingly) to follow the observed load pattern.
 * <p>
 * The algorithm is heavily influenced by the following prior-art
 * <ul>
 *     <li><a href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/adaptive_concurrency_filter">Envoy Adaptive Concurrency</a></li>
 *     <li><a href="https://github.com/Netflix/concurrency-limits">Netflix Concurrency Limits</a></li>
 * </ul>
 */
final class GradientCapacityLimiter implements CapacityLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradientCapacityLimiter.class);

    private final ReentrantLock lock = new ReentrantLock();

    private final String name;
    private final int min;
    private final int max;
    private final float backoffRatioOnLimit;
    private final float backoffRatioOnLoss;
    private final long limitUpdateIntervalNs;
    private final float minGradient;
    private final float maxPositiveGradient;
    private final Observer observer;
    private final BiPredicate<Integer, Double> suspendLimitInc;
    private final BiPredicate<Integer, Double> suspendLimitDec;
    private final BiFunction<Double, Double, Double> headroom;
    private final LongSupplier timeSource;
    private final int initial;
    private final LatencyTracker longLatency;
    private final LatencyTracker shortLatency;

    private int pending;
    private double limit;
    private long lastSamplingNs;
    GradientCapacityLimiter(final String name, final int min, final int max, final int initial,
                            final float backoffRatioOnLimit, final float backoffRatioOnLoss,
                            final LatencyTracker shortLatencyTracker, final LatencyTracker longLatencyTracker,
                            final Duration limitUpdateInterval, final float minGradient,
                            final float maxPositiveGradient, final Observer observer,
                            final BiPredicate<Integer, Double> suspendLimitInc,
                            final BiPredicate<Integer, Double> suspendLimitDec,
                            final BiFunction<Double, Double, Double> headroom, final LongSupplier timeSource) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.initial = initial;
        this.limit = initial;
        this.backoffRatioOnLimit = backoffRatioOnLimit;
        this.backoffRatioOnLoss = backoffRatioOnLoss;
        this.limitUpdateIntervalNs = limitUpdateInterval.toNanos();
        this.minGradient = minGradient;
        this.maxPositiveGradient = maxPositiveGradient;
        this.observer = new CatchAllObserver(observer);
        this.suspendLimitInc = suspendLimitInc;
        this.suspendLimitDec = suspendLimitDec;
        this.headroom = headroom;
        this.timeSource = timeSource;
        this.lastSamplingNs = timeSource.getAsLong();
        this.longLatency = longLatencyTracker;
        this.shortLatency = shortLatencyTracker;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Ticket tryAcquire(final Classification classification, @Nullable final ContextMap meta) {
        Ticket ticket = null;
        lock.lock();
        try {
            final int newLimit = (int) limit;
            if (pending < limit) {
                final int newPending = pending + 1;
                ticket = new DefaultTicket(this, newLimit - newPending, newPending);
                // update the state last just in case anything throws.
                ++pending;
            }
        } catch (Throwable t) {
            LOGGER.error("Exception while attempting to acquire ticket", t);
        } finally {
            lock.unlock();
        }

        if (ticket != null) {
            try {
                observer.onActiveRequestsIncr();
            } catch (Throwable t) {
                ticket.ignored();
                throw t;
            }
        }
        return ticket;
    }

    /**
     * Needs to be called while holding the lock.
     */
    private int updateLimit(final long timestampNs, final double shortLatencyMillis, final double longLatencyMillis) {
        assert lock.isHeldByCurrentThread();
        if (isNaN(longLatencyMillis) || isNaN(shortLatencyMillis) || shortLatencyMillis == 0) {
            return -1;
        }
        lastSamplingNs = timestampNs;
        final double gradient = max(minGradient, min(maxPositiveGradient, longLatencyMillis / shortLatencyMillis));
        // When positive gradient, and limit already above initial,
        // avoid increasing the limit when we are far from meeting it - i.e. blast radius.
        final boolean isPositiveSuspended = !isNaN(gradient) &&
                gradient > 1.0 && limit > initial && suspendLimitInc.test(pending, limit);
        // When negative gradient, and consumption not close to limit,
        // avoid decreasing the limit. Low RPS & noisy environments (RTT deviations > 500ms) tend to bring the limit
        // down to "min" even though there aren't many pending requests to justify the decision.
        final boolean isNegativeSuspended = !isNaN(gradient) && gradient < 1.0 && suspendLimitDec.test(pending, limit);
        if (isNaN(gradient) || isPositiveSuspended || isNegativeSuspended) {
            return -1;
        }

        final double headroom = gradient >= 1 ? this.headroom.apply(gradient, limit) : 0;
        final double oldLimit = limit;
        limit = min(max, max(min, (gradient * limit) + headroom));
        final int newLimit = (int) limit;
        observer.onLimitChange(longLatencyMillis, shortLatencyMillis, gradient, oldLimit, newLimit);
        return newLimit;
    }

    private int onSuccess(final long durationNs) {
        final long nowNs = timeSource.getAsLong();
        final long rttMillis = NANOSECONDS.toMillis(durationNs);
        int newPending;
        int limit;
        lock.lock();
        try {
            limit = (int) this.limit;
            // On the return pathway update the state first just in case any of the interfaces throw exceptions.
            newPending = --pending;
            final double longLatencyMillis = longLatency.observe(nowNs, rttMillis);
            final double shortLatencyMillis = shortLatency.observe(nowNs, rttMillis);

            if ((nowNs - lastSamplingNs) >= limitUpdateIntervalNs) {
                limit = updateLimit(nowNs, shortLatencyMillis, longLatencyMillis);
            }
        } catch (Throwable t) {
            LOGGER.error("Exception caught updating state machine", t);
            throw t;
        } finally {
            lock.unlock();
        }

        observer.onActiveRequestsDecr();
        return limit - newPending;
    }

    private int onDrop() {
        int newPending;
        double newLimit;

        lock.lock();
        try {
            newLimit = limit = max(min, limit * (limit >= max ? backoffRatioOnLimit : backoffRatioOnLoss));
            newPending = --pending;
        } finally {
            lock.unlock();
        }

        observer.onActiveRequestsDecr();
        return (int) (newLimit - newPending);
    }

    private int onIgnore() {
        int newPending;
        double newLimit;

        lock.lock();
        try {
            newLimit = limit;
            newPending = --pending;
        } finally {
            lock.unlock();
        }
        observer.onActiveRequestsDecr();
        return (int) (newLimit - newPending);
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return "GradientCapacityLimiter{" +
                    ", name='" + name + '\'' +
                    ", min=" + min +
                    ", max=" + max +
                    ", backoffRatioOnLimit=" + backoffRatioOnLimit +
                    ", backoffRatioOnLoss=" + backoffRatioOnLoss +
                    ", limitUpdateIntervalNs=" + limitUpdateIntervalNs +
                    ", minGradient=" + minGradient +
                    ", maxPositiveGradient=" + maxPositiveGradient +
                    ", initial=" + initial +
                    ", pending=" + pending +
                    ", limit=" + limit +
                    ", lastSamplingNs=" + lastSamplingNs +
                    '}';
        } finally {
            lock.unlock();
        }
    }

    private static final class DefaultTicket implements Ticket, LimiterState {

        private final long startTime;
        private final GradientCapacityLimiter provider;
        private final int remaining;
        private final int pending;

        DefaultTicket(final GradientCapacityLimiter provider, final int remaining, final int pending) {
            this.provider = provider;
            this.startTime = provider.timeSource.getAsLong();
            this.remaining = remaining;
            this.pending = pending;
        }

        @Override
        public LimiterState state() {
            return this;
        }

        @Override
        public int remaining() {
            return remaining;
        }

        @Override
        public int pending() {
            return pending;
        }

        @Override
        public int completed() {
            return provider.onSuccess(provider.timeSource.getAsLong() - startTime);
        }

        @Override
        public int dropped() {
            return provider.onDrop();
        }

        @Override
        public int failed(Throwable __) {
            return provider.onDrop();
        }

        @Override
        public int ignored() {
            return provider.onIgnore();
        }
    }

    private static final class CatchAllObserver implements Observer {

        private final Observer delegate;

        CatchAllObserver(Observer observer) {
            this.delegate = observer;
        }

        @Override
        public void onActiveRequestsIncr() {
            try {
                delegate.onActiveRequestsIncr();
            } catch (Throwable t) {
                LOGGER.warn("Unexpected exception from {}.onActiveRequestsIncr()",
                        delegate.getClass().getSimpleName(), t);
            }
        }

        @Override
        public void onActiveRequestsDecr() {
            try {
                delegate.onActiveRequestsDecr();
            } catch (Throwable t) {
                LOGGER.warn("Unexpected exception from {}.onActiveRequestsDecr()",
                        delegate.getClass().getSimpleName(), t);
            }
        }

        @Override
        public void onLimitChange(final double longRtt, final double shortRtt, final double gradient,
                                  final double oldLimit, final double newLimit) {
            try {
                delegate.onLimitChange(longRtt, shortRtt, gradient, oldLimit, newLimit);
            } catch (Throwable t) {
                LOGGER.warn("Unexpected exception from {}.onLimitChange({}, {}, {}, {}, {})",
                        delegate.getClass().getSimpleName(), longRtt, shortRtt, gradient, oldLimit, newLimit, t);
            }
        }
    }
}
