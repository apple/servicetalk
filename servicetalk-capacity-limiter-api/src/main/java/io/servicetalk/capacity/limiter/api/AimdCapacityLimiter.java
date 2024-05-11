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

import io.servicetalk.capacity.limiter.api.AimdCapacityLimiterBuilder.StateObserver;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static java.lang.Math.max;

/**
 * A client side dynamic {@link CapacityLimiter} that adapts its limit based on a configurable range of concurrency
 * {@link #min} and {@link #max}, and re-evaluates this limit upon a request-drop event
 * (e.g., timeout or rejection due to capacity). It's not ideal for server-side solutions, due to the slow recover
 * mechanism it offers, which can lead in significant traffic loss during the recovery window.
 * <p>
 * The limit translates to a concurrency figure, e.g., how many requests can be in-flight simultaneously and doesn't
 * represent a constant rate (i.e., has no notion of time).
 * <p>
 * The solution is based on the <a href="https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease">
 * AIMD feedback control algorithm</a>.
 */
final class AimdCapacityLimiter implements CapacityLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AimdCapacityLimiter.class);

    private static final AtomicIntegerFieldUpdater<AimdCapacityLimiter> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AimdCapacityLimiter.class, "state");

    private static final int UNLOCKED = 0;
    private static final int LOCKED = 1;

    private final String name;
    private final int min;
    private final int max;
    private final float increment;
    private final float backoffRatioOnLoss;
    private final float backoffRatioOnLimit;
    private final long coolDownPeriodNs;
    private final LongSupplier timeSource;
    @Nullable
    private final StateObserver observer;
    private int pending;
    private double limit;
    private long lastIncreaseTimestampNs;
    private volatile int state;
    AimdCapacityLimiter(final String name, final int min, final int max, final int initial, final float increment,
                        final float backoffRatioOnLimit, final float backoffRatioOnLoss,
                        final Duration cooldown, @Nullable final StateObserver observer,
                        final LongSupplier timeSource) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.increment = increment;
        this.limit = initial;
        this.pending = 0;
        this.state = UNLOCKED;
        this.backoffRatioOnLimit = backoffRatioOnLimit;
        this.backoffRatioOnLoss = backoffRatioOnLoss;
        this.coolDownPeriodNs = cooldown.toNanos();
        this.observer = observer == null ? null : new CatchAllStateObserver(observer);
        this.timeSource = timeSource;
        this.lastIncreaseTimestampNs = timeSource.getAsLong();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Ticket tryAcquire(final Classification classification, @Nullable final ContextMap meta) {
        Ticket ticket;
        double l;
        int p;
        for (;;) {
            if (stateUpdater.compareAndSet(this, UNLOCKED, LOCKED)) {
                if (pending >= limit || pending == max) {   // prevent pending going above max if limit is fractional
                    ticket = null;
                } else {
                    ticket = new DefaultTicket(this, (int) limit - pending, pending);
                    pending++;
                }
                l = limit;
                p = pending;
                stateUpdater.set(this, UNLOCKED);
                break;
            }
        }
        notifyObserver(l, p);
        return ticket;
    }

    private void notifyObserver(double limit, int pending) {
        if (observer != null) {
            observer.observe((int) limit, pending);
        }
    }

    private void onSuccess() {
        double l;
        int p;
        for (;;) {
            if (stateUpdater.compareAndSet(this, UNLOCKED, LOCKED)) {
                if (coolDownPeriodNs == 0 || (timeSource.getAsLong() - lastIncreaseTimestampNs) >= coolDownPeriodNs) {
                    limit += increment;
                    if (limit > max || limit < 0) { // prevent limit going above max or overflow
                        limit = max;
                    }
                    if (coolDownPeriodNs != 0) {
                        lastIncreaseTimestampNs = timeSource.getAsLong();
                    }
                }
                pending--;
                l = limit;
                p = pending;
                stateUpdater.set(this, UNLOCKED);
                break;
            }
        }
        notifyObserver(l, p);
    }

    private void onLoss() {
        double l;
        int p;
        for (;;) {
            if (stateUpdater.compareAndSet(this, UNLOCKED, LOCKED)) {
                limit = max(min, (int) (limit * (limit >= max ? backoffRatioOnLimit : backoffRatioOnLoss)));
                pending--;
                l = limit;
                p = pending;
                stateUpdater.set(this, UNLOCKED);
                break;
            }
        }
        notifyObserver(l, p);
    }

    private void onIgnore() {
        double l;
        int p;
        for (;;) {
            if (stateUpdater.compareAndSet(this, UNLOCKED, LOCKED)) {
                pending--;
                l = limit;
                p = pending;
                stateUpdater.set(this, UNLOCKED);
                break;
            }
        }
        notifyObserver(l, p);
    }

    @Override
    public String toString() {
        return "AimdCapacityLimiter{" +
                "name='" + name + '\'' +
                ", min=" + min +
                ", max=" + max +
                ", increment=" + increment +
                ", backoffRatioOnLoss=" + backoffRatioOnLoss +
                ", backoffRatioOnLimit=" + backoffRatioOnLimit +
                ", coolDownPeriodNs=" + coolDownPeriodNs +
                ", pending=" + pending +
                ", limit=" + limit +
                ", lastIncreaseTimestampNs=" + lastIncreaseTimestampNs +
                ", state=" + state +
                '}';
    }

    private static final class DefaultTicket implements Ticket, LimiterState {

        private static final int UNSUPPORTED = -1;
        private final AimdCapacityLimiter provider;
        private final int remaining;
        private final int pending;

        DefaultTicket(final AimdCapacityLimiter provider, final int remaining, final int pending) {
            this.provider = provider;
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
            provider.onSuccess();
            return UNSUPPORTED;
        }

        @Override
        public int dropped() {
            provider.onLoss();
            return UNSUPPORTED;
        }

        @Override
        public int failed(Throwable __) {
            completed();
            return UNSUPPORTED;
        }

        @Override
        public int ignored() {
            provider.onIgnore();
            return UNSUPPORTED;
        }
    }

    private static final class CatchAllStateObserver implements StateObserver {

        private final StateObserver delegate;

        CatchAllStateObserver(final StateObserver delegate) {
            this.delegate = delegate;
        }

        @Override
        public void observe(final int limit, final int consumed) {
            try {
                delegate.observe(limit, consumed);
            } catch (Throwable t) {
                LOGGER.warn("Unexpected exception from {}.observe({}, {})",
                        delegate.getClass().getSimpleName(), limit, consumed, t);
            }
        }
    }
}
