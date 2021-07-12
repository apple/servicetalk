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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.BufferStrategies.CountingAccumulator;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Long.MIN_VALUE;
import static java.util.Objects.requireNonNull;

final class PublisherBuffer<T, B> extends AbstractAsynchronousPublisherOperator<T, B> {
    private final BufferStrategy<T, ?, B> bufferStrategy;

    PublisherBuffer(final Publisher<T> original,
                    final BufferStrategy<T, ?, B> bufferStrategy) {
        super(original);
        this.bufferStrategy = requireNonNull(bufferStrategy);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super B> subscriber) {
        final int bufferSizeHint = bufferStrategy.bufferSizeHint();
        if (bufferSizeHint <= 0) {
            return new Subscriber<T>() {
                @Override
                public void onSubscribe(final Subscription subscription) {
                    subscription.cancel();
                    deliverErrorFromSource(subscriber,
                            new IllegalArgumentException("bufferSizeHint: " + bufferSizeHint + " (expected > 0)"));
                }

                @Override
                public void onNext(@Nullable final T t) {
                    // Noop
                }

                @Override
                public void onError(final Throwable t) {
                    // Noop
                }

                @Override
                public void onComplete() {
                    // Noop
                }
            };
        } else {
            return new ItemsSubscriber<>(bufferStrategy.boundaries(), subscriber, bufferSizeHint);
        }
    }

    private static final class ItemsSubscriber<T, B> implements Subscriber<T> {
        private final State state;
        private final Subscriber<? super B> target;
        private final DelayedSubscription bSubscription;
        private final DelayedSubscription tSubscription;
        private final int bufferSizeHint;
        private int itemsPending;

        ItemsSubscriber(final Publisher<? extends Accumulator<T, B>> boundaries,
                        final Subscriber<? super B> target, final int bufferSizeHint) {
            state = new State(bufferSizeHint);
            this.target = target;
            bSubscription = new DelayedSubscription();
            tSubscription = new DelayedSubscription();
            this.bufferSizeHint = bufferSizeHint;
            // Request-n is delayed till we receive the first boundary but we will request bufferSizeHint.
            // This is done here to localize state management (pending count) in this subscriber but still drive the
            // first request-n from inside State.
            itemsPending = bufferSizeHint;
            toSource(boundaries).subscribe(new BoundariesSubscriber<>(state, target, bSubscription, tSubscription));
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            // We may cancel from multiple threads and DelayedSubscription will atomically swap if a cancel occurs but
            // it will not prevent concurrent access between request(n) and cancel() on the original subscription.
            tSubscription.delayedSubscription(ConcurrentSubscription.wrap(subscription));
        }

        @Override
        public void onNext(@Nullable final T t) {
            assert itemsPending > 0;
            --itemsPending;
            state.accumulate(t, target, bSubscription, tSubscription);
            if (itemsPending == 0) {
                itemsPending = bufferSizeHint;
                tSubscription.request(bufferSizeHint);
            }
        }

        @Override
        public void onError(final Throwable t) {
            state.itemsTerminated(error(t), target, bSubscription);
        }

        @Override
        public void onComplete() {
            state.itemsTerminated(complete(), target, bSubscription);
        }
    }

    private static final class BoundariesSubscriber<T, B> implements Subscriber<Accumulator<T, B>> {
        private final State state;
        private final Subscriber<? super B> target;
        private final DelayedSubscription bSubscription;
        private final Subscription tSubscription;

        BoundariesSubscriber(final State state, final Subscriber<? super B> target,
                             final DelayedSubscription bSubscription, final Subscription tSubscription) {
            this.state = state;
            this.target = target;
            this.bSubscription = bSubscription;
            this.tSubscription = tSubscription;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            // We may cancel from multiple threads and DelayedSubscription will atomically swap if a cancel occurs but
            // it will not prevent concurrent access between request(n) and cancel() on the original subscription.
            bSubscription.delayedSubscription(ConcurrentSubscription.wrap(new Subscription() {
                @Override
                public void request(final long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    try {
                        subscription.cancel();
                    } finally {
                        tSubscription.cancel();
                    }
                }
            }));
            // Wrap bSubscription to count number of requested items excluding internal demand for discarded boundaries.
            target.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    state.requested(n, target);
                    bSubscription.request(n);
                }

                @Override
                public void cancel() {
                    bSubscription.cancel();
                }
            });
        }

        @Override
        public void onNext(@Nonnull final Accumulator<T, B> accumulator) {
            state.nextAccumulator(accumulator, target, bSubscription, tSubscription);
        }

        @Override
        public void onError(final Throwable t) {
            try {
                state.boundariesTerminated(t, target);
            } finally {
                tSubscription.cancel();
            }
        }

        @Override
        public void onComplete() {
            try {
                state.boundariesTerminated(new IllegalStateException("Boundaries source completed unexpectedly."),
                        target);
            } finally {
                tSubscription.cancel();
            }
        }
    }

    private static final class State {
        private static final Object ADDING = new Object();
        private static final Object TERMINATING = new Object();
        private static final Object TERMINATED = new Object();
        private static final AtomicReferenceFieldUpdater<State, Object> maybeAccumulatorUpdater =
                AtomicReferenceFieldUpdater.newUpdater(State.class, Object.class, "maybeAccumulator");
        private static final AtomicLongFieldUpdater<State> pendingUpdater =
                AtomicLongFieldUpdater.newUpdater(State.class, "pending");

        private final int firstItemsRequestN;
        /**
         * Following values are assigned to this variable:
         * <ul>
         *     <li>{@code null} till the first accumulator arrives.</li>
         *     <li>{@link Accumulator} which is emitted by the boundaries source.</li>
         *     <li>{@link #ADDING} if an item is being added to the currently active {@link Accumulator}.</li>
         *     <li>{@link NextAccumulatorHolder} if the next boundary is received while still {@link #ADDING} or
         *     finishing current {@link Accumulator}.</li>
         *     <li>{@link ItemsTerminated} if the items source terminated but the target is not yet terminated because
         *     we were delivering onNext or because where was not demand.</li>
         *     <li>{@link #TERMINATING} if the items terminated but demand was not checked.</li>
         *     <li>{@link #TERMINATED} if the target subscriber has been terminated (or cancelled).</li>
         * </ul>
         */
        @Nullable
        private volatile Object maybeAccumulator;
        /**
         * Number of pending boundaries to deliver to the target based on expressed demand.
         */
        private volatile long pending;

        State(final int firstItemsRequestN) {
            this.firstItemsRequestN = firstItemsRequestN;
        }

        <T, B> void requested(long n, final Subscriber<? super B> target) {
            final long pending = pendingUpdater.accumulateAndGet(this, n, (prev, nValue) ->
                    prev == MIN_VALUE || nValue <= 0L ? prev : addWithOverflowProtection(prev, nValue));
            if (pending == MIN_VALUE && n > 0L) {
                for (;;) {
                    final Object cMaybeAccumulator = maybeAccumulator;
                    assert cMaybeAccumulator != null;
                    if (ItemsTerminated.class.equals(cMaybeAccumulator.getClass()))  {
                        @SuppressWarnings("unchecked")
                        final ItemsTerminated<T, B> it = (ItemsTerminated<T, B>) cMaybeAccumulator;
                        maybeAccumulator = TERMINATED;
                        terminateTarget(it.accumulator, target, it.terminalNotification);
                        return;
                    }
                }
            }
        }

        <T, B> void accumulate(@Nullable final T item, final Subscriber<? super B> target,
                               final Cancellable bCancellable, final Cancellable tCancellable) {
            for (;;) {
                final Object cMaybeAccumulator = maybeAccumulator;
                assert cMaybeAccumulator != null;   // without the first accumulator there is no demand for items
                // no accumulation is expected after termination:
                assert cMaybeAccumulator != TERMINATING && !(cMaybeAccumulator instanceof ItemsTerminated);

                // This method is called when a new item is received.
                // The subscription for items source is local to this operator and could never be interacted from an
                // external entity. This means we neither re-enter this method nor the items source can terminate
                // when we are inside this method (onNext and onError/onComplete can not be concurrent).
                if (cMaybeAccumulator == TERMINATED) {
                    return;
                }
                assert cMaybeAccumulator != ADDING; // Invocation of 'accumulate' method is expected to be sequential
                // If we are ADDING and maybeAccumulator has changed then either it should have terminated or
                // a new accumulator has been received. If a new accumulator is received, we will finish the current
                // accumulator and emit the result.
                if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, ADDING)) {
                    // Use the next accumulator if the current one is already on the way to the target:
                    @SuppressWarnings("unchecked")
                    final Accumulator<T, B> accumulator =
                            NextAccumulatorHolder.class.equals(cMaybeAccumulator.getClass()) ?
                                    ((NextAccumulatorHolder<T, B>) cMaybeAccumulator).accumulator :
                                    (Accumulator<T, B>) cMaybeAccumulator;
                    accumulator.accumulate(item);
                    final Object nextState = maybeAccumulatorUpdater.accumulateAndGet(this, accumulator,
                            (prev, next) -> prev == ADDING ? next : prev);
                    if (nextState == accumulator || nextState == TERMINATED) {
                        return;
                    }
                    // Received the next boundary while adding, deliver current accumulator and unwrap the next boundary
                    assert nextState instanceof NextAccumulatorHolder : "Expected next boundary";
                    if (deliverOnNext(accumulator, target, bCancellable, tCancellable)) {
                        @SuppressWarnings("unchecked")
                        final Accumulator<T, B> nextAccumulator = ((NextAccumulatorHolder<T, B>) nextState).accumulator;
                        maybeAccumulatorUpdater.accumulateAndGet(this, nextAccumulator,
                                (prev, next) -> prev == TERMINATED ? TERMINATED : next);
                    }
                    return;
                }
            }
        }

        <T, B> void nextAccumulator(final Accumulator<T, B> nextAccumulator, final Subscriber<? super B> target,
                                    final Subscription bSubscription, final Subscription tSubscription) {
            requireNonNull(nextAccumulator);
            for (;;) {
                final Object cMaybeAccumulator = maybeAccumulator;
                if (cMaybeAccumulator == TERMINATING || cMaybeAccumulator == TERMINATED) {
                    return;
                }
                if (cMaybeAccumulator == null) {
                    // This is the first received accumulator (first boundary start); so we just store the accumulator
                    // to accumulate and request items from tSubscription.
                    if (maybeAccumulatorUpdater.compareAndSet(this, null, toCounting(nextAccumulator))) {
                        tSubscription.request(firstItemsRequestN);
                        // Since we did not emit the accumulator we request one more to observe the next boundary:
                        bSubscription.request(1);
                        return;
                    }
                } else if (ItemsTerminated.class.equals(cMaybeAccumulator.getClass())) {
                    // ItemsTerminated with outstanding accumulated data while there was no demand, waiting to demand
                    // to finish termination.
                    return;
                } else if (cMaybeAccumulator == ADDING) {
                    // Hand-off emission of nextAccumulator to the thread that is ADDING
                    if (maybeAccumulatorUpdater.compareAndSet(this, ADDING,
                            new NextAccumulatorHolder<>(nextAccumulator))) {
                        // Thread that is ADDING will observe the change and emit the current accumulator
                        return;
                    }
                } else if (NextAccumulatorHolder.class.equals(cMaybeAccumulator.getClass())) {
                    // One more boundary is received when we already have a "queued" next boundary (we are in ADDING
                    // state or delivering the previous boundary), discarding it.
                    // Since we did not emit the accumulator we request one more to observe the next boundary:
                    bSubscription.request(1);
                    return;
                } else {
                    assert cMaybeAccumulator instanceof CountingAccumulator;
                    final NextAccumulatorHolder<T, B> holder = new NextAccumulatorHolder<>(nextAccumulator);
                    if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, holder)) {
                        @SuppressWarnings("unchecked")
                        Accumulator<T, B> oldAccumulator = (Accumulator<T, B>) cMaybeAccumulator;
                        if (deliverOnNext(oldAccumulator, target, bSubscription, tSubscription)) {
                            final Object nextState = maybeAccumulatorUpdater.accumulateAndGet(this,
                                    // Keep "prev" state because we observed `itemsTerminated` or `accumulate` took
                                    // ownership of the `holder`
                                    toCounting(nextAccumulator), (prev, next) -> prev == holder ? next : prev);
                            assert nextState != null;
                            if (ItemsTerminated.class.equals(nextState.getClass())) {
                                @SuppressWarnings("unchecked")
                                final ItemsTerminated<T, B> it = (ItemsTerminated<T, B>) nextState;
                                // Deliver the last boundary only if there are some items pending and demand is present
                                if (it.accumulator.isEmpty()) {
                                    maybeAccumulator = TERMINATED;
                                    terminateTarget(null, target, it.terminalNotification);
                                } else {
                                    final long demand = pendingUpdater.accumulateAndGet(this, MIN_VALUE,
                                            (prev, next) -> prev > 0L ? prev : next);
                                    if (demand > 0L) {
                                        maybeAccumulator = TERMINATED;
                                        terminateTarget(it.accumulator, target, it.terminalNotification);
                                    }
                                }
                            }
                        }
                        return;
                    }
                }
            }
        }

        <T, B> void itemsTerminated(final TerminalNotification terminalNotification,
                                    final Subscriber<? super B> target, final Cancellable bCancellable) {
            for (;;) {
                final Object cMaybeAccumulator = maybeAccumulator;
                assert cMaybeAccumulator != ADDING; // `accumulate` and `itemsTerminated` are sequential
                if (cMaybeAccumulator == TERMINATED) {
                    return;
                } else if (cMaybeAccumulator != null &&
                        NextAccumulatorHolder.class.equals(cMaybeAccumulator.getClass())) {
                    // Terminated while `nextAccumulator` delivers onNext, notify that thread using ItemsTerminated
                    // wrapper. There is no more concurrency because neither `itemsTerminated` nor `accumulate` will be
                    // invoked again.
                    @SuppressWarnings("unchecked")
                    final ItemsTerminated<T, B> itemsTerminated = new ItemsTerminated<>(
                            ((NextAccumulatorHolder<T, B>) cMaybeAccumulator).accumulator, terminalNotification);
                    if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, itemsTerminated)) {
                        bCancellable.cancel();
                        return;
                    }
                } else if (cMaybeAccumulator == null &&
                        maybeAccumulatorUpdater.compareAndSet(this, null, TERMINATED)) {
                    // Terminated without any produced items or boundaries
                    terminateTarget(null, target, terminalNotification);
                    bCancellable.cancel();
                    return;
                } else if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, TERMINATING)) {
                    assert cMaybeAccumulator instanceof CountingAccumulator;
                    bCancellable.cancel();
                    // Deliver the last boundary only if there are some items pending and demand is present
                    @SuppressWarnings("unchecked")
                    final CountingAccumulator<T, B> accumulator = (CountingAccumulator<T, B>) cMaybeAccumulator;
                    if (accumulator.isEmpty()) {
                        maybeAccumulator = TERMINATED;
                        terminateTarget(null, target, terminalNotification);
                    } else {
                        //
                        final long demand = pendingUpdater.accumulateAndGet(this, MIN_VALUE,
                                (prev, next) -> prev > 0L ? prev : next);
                        if (demand > 0L) {
                            maybeAccumulator = TERMINATED;
                            terminateTarget(accumulator, target, terminalNotification);
                        } else {
                            maybeAccumulator = new ItemsTerminated<>(accumulator, terminalNotification);
                        }
                    }
                    return;
                }
            }
        }

        void boundariesTerminated(final Throwable cause, final Subscriber<?> target) {
            maybeAccumulator = TERMINATED;
            safeOnError(target, cause);
        }

        private <T, B> boolean deliverOnNext(final Accumulator<T, B> accumulator,
                                             final Subscriber<? super B> target,
                                             final Cancellable bCancellable,
                                             final Cancellable tCancellable) {
            try {
                // decrementAndGet alternative without update of the MIN_VALUE state:
                final long pending = pendingUpdater.accumulateAndGet(this, 1,
                        (prev, next) -> prev == MIN_VALUE ? prev : prev - 1);
                assert pending >= 0;
                target.onNext(accumulator.finish());
                return true;
            } catch (Throwable t) {
                terminate(t, target, bCancellable, tCancellable);
                return false;
            }
        }

        private <B> void terminate(final Throwable cause, final Subscriber<? super B> target,
                                   final Cancellable bCancellable, final Cancellable tCancellable) {
            boundariesTerminated(cause, target);
            try {
                bCancellable.cancel();
            } finally {
                tCancellable.cancel();
            }
        }

        private static <T, B> void terminateTarget(@Nullable final Accumulator<T, B> accumulator,
                                                   final Subscriber<? super B> target,
                                                   final TerminalNotification terminalNotification) {
            if (accumulator != null) {
                try {
                    target.onNext(accumulator.finish());
                } catch (Throwable t) {
                    safeOnError(target, t);
                    return;
                }
            }
            Throwable cause = terminalNotification.cause();
            if (cause == null) {
                safeOnComplete(target);
            } else {
                safeOnError(target, cause);
            }
        }
    }

    private static <T, B> CountingAccumulator<T, B> toCounting(final Accumulator<T, B> accumulator) {
        return CountingAccumulator.class.equals(accumulator.getClass()) ? (CountingAccumulator<T, B>) accumulator :
                new CountingAccumulator<>(accumulator);
    }

    private static final class NextAccumulatorHolder<T, B> {
        final CountingAccumulator<T, B> accumulator;

        NextAccumulatorHolder(final Accumulator<T, B> accumulator) {
            this.accumulator = toCounting(accumulator);
        }
    }

    private static final class ItemsTerminated<T, B> {
        final CountingAccumulator<T, B> accumulator;
        final TerminalNotification terminalNotification;

        ItemsTerminated(final CountingAccumulator<T, B> accumulator,
                        final TerminalNotification terminalNotification) {
            this.accumulator = accumulator;
            this.terminalNotification = terminalNotification;
        }
    }
}
