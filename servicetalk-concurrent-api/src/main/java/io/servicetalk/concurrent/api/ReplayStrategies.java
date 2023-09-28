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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
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
        private static final Cancellable CANCELLED = () -> { };
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<MostRecentTimeLimitedReplayAccumulator, Cancellable>
                timerCancellableUpdater = newUpdater(MostRecentTimeLimitedReplayAccumulator.class, Cancellable.class,
                "timerCancellable");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<MostRecentTimeLimitedReplayAccumulator> queueLockUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MostRecentTimeLimitedReplayAccumulator.class, "queueLock");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<MostRecentTimeLimitedReplayAccumulator> queueSizeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MostRecentTimeLimitedReplayAccumulator.class, "queueSize");
        private final Executor executor;
        private final Queue<TimeStampSignal<T>> items;
        private final long ttlNanos;
        private final int maxItems;
        private volatile int queueSize;
        @SuppressWarnings("unused")
        private volatile int queueLock;
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
            // SpSc, but needs iterator.
            // accumulate is called on one thread (no concurrent access on this method).
            // timerFire maybe called on another thread
            items = new ConcurrentLinkedQueue<>();
        }

        @Override
        public void accumulate(@Nullable final T t) {
            long scheduleTimerNanos = -1;
            final TimeStampSignal<T> signal = new TimeStampSignal<>(executor.currentTime(NANOSECONDS), t);
            if (tryAcquireLock(queueLockUpdater, this)) {
                for (;;) {
                    final int qSize = queueSize;
                    if (qSize < maxItems) {
                        if (queueSizeUpdater.compareAndSet(this, qSize, qSize + 1)) {
                            items.add(signal);
                            if (qSize == 0) {
                                scheduleTimerNanos = ttlNanos;
                            }
                            break;
                        }
                    } else if (queueSizeUpdater.compareAndSet(this, qSize, qSize)) {
                        // Queue removal is only done while queueLock is acquired, so we don't need to worry about
                        // the timer thread removing items concurrently.
                        items.poll();
                        items.add(signal);
                        break;
                    }
                }
                if (!releaseLock(queueLockUpdater, this)) {
                    scheduleTimerNanos = tryDrainQueue();
                }
            } else {
                queueSizeUpdater.incrementAndGet(this);
                items.add(signal);
                scheduleTimerNanos = tryDrainQueue();
            }

            if (scheduleTimerNanos >= 0) {
                schedulerTimer(scheduleTimerNanos);
            }
        }

        @Override
        public void deliverAccumulation(final Consumer<T> consumer) {
            int i = 0;
            for (TimeStampSignal<T> timeStampSignal : items) {
                consumer.accept(timeStampSignal.signal);
                // The queue size maybe larger than maxItems if we weren't able to acquire the queueLock while adding.
                // This is only a temporary condition while there is concurrent access between timer and accumulator.
                // Guard against it here to preserve the invariant that we shouldn't deliver more than maxItems.
                if (++i >= maxItems) {
                    break;
                }
            }
        }

        @Override
        public void cancelAccumulation() {
            // Stop the background timer and prevent it from being rescheduled. It is possible upstream may deliver
            // more data but the queue size is bounded by maxItems and this method should only be called when upstream
            // is cancelled which should eventually dereference this object making it eligible for GC (no memory leak).
            final Cancellable cancellable = timerCancellableUpdater.getAndSet(this, CANCELLED);
            if (cancellable != null) {
                cancellable.cancel();
            }
        }

        private long tryDrainQueue() {
            long scheduleTimerNanos = -1;
            boolean tryAcquire = true;
            while (tryAcquire && tryAcquireLock(queueLockUpdater, this)) {
                // Ensure the queue contains maxItems or less items.
                for (;;) {
                    final int qSize = queueSize;
                    if (qSize <= maxItems) {
                        break;
                    } else if (queueSizeUpdater.compareAndSet(this, qSize, qSize - 1)) {
                        items.poll();
                    }
                }

                scheduleTimerNanos = doExpire();

                tryAcquire = !releaseLock(queueLockUpdater, this);
            }
            return scheduleTimerNanos;
        }

        private void schedulerTimer(long nanos) {
            for (;;) {
                final Cancellable currentCancellable = timerCancellable;
                if (currentCancellable == CANCELLED) {
                    break;
                } else {
                    final Cancellable nextCancellable = executor.schedule(this::timerFire, nanos, NANOSECONDS);
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

        // lock must be held!
        private long doExpire() {
            final long nanoTime = executor.currentTime(NANOSECONDS);
            TimeStampSignal<T> item;
            for (;;) {
                final long delta;
                item = items.peek();
                if (item == null) {
                    return -1;
                } else if ((delta = nanoTime - item.timeStamp) >= ttlNanos) {
                    final int qSize = queueSizeUpdater.decrementAndGet(this);
                    assert qSize >= 0;
                    // Removal is only done while holding the lock. This means we don't have to worry about the
                    // accumulator thread running concurrently and removing the peeked item behind our back.
                    items.poll();
                } else {
                    // elements sorted in increasing time, break when first non-expired entry found.
                    // delta maybe negative if ttlNanos is small and this method sees newly added items while looping.
                    return delta <= 0 ? ttlNanos : ttlNanos - (nanoTime - item.timeStamp);
                }
            }
        }

        private void timerFire() {
            long scheduleTimerNanos;
            if (tryAcquireLock(queueLockUpdater, this)) {
                scheduleTimerNanos = doExpire();
                if (!releaseLock(queueLockUpdater, this)) {
                    scheduleTimerNanos = tryDrainQueue();
                }
            } else {
                scheduleTimerNanos = tryDrainQueue();
            }

            if (scheduleTimerNanos >= 0) {
                schedulerTimer(scheduleTimerNanos);
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
