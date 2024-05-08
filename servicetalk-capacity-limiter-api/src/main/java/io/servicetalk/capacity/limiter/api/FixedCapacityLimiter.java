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

import io.servicetalk.capacity.limiter.api.FixedCapacityLimiterBuilder.StateObserver;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class FixedCapacityLimiter implements CapacityLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FixedCapacityLimiter.class);

    private static final AtomicIntegerFieldUpdater<FixedCapacityLimiter> pendingUpdater =
            newUpdater(FixedCapacityLimiter.class, "pending");

    private final int capacity;
    @Nullable
    private final StateObserver observer;
    private final String name;

    private volatile int pending;

    FixedCapacityLimiter(final int capacity) {
        this(FixedCapacityLimiter.class.getSimpleName(), capacity, null);
    }

    FixedCapacityLimiter(final String name, final int capacity, @Nullable StateObserver observer) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity: " + capacity + " (expected: > 0)");
        }
        this.name = name;
        this.capacity = capacity;
        this.observer = observer == null ? null : new CatchAllStateObserver(observer);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Ticket tryAcquire(final Classification classification, @Nullable final ContextMap meta) {
        final int priority = min(max(classification.priority(), 0), 100);
        final int effectiveLimit = (capacity * priority) / 100;
        for (;;) {
            final int currPending = pending;
            if (currPending == effectiveLimit &&
                    pendingUpdater.compareAndSet(this, currPending, currPending)) {
                notifyObserver(currPending);
                return null;
            } else if (currPending < effectiveLimit) {
                final int newPending = currPending + 1;
                if (pendingUpdater.compareAndSet(this, currPending, newPending)) {
                    notifyObserver(newPending);
                    return new DefaultTicket(this, effectiveLimit - newPending);
                }
            }
        }
    }

    private void notifyObserver(final int pending) {
        if (observer != null) {
            observer.observe(capacity, pending);
        }
    }

    @Override
    public String toString() {
        return "FixedCapacityLimiter{" +
                "capacity=" + capacity +
                ", name='" + name + '\'' +
                ", pending=" + pending +
                '}';
    }

    private static final class DefaultTicket implements Ticket, LimiterState {

        private final FixedCapacityLimiter fixedCapacityProvider;
        private final int remaining;

        DefaultTicket(final FixedCapacityLimiter fixedCapacityProvider, int remaining) {
            this.fixedCapacityProvider = fixedCapacityProvider;
            this.remaining = remaining;
        }

        @Override
        public LimiterState state() {
            return this;
        }

        @Override
        public int remaining() {
            return remaining;
        }

        private int release() {
            final int pending = pendingUpdater.decrementAndGet(fixedCapacityProvider);
            fixedCapacityProvider.notifyObserver(pending);
            return max(0, fixedCapacityProvider.capacity - pending);
        }

        @Override
        public int completed() {
            return release();
        }

        @Override
        public int dropped() {
            return release();
        }

        @Override
        public int failed(final Throwable __) {
            return release();
        }

        @Override
        public int ignored() {
            return release();
        }
    }

    private static final class CatchAllStateObserver implements StateObserver {

        private final StateObserver delegate;

        CatchAllStateObserver(final StateObserver delegate) {
            this.delegate = delegate;
        }

        @Override
        public void observe(final int capacity, final int consumed) {
            try {
                delegate.observe(capacity, consumed);
            } catch (Throwable t) {
                LOGGER.warn("Unexpected exception from {}.observe({}, {})",
                        delegate.getClass().getSimpleName(), capacity, consumed, t);
            }
        }
    }
}
