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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.ConcurrentSubscription.wrap;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class PublisherBuffer<T, B> extends AbstractAsynchronousPublisherOperator<T, B> {
    private final BufferStrategy<T, ?, B> bufferStrategy;

    PublisherBuffer(final Publisher<T> original, final Executor executor,
                    final BufferStrategy<T, ?, B> bufferStrategy) {
        super(original, executor);
        this.bufferStrategy = requireNonNull(bufferStrategy);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super B> subscriber) {
        final int bufferSizeHint = bufferStrategy.bufferSizeHint();
        if (bufferSizeHint <= 0) {
            deliverErrorFromSource(subscriber,
                    new IllegalArgumentException("bufferSizeHint: " + bufferSizeHint + " (expected > 0)"));
        }
        return new ItemsSubscriber<>(bufferStrategy.boundaries(), subscriber, bufferSizeHint);
    }

    private static final class ItemsSubscriber<T, B> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ItemsSubscriber> pendingUpdater =
                newUpdater(ItemsSubscriber.class, "pending");

        private final Subscriber<? super B> target;
        private final State state;
        private final DelayedSubscription tSubscription;
        private final int bufferSizeHint;
        private final DelayedCancellable bCancellable;

        @Nullable
        private ConcurrentSubscription subscription;
        private volatile long pending;

        ItemsSubscriber(final Publisher<? extends Accumulator<T, B>> boundaries,
                        final Subscriber<? super B> target, final int bufferSizeHint) {
            this.target = target;
            state = new State(bufferSizeHint);
            tSubscription = new DelayedSubscription();
            this.bufferSizeHint = bufferSizeHint;
            // Request-n is delayed till we receive the first boundary but we will request bufferSizeHint.
            // This is done here to localize state management (pending count) in this subscriber but still drive the
            // first request-n from inside State.
            pending = bufferSizeHint;
            final BoundariesSubscriber<T, B> bSubscriber = new BoundariesSubscriber<>(state, target, tSubscription);
            bCancellable = bSubscriber;
            toSource(boundaries).subscribe(bSubscriber);
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            this.subscription = wrap(subscription);
            tSubscription.delayedSubscription(this.subscription);
        }

        @Override
        public void onNext(@Nullable final T t) {
            final long pending = pendingUpdater.decrementAndGet(this);
            state.accumulate(t, target);
            if (pending == 0) {
                assert subscription != null;
                requestMoreItems(subscription);
            }
        }

        @Override
        public void onError(final Throwable t) {
            state.itemsTerminated(error(t), target, bCancellable);
        }

        @Override
        public void onComplete() {
            state.itemsTerminated(complete(), target, bCancellable);
        }

        private void requestMoreItems(final Subscription subscription) {
            pendingUpdater.accumulateAndGet(this, bufferSizeHint, FlowControlUtils::addWithOverflowProtection);
            subscription.request(bufferSizeHint);
        }
    }

    private static final class BoundariesSubscriber<T, B> extends DelayedCancellable
            implements Subscriber<Accumulator<T, B>> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<BoundariesSubscriber> pendingUpdater =
                newUpdater(BoundariesSubscriber.class, "pending");
        private final State state;
        private final Subscriber<? super B> target;
        private final Subscription tSubscription;

        private volatile long pending;
        @Nullable
        private ConcurrentSubscription subscription;

        BoundariesSubscriber(final State state, final Subscriber<? super B> target, final Subscription tSubscription) {
            this.state = state;
            this.target = target;
            this.tSubscription = tSubscription;
        }

        @Override
        public void onSubscribe(final Subscription bSubscription) {
            subscription = wrap(new Subscription() {
                @Override
                public void request(final long n) {
                    if (isRequestNValid(n)) {
                        pendingUpdater.accumulateAndGet(BoundariesSubscriber.this, n,
                                FlowControlUtils::addWithOverflowProtection);
                    }
                    bSubscription.request(n);
                }

                @Override
                public void cancel() {
                    try {
                        bSubscription.cancel();
                    } finally {
                        tSubscription.cancel();
                    }
                }
            });
            delayedCancellable(subscription);
            target.onSubscribe(subscription);
        }

        @Override
        public void onNext(@Nonnull final Accumulator<T, B> accumulator) {
            requireNonNull(accumulator);
            pendingUpdater.decrementAndGet(this);
            assert subscription != null;
            if (!state.nextAccumulator(accumulator, target, subscription, tSubscription)) {
                pendingUpdater.incrementAndGet(this);
                subscription.request(1);
            }
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
        private static final Object TERMINATED = new Object();
        private static final AtomicReferenceFieldUpdater<State, Object> maybeAccumulatorUpdater =
                AtomicReferenceFieldUpdater.newUpdater(State.class, Object.class, "maybeAccumulator");

        private final int firstItemsRequestN;
        /**
         * Following values are assigned to this variable:
         * <ul>
         *     <li>{@code null} till the first accumulator arrives.</li>
         *     <li>{@link #ADDING} if an item is being added to the currently active {@link Accumulator}</li>
         *     <li>{@link ItemsTerminated} if the items source terminated but the target is not yet terminated.</li>
         *     <li>{@link #TERMINATED} if the target subscriber has been terminated (or cancelled).</li>
         *     <li>{@link Accumulator} which is emitted by the boundaries source.</li>
         * </ul>
         */
        @Nullable
        private volatile Object maybeAccumulator;

        State(final int firstItemsRequestN) {
            this.firstItemsRequestN = firstItemsRequestN;
        }

        <T, B> void accumulate(@Nullable final T item, final Subscriber<? super B> target) {
            Accumulator<T, B> missedAccumulator = null;
            for (;;) {
                final Object cMaybeAccumulator = maybeAccumulator;
                assert cMaybeAccumulator != null;

                // This method is called when a new item is received.
                // The subscription for items source is local to this operator and could never be interacted from an
                // external entity. This means we neither re-enter this method nor the items source can terminate
                // when we are inside this method (onNext and onError/onComplete can not be concurrent).
                if (cMaybeAccumulator == TERMINATED || cMaybeAccumulator instanceof ItemsTerminated) {
                    return;
                }
                assert cMaybeAccumulator != ADDING;
                // If we are ADDING and maybeAccumulatorUpdater has changed then either it should have terminated or
                // a new accumulator has been received. If a new accumulator is received, we could have never added to
                // that accumulator (as we were adding to an old accumulator), so we discard that accumulator.
                if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, ADDING)) {
                    assert cMaybeAccumulator instanceof Accumulator;
                    @SuppressWarnings("unchecked")
                    final Accumulator<T, B> accumulator = (Accumulator<T, B>) cMaybeAccumulator;
                    if (missedAccumulator != null) {
                        target.onNext(missedAccumulator.finish());
                    } else {
                        accumulator.accumulate(item);
                    }
                    if (maybeAccumulatorUpdater.compareAndSet(this, ADDING, accumulator)) {
                        return;
                    } else if (missedAccumulator == null) {
                        missedAccumulator = accumulator;
                    } else {
                        // we have added an item to the accumulator and emitted the missedAccumulator. The accumulator
                        // in maybeAccumulator is now the current to which subsequent items must be added.
                        return;
                    }
                }
            }
        }

        <T, B> boolean nextAccumulator(final Accumulator<T, B> nextAccumulator, final Subscriber<? super B> target,
                                       final Cancellable boundariesCancellable, final Subscription itemsSubscription) {
            requireNonNull(nextAccumulator);
            for (;;) {
                final Object cMaybeAccumulator = maybeAccumulator;
                if (cMaybeAccumulator == TERMINATED) {
                    return true;
                }
                if (cMaybeAccumulator == null) {
                    // This is the first received accumulator (first boundary start); so we just store the accumulator
                    // to accumulate and request items from itemsSubscription.
                    if (maybeAccumulatorUpdater.compareAndSet(this, null, nextAccumulator)) {
                        itemsSubscription.request(firstItemsRequestN);
                        return false;
                    }
                } else if (cMaybeAccumulator == ADDING) {
                    // Hand-off emission of nextAccumulator to the thread that is ADDING
                    if (maybeAccumulatorUpdater.compareAndSet(this, ADDING, nextAccumulator)) {
                        // ADDING thread will emit nextAccumulator.
                        return true;
                    }
                } else if (cMaybeAccumulator instanceof ItemsTerminated) {
                    if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, TERMINATED)) {
                        @SuppressWarnings("unchecked")
                        ItemsTerminated<T, B> itemsTerminated = (ItemsTerminated<T, B>) cMaybeAccumulator;
                        terminateTarget(itemsTerminated.accumulator, target, itemsTerminated.terminalNotification,
                                boundariesCancellable);
                        return true;
                    }
                } else {
                    assert cMaybeAccumulator instanceof Accumulator;
                    if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, nextAccumulator)) {
                        @SuppressWarnings("unchecked")
                        Accumulator<T, B> oldAccumulator = (Accumulator<T, B>) cMaybeAccumulator;
                        target.onNext(oldAccumulator.finish());
                        return true;
                    }
                }
            }
        }

        <B> void itemsTerminated(final TerminalNotification terminalNotification, final Subscriber<? super B> target,
                                 final Cancellable bCancellable) {
            for (;;) {
                final Object cMaybeAccumulator = maybeAccumulator;
                if (cMaybeAccumulator == TERMINATED) {
                    return;
                }
                assert !(cMaybeAccumulator instanceof ItemsTerminated);

                if (cMaybeAccumulator == ADDING || cMaybeAccumulator == null) {
                    // we threw from onNext or items source completed before request-n
                    if (maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, TERMINATED)) {
                        terminalNotification.terminate(target);
                        return;
                    }
                } else if (cMaybeAccumulator instanceof Accumulator &&
                        maybeAccumulatorUpdater.compareAndSet(this, cMaybeAccumulator, TERMINATED)) {
                    @SuppressWarnings("unchecked")
                    Accumulator<?, B> accumulator = (Accumulator<?, B>) cMaybeAccumulator;
                    terminateTarget(accumulator, target, terminalNotification, bCancellable);
                    return;
                }
            }
        }

        void boundariesTerminated(final Throwable cause, final Subscriber<?> target) {
            maybeAccumulator = TERMINATED;
            target.onError(cause);
        }

        private static <T, B> void terminateTarget(@Nullable final Accumulator<T, B> accumulator,
                                                   final Subscriber<? super B> target,
                                                   final TerminalNotification terminalNotification,
                                                   final Cancellable bCancellable) {
            try {
                if (accumulator != null) {
                    try {
                        target.onNext(accumulator.finish());
                    } catch (Throwable t) {
                        target.onError(t);
                        return;
                    }
                }
                Throwable cause = terminalNotification.cause();
                if (cause == null) {
                    safeOnComplete(target);
                } else {
                    safeOnError(target, cause);
                }
            } finally {
                bCancellable.cancel();
            }
        }
    }

    private static final class ItemsTerminated<T, B> {
        @Nullable
        final Accumulator<T, B> accumulator;
        final TerminalNotification terminalNotification;

        ItemsTerminated(final Object maybeAccumulator, final TerminalNotification terminalNotification) {
            if (maybeAccumulator instanceof Accumulator) {
                @SuppressWarnings("unchecked")
                Accumulator<T, B> accumulator = (Accumulator<T, B>) maybeAccumulator;
                this.accumulator = accumulator;
            } else {
                accumulator = null;
            }
            this.terminalNotification = terminalNotification;
        }
    }
}
