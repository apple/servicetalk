/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.AutoClosableUtils.closeAndReThrowUnchecked;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static java.util.Collections.emptyIterator;
import static java.util.Objects.requireNonNull;

final class PublisherConcatMapIterable<T, U> extends AbstractSynchronousPublisherOperator<T, U> {
    private static final long CANCEL_PENDING = -1;
    private static final long CANCELLED = Long.MIN_VALUE;
    private final Function<? super T, ? extends Iterable<? extends U>> mapper;

    PublisherConcatMapIterable(Publisher<T> original, Function<? super T, ? extends Iterable<? extends U>> mapper) {
        super(original);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super U> subscriber) {
        return new FlatMapIterableSubscriber<>(mapper, subscriber);
    }

    private static final class FlatMapIterableSubscriber<T, U> implements Subscriber<T>, Subscription {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapIterableSubscriber> requestNUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapIterableSubscriber.class, "requestN");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapIterableSubscriber> emittingUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapIterableSubscriber.class, "emitting");
        private final Function<? super T, ? extends Iterable<? extends U>> mapper;
        private final Subscriber<? super U> target;
        @Nullable
        private Subscription sourceSubscription;
        /**
         * Visibility and thread safety provided by {@link #emitting}.
         */
        @Nullable
        private TerminalNotification terminalNotification;
        /**
         * We only ever request a single {@link Iterable} at a time, and wait to request another {@link Iterable} until
         * {@link Iterator#hasNext()} returns {@code false}. This means we don't need to queue {@link Iterator}s, and
         * was done because we don't know how many elements will be returned by each {@link Iterator} and so we are as
         * conservative as we can be about memory consumption.
         * <p>
         * Visibility and thread safety provided by {@link #emitting}.
         */
        private Iterator<? extends U> currentIterator = emptyIterator();
        @SuppressWarnings("unused")
        private volatile long requestN;
        @SuppressWarnings("unused")
        private volatile int emitting;

        FlatMapIterableSubscriber(Function<? super T, ? extends Iterable<? extends U>> mapper,
                                  Subscriber<? super U> target) {
            this.target = target;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (checkDuplicateSubscription(sourceSubscription, s)) {
                sourceSubscription = s;
                target.onSubscribe(this);
            }
        }

        @Override
        public void onNext(T u) {
            // If Function.apply(...) throws we just propagate it to the caller which is responsible to terminate
            // its subscriber and cancel the subscription.
            currentIterator = requireNonNull(mapper.apply(u).iterator());
            tryDrainIterator(ErrorHandlingStrategyInDrain.Throw);
        }

        @Override
        public void onError(Throwable t) {
            terminalNotification = TerminalNotification.error(t);
            tryDrainIterator(ErrorHandlingStrategyInDrain.Propagate);
        }

        @Override
        public void onComplete() {
            terminalNotification = TerminalNotification.complete();
            tryDrainIterator(ErrorHandlingStrategyInDrain.Propagate);
        }

        @Override
        public void request(long n) {
            assert sourceSubscription != null;
            if (!isRequestNValid(n)) {
                sourceSubscription.request(n);
            } else {
                requestNUpdater.accumulateAndGet(this, n,
                        FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                tryDrainIterator(ErrorHandlingStrategyInDrain.PropagateAndCancel);
            }
        }

        @Override
        public void cancel() {
            for (;;) {
                final long currRequestN = requestN;
                if (currRequestN < 0) {
                    break;
                } else if (requestNUpdater.compareAndSet(this, currRequestN, CANCEL_PENDING)) {
                    if (tryAcquireLock(emittingUpdater, this)) {
                        try {
                            requestN = CANCELLED;
                            doCancel();
                        } finally {
                            if (!releaseLock(emittingUpdater, this)) {
                                tryDrainIterator(ErrorHandlingStrategyInDrain.Propagate);
                            }
                        }
                    }
                    break;
                }
            }
        }

        private void doCancel() {
            assert sourceSubscription != null;
            final Iterator<? extends U> currentIterator = this.currentIterator;
            this.currentIterator = EmptyIterator.instance();
            try {
                tryClose(currentIterator);
            } finally {
                sourceSubscription.cancel();
            }
        }

        private static <U> void tryClose(final Iterator<? extends U> currentIterator) {
            if (currentIterator instanceof AutoCloseable) {
                closeAndReThrowUnchecked(((AutoCloseable) currentIterator));
            }
        }

        private enum ErrorHandlingStrategyInDrain {
            PropagateAndCancel,
            Propagate,
            Throw
        }

        private void tryDrainIterator(ErrorHandlingStrategyInDrain errorHandlingStrategyInDrain) {
            boolean hasNext = false;
            boolean thrown = false;
            boolean terminated = false;
            boolean releasedLock = false;
            do {
                if (!tryAcquireLock(emittingUpdater, this)) {
                    break;
                }
                long currRequestN = this.requestN;
                final long initialRequestN = currRequestN;
                try {
                    try {
                        while ((hasNext = currentIterator.hasNext()) && currRequestN > 0) {
                            --currRequestN;
                            target.onNext(currentIterator.next());
                        }
                    } catch (Throwable cause) {
                        switch (errorHandlingStrategyInDrain) {
                            case PropagateAndCancel:
                                terminated = true;
                                safeOnError(target, cause);
                                doCancel();
                                return; // hard return to avoid potential for duplicate terminal events
                            case Propagate:
                                terminated = true;
                                safeOnError(target, cause);
                                tryClose(currentIterator);
                                return; // hard return to avoid potential for duplicate terminal events
                            case Throw:
                                // since we only request 1 at a time we maybe holding requestN demand, in this case we
                                // discard the current iterator and request 1 more from upstream (if there is demand).
                                hasNext = false;
                                thrown = true;
                                final Iterator<? extends U> currentIterator = this.currentIterator;
                                this.currentIterator = EmptyIterator.instance();
                                tryClose(currentIterator);
                                // let the exception propagate so the upstream source can do the cleanup.
                                throw cause;
                            default:
                                throw new IllegalArgumentException("Unknown error handling strategy: " +
                                        errorHandlingStrategyInDrain);
                        }
                    }
                    if (terminalNotification != null && !hasNext) {
                        terminated = true;
                        terminalNotification.terminate(target);
                    }
                } finally {
                    // If we terminated we don't want to unlock, otherwise we may propagate duplicate terminal signals.
                    if (!terminated) {
                        currRequestN = requestNUpdater.accumulateAndGet(this, currRequestN - initialRequestN,
                                FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                        try {
                            if (currRequestN == CANCEL_PENDING) {
                                terminated = true;
                                // If we throw we expect an error to be propagated, so we are effectively cancelled.
                                requestN = CANCELLED;
                                if (!thrown) {
                                    // We have been cancelled while we held the lock, do the cancel operation.
                                    doCancel();
                                }
                            } else if (terminalNotification == null && !hasNext && currRequestN > 0 &&
                                    (currentIterator != EmptyIterator.instance() || thrown)) {
                                // We only request 1 at a time, and therefore we don't have any outstanding demand, so
                                // we will not be getting an onNext call, so we write to the currentIterator variable
                                // here before we unlock emitting so visibility to other threads should be taken care of
                                // by the write to emitting below (and later read).
                                currentIterator = EmptyIterator.instance();
                                if (sourceSubscription != null) {
                                    sourceSubscription.request(1);
                                }
                            }
                        } finally {
                            // The lock must be released after we interact with the subscription for thread safety
                            // reasons.
                            releasedLock = releaseLock(emittingUpdater, this);
                        }
                    }
                }
            } while (!terminated && !releasedLock);
        }

        /**
         * This exists instead of using {@link Collections#emptyIterator()} so we can differentiate between user data
         * and internal state based upon empty iterator.
         * @param <U> The type of element in the iterator.
         */
        private static final class EmptyIterator<U> implements Iterator<U> {
            @SuppressWarnings("rawtypes")
            private static final EmptyIterator INSTANCE = new EmptyIterator();

            private EmptyIterator() {
                // singleton
            }

            @SuppressWarnings("unchecked")
            static <T> EmptyIterator<T> instance() {
                return (EmptyIterator<T>) INSTANCE;
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public U next() {
                throw new NoSuchElementException();
            }
        }
    }
}
