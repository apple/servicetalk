/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.ImmediateExecutor.IMMEDIATE_EXECUTOR;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A static factory of {@link BufferStrategy} instances.
 */
public final class BufferStrategies {
    private BufferStrategies() {
        // no instances
    }

    /**
     * Returns a {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started. This does not guarantee that the emitted buffers after applying the
     * returned {@link BufferStrategy} will have exactly {@code count} number of items. The emitted buffers may have
     * less or more items than the {@code count}.
     *
     * @param count Number of items to add before closing the current buffer boundary, if not already closed. The
     * emitted buffers may have less or more items than this {@code count}.
     * @param duration {@link Duration} after which the current buffer boundary is closed, if not already closed.
     * @param <T> Type of items added to the buffer.
     * @return {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started.
     */
    public static <T> BufferStrategy<T, Accumulator<T, Iterable<T>>, Iterable<T>> forCountOrTime(
            final int count, final Duration duration) {
        return forCountOrTime(count, duration, IMMEDIATE_EXECUTOR);
    }

    /**
     * Returns a {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started. This does not guarantee that the emitted buffers after applying the
     * returned {@link BufferStrategy} will have exactly {@code count} number of items. The emitted buffers may have
     * less or more items than the {@code count}.
     *
     * @param count Number of items to add before closing the current buffer boundary, if not already closed.
     * @param duration {@link Duration} after which the current buffer boundary is closed, if not already closed.
     * @param executor {@link Executor} to use for recording the passed {@code duration}.
     * @param <T> Type of items added to the buffer.
     * @return {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started.
     */
    public static <T> BufferStrategy<T, Accumulator<T, Iterable<T>>, Iterable<T>> forCountOrTime(
            final int count, final Duration duration, final Executor executor) {
        return forCountOrTime(count, duration, () -> new Accumulator<T, Iterable<T>>() {
            private final List<T> accumulate = new ArrayList<>();

            @Override
            public void accumulate(final T t) {
                accumulate.add(t);
            }

            @Override
            public Iterable<T> finish() {
                return accumulate;
            }
        }, executor);
    }

    /**
     * Returns a {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started. This does not guarantee that the emitted buffers after applying the
     * returned {@link BufferStrategy} will have exactly {@code count} number of items. The emitted buffers may have
     * less or more items than the {@code count}.
     *
     * @param count Number of items to add before closing the current buffer boundary, if not already closed.
     * @param duration {@link Duration} after which the current buffer boundary is closed, if not already closed.
     * @param accumulatorSupplier A {@link Supplier} of {@link Accumulator} every time a buffer boundary is closed.
     * Methods on the {@link Accumulator} returned from this {@link Supplier} may or may not be called.
     * @param <T> Type of items added to the buffer.
     * @param <BC> Type of {@link Accumulator} used to accumulate items in a buffer.
     * @param <B> Type of object created after an {@link Accumulator} is {@link Accumulator#finish() finished}.
     * @return {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started.
     */
    public static <T, BC extends Accumulator<T, B>, B> BufferStrategy<T, Accumulator<T, B>, B> forCountOrTime(
            final int count, final Duration duration, Supplier<BC> accumulatorSupplier) {
        return forCountOrTime(count, duration, accumulatorSupplier, IMMEDIATE_EXECUTOR);
    }

    /**
     * Returns a {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started. This does not guarantee that the emitted buffers after applying the
     * returned {@link BufferStrategy} will have exactly {@code count} number of items. The emitted buffers may have
     * less or more items than the {@code count}.
     *
     * @param count Number of items to add before closing the current buffer boundary, if not already closed.
     * @param duration {@link Duration} after which the current buffer boundary is closed, if not already closed.
     * @param accumulatorSupplier A {@link Supplier} of {@link Accumulator} every time a buffer boundary is closed.
     * Methods on the {@link Accumulator} returned from this {@link Supplier} may or may not be called.
     * @param executor {@link Executor} to use for recording the passed {@code duration}.
     * @param <T> Type of items added to the buffer.
     * @param <BC> Type of {@link Accumulator} used to accumulate items in a buffer.
     * @param <B> Type of object created after an {@link Accumulator} is {@link Accumulator#finish() finished}.
     * @return {@link BufferStrategy} that creates buffer boundaries based on number of items buffered or time elapsed
     * since the current buffer boundary started.
     */
    public static <T, BC extends Accumulator<T, B>, B> BufferStrategy<T, Accumulator<T, B>, B> forCountOrTime(
            final int count, final Duration duration, Supplier<BC> accumulatorSupplier,
            final Executor executor) {
        if (count <= 0) {
            throw new IllegalArgumentException("count: " + count + " (expected > 0)");
        }
        requireNonNull(duration);
        requireNonNull(accumulatorSupplier);
        requireNonNull(executor);
        return new BufferStrategy<T, Accumulator<T, B>, B>() {
            @Override
            public Publisher<Accumulator<T, B>> boundaries() {
                return defer(() -> {
                    State<T, B> state = new State<>();
                    CountingAccumulator<T, B> firstAccum =
                            new CountingAccumulator<>(state, accumulatorSupplier.get(), count);
                    state.beforeNewAccumulatorEmitted(firstAccum);
                    return Single.succeeded(firstAccum).concat(new Completable() {
                        @Override
                        protected void handleSubscribe(final Subscriber subscriber) {
                            // We ignore cancel and expect ambWith to ignore if we delay emit
                            try {
                                subscriber.onSubscribe(IGNORE_CANCEL);
                            } catch (Throwable t) {
                                handleExceptionFromOnSubscribe(subscriber, t);
                                return;
                            }
                            state.countCompletableSubscribed(subscriber);
                        }
                    }
                    .ambWith(executor.timer(duration))
                    .toSingle()
                    .map(__ -> {
                        CountingAccumulator<T, B> accum =
                                new CountingAccumulator<>(state, accumulatorSupplier.get(), count);
                        state.beforeNewAccumulatorEmitted(accum);
                        return accum;
                    })
                    .repeat(__ -> true));
                });
            }

            @Override
            public int bufferSizeHint() {
                return count;
            }
        };
    }

    private static final class State<T, B> {
        private static final Object THRESHOLD_BREACHED_BEFORE_SUBSCRIBE = new Object();
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<State, Object> stateUpdater =
                newUpdater(State.class, Object.class, "state");

        /**
         * The following values can be set to this state:
         * <ul>
         *     <li>{@code null} if neither an active accumulator is present nor a subscriber.</li>
         *     <li>An {@link Accumulator} which is expected to accumulating items for the current boundary.</li>
         *     <li>{@link AccumulatorAndSubscriber} instance when an active accumulator and {@link Subscriber} both are
         *     active.</li>
         *     <li>{@link Subscriber} if the size threshold {@link Completable} is subscribed and there is no
         *     accumulator present.</li>
         * </ul>
         */
        @Nullable
        private volatile Object state;

        void countThresholdBreached(final Accumulator<T, B> breachedAccumulator) {
            for (;;) {
                final Object cState = state;
                if (cState == THRESHOLD_BREACHED_BEFORE_SUBSCRIBE || cState == null) {
                    // multiple breaches
                    return;
                }
                if (cState instanceof Accumulator) {
                    if (cState != breachedAccumulator ||
                            stateUpdater.compareAndSet(this, cState, THRESHOLD_BREACHED_BEFORE_SUBSCRIBE)) {
                        // Either the threshold was breached for a stale accumulator or threshold was breached before
                        // the accumulator could be set.
                        return;
                    }
                } else if (cState instanceof Subscriber) {
                    if (stateUpdater.compareAndSet(this, cState, null)) {
                        Subscriber subscriber = (Subscriber) cState;
                        safeOnComplete(subscriber);
                        return;
                    }
                } else if (cState instanceof AccumulatorAndSubscriber) {
                    @SuppressWarnings("unchecked")
                    final AccumulatorAndSubscriber<T, B> accumulatorAndSubscriber =
                            (AccumulatorAndSubscriber<T, B>) cState;
                    if (accumulatorAndSubscriber.accumulator != breachedAccumulator) {
                        // threshold breach happened after the subscriber completed.
                        return;
                    }

                    if (stateUpdater.compareAndSet(this, cState, null)) {
                        safeOnComplete(accumulatorAndSubscriber.subscriber);
                        return;
                    }
                }
            }
        }

        void countCompletableSubscribed(final Subscriber countSubscriber) {
            for (;;) {
                final Object cState = state;
                // Ordering of subscriber and accumulator is provided by repeat() operator such that a new accumulator
                // is emitted and then the original source is subscribed again (repeat). As neither the accumulator nor
                // the subscribers are skipped, an accumulator that breaches threshold, always indicates completion
                // of the associated Subscriber.
                if (cState == THRESHOLD_BREACHED_BEFORE_SUBSCRIBE) {
                    if (stateUpdater.compareAndSet(this, cState, null)) {
                        safeOnComplete(countSubscriber);
                        return;
                    }
                } else if (cState instanceof Accumulator) {
                    @SuppressWarnings("unchecked")
                    final Accumulator<T, B> accumulator = (Accumulator<T, B>) cState;
                    if (stateUpdater.compareAndSet(
                            this, cState, new AccumulatorAndSubscriber<>(accumulator, countSubscriber))) {
                        return;
                    }
                } else if (stateUpdater.compareAndSet(this, cState, countSubscriber)) {
                    return;
                }
            }
        }

        void beforeNewAccumulatorEmitted(final Accumulator<T, B> newAccumulator) {
            // We always expect Accumulator followed by the Subscriber. So, we blindly overwrite whenever a new
            // accumulator is emitted.
            state = newAccumulator;
        }
    }

    static final class CountingAccumulator<T, B> implements Accumulator<T, B> {
        @Nullable
        private final State<T, B> state;
        private final Accumulator<T, B> delegate;
        private final int sizeThreshold;
        private int size;

        CountingAccumulator(final Accumulator<T, B> delegate) {
            this.state = null;
            this.delegate = delegate;
            this.sizeThreshold = -1;
        }

        CountingAccumulator(final State<T, B> state, final Accumulator<T, B> delegate, final int sizeThreshold) {
            this.state = state;
            this.delegate = delegate;
            this.sizeThreshold = sizeThreshold;
        }

        @Override
        public void accumulate(@Nullable final T item) {
            ++size;
            delegate.accumulate(item);
            if (size == sizeThreshold) {
                assert state != null;
                state.countThresholdBreached(this);
            }
        }

        @Override
        public B finish() {
            return delegate.finish();
        }

        boolean isEmpty() {
            return size == 0;
        }
    }

    private static final class AccumulatorAndSubscriber<T, B> {
        final Accumulator<T, B> accumulator;
        final Subscriber subscriber;

        AccumulatorAndSubscriber(final Accumulator<T, B> accumulator, final Subscriber subscriber) {
            this.accumulator = accumulator;
            this.subscriber = subscriber;
        }
    }
}
