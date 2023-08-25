/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Executor;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION_NO_THROW;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * Utilities to customize {@link ReplayStrategy}.
 */
public final class ReplayStrategies {
    private ReplayStrategies() {
    }

    /**
     * Create a {@link ReplayStrategyBuilder} using the history strategy.
     * @param history max number of items to retain which can be delivered to new subscribers.
     * @param <T> The type of {@link ReplayStrategyBuilder}.
     * @return a {@link ReplayStrategyBuilder} using the history strategy.
     */
    public static <T> ReplayStrategyBuilder<T> historyBuilder(int history) {
        return new ReplayStrategyBuilder<>(() -> new MostRecentReplayAccumulator<>(history));
    }

    /**
     * Create a {@link ReplayStrategyBuilder} using the history and TTL strategy.
     * @param history max number of items to retain which can be delivered to new subscribers.
     * @param ttl duration each element will be retained before being removed.
     * @param executor used to enforce the {@code ttl} argument.
     * @param <T> The type of {@link ReplayStrategyBuilder}.
     * @return a {@link ReplayStrategyBuilder} using the history and TTL strategy.
     */
    public static <T> ReplayStrategyBuilder<T> historyTtlBuilder(int history, Duration ttl, Executor executor) {
        return new ReplayStrategyBuilder<>(() -> new MostRecentTimeLimitedReplayAccumulator<>(history, ttl, executor));
    }

    private static final class MostRecentReplayAccumulator<T> implements ReplayAccumulator<T> {
        private final int maxItems;
        private final Deque<Object> list = new ArrayDeque<>();

        MostRecentReplayAccumulator(final int maxItems) {
            if (maxItems <= 0) {
                throw new IllegalArgumentException("maxItems: " + maxItems + "(expected >0)");
            }
            this.maxItems = maxItems;
        }

        @Override
        public void accumulate(@Nullable final T t) {
            if (list.size() >= maxItems) {
                list.pop();
            }
            list.add(wrapNull(t));
        }

        @Override
        public void deliverAccumulation(final Consumer<T> consumer) {
            for (Object item : list) {
                consumer.accept(unwrapNullUnchecked(item));
            }
        }
    }

    private static final class MostRecentTimeLimitedReplayAccumulator<T> implements ReplayAccumulator<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<MostRecentTimeLimitedReplayAccumulator> stateSizeUpdater =
                AtomicLongFieldUpdater.newUpdater(MostRecentTimeLimitedReplayAccumulator.class, "stateSize");
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<MostRecentTimeLimitedReplayAccumulator, Cancellable>
                timerCancellableUpdater = newUpdater(MostRecentTimeLimitedReplayAccumulator.class, Cancellable.class,
                "timerCancellable");
        private final Executor executor;
        private final Queue<TimeStampSignal<T>> items;
        private final long ttlNanos;
        private final int maxItems;
        /**
         * Provide atomic state for size of {@link #items} and also for visibility between the threads consuming and
         * producing. The atomically incrementing "state" ensures that any modifications from the producer thread
         * are visible from the consumer thread and we never "miss" a timer schedule event if the queue becomes empty.
         */
        private volatile long stateSize;
        @Nullable
        private volatile Cancellable timerCancellable;

        MostRecentTimeLimitedReplayAccumulator(final int maxItems, final Duration ttl, final Executor executor) {
            if (ttl.isNegative()) {
                throw new IllegalArgumentException("ttl: " + ttl + "(expected non-negative)");
            }
            if (maxItems <= 0) {
                throw new IllegalArgumentException("maxItems: " + maxItems + "(expected >0)");
            }
            this.executor = requireNonNull(executor);
            this.ttlNanos = ttl.toNanos();
            this.maxItems = maxItems;
            items = new ConcurrentLinkedQueue<>(); // SpMc
        }

        @Override
        public void accumulate(@Nullable final T t) {
            // We may exceed max items in the queue but this method isn't invoked concurrently, so we only go over by
            // at most 1 item.
            items.add(new TimeStampSignal<>(executor.currentTime(NANOSECONDS), t));
            for (;;) {
                final long currentStateSize = stateSize;
                final int currentSize = getSize(currentStateSize);
                final int nextState = getState(currentStateSize) + 1;
                if (currentSize >= maxItems) {
                    if (stateSizeUpdater.compareAndSet(this, currentStateSize,
                            buildStateSize(nextState, currentSize))) {
                        items.poll();
                        break;
                    }
                } else if (stateSizeUpdater.compareAndSet(this, currentStateSize,
                        buildStateSize(nextState, currentSize + 1))) {
                    if (currentSize == 0) {
                        schedulerTimer(ttlNanos);
                    }
                    break;
                }
            }
        }

        @Override
        public void deliverAccumulation(final Consumer<T> consumer) {
            for (TimeStampSignal<T> timeStampSignal : items) {
                consumer.accept(timeStampSignal.signal);
            }
        }

        @Override
        public void cancelAccumulation() {
            final Cancellable cancellable = timerCancellableUpdater.getAndSet(this, EMPTY_SUBSCRIPTION_NO_THROW);
            if (cancellable != null) {
                cancellable.cancel();
            }
        }

        private static int getSize(long stateSize) {
            return (int) stateSize;
        }

        private static int getState(long stateSize) {
            return (int) (stateSize >>> 32);
        }

        private static long buildStateSize(int state, int size) {
            return (((long) state) << 32) | size;
        }

        private void schedulerTimer(long nanos) {
            for (;;) {
                final Cancellable currentCancellable = timerCancellable;
                if (currentCancellable == EMPTY_SUBSCRIPTION_NO_THROW) {
                    break;
                } else {
                    final Cancellable nextCancellable = executor.schedule(this::expireSignals, nanos, NANOSECONDS);
                    if (timerCancellableUpdater.compareAndSet(this, currentCancellable, nextCancellable)) {
                        // Current logic only has 1 timer outstanding at any give time so cancellation of
                        // the current cancellable shouldn't be necessary but do it for completeness.
                        if (currentCancellable != null) {
                            currentCancellable.cancel();
                        }
                        break;
                    } else {
                        nextCancellable.cancel();
                    }
                }
            }
        }

        private void expireSignals() {
            final long nanoTime = executor.currentTime(NANOSECONDS);
            TimeStampSignal<T> item;
            for (;;) {
                // read stateSize before peek, so if we poll from the queue we are sure to see the correct
                // state relative to items in the queue.
                final long currentStateSize = stateSize;
                item = items.peek();
                if (item == null) {
                    break;
                } else if (nanoTime - item.timeStamp >= ttlNanos) {
                    final int currentSize = getSize(currentStateSize);
                    if (stateSizeUpdater.compareAndSet(this, currentStateSize,
                            buildStateSize(getState(currentStateSize) + 1, currentSize - 1))) {
                        // When we add: we add to the queue we add first, then CAS sizeState.
                        // When we remove: we CAS the atomic state first, then poll.
                        // This avoids removing a non-expired item because if the "add" thread is running faster and
                        // already polled "item" the CAS will fail, and we will try again on the next loop iteration.
                        items.poll();
                        if (currentSize == 1) {
                            // a new timer task will be scheduled after addition if this is the case. break to avoid
                            // multiple timer tasks running concurrently.
                            break;
                        }
                    }
                } else {
                    schedulerTimer(ttlNanos - (nanoTime - item.timeStamp));
                    break; // elements sorted in increasing time, break when first non-expired entry found.
                }
            }
        }
    }

    private static final class TimeStampSignal<T> {
        final long timeStamp;
        @Nullable
        final T signal;

        private TimeStampSignal(final long timeStamp, @Nullable final T signal) {
            this.timeStamp = timeStamp;
            this.signal = signal;
        }
    }
}
