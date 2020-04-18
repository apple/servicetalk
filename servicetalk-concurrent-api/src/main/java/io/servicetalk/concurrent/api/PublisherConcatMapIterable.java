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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.AutoClosableUtils.closeAndReThrowUnchecked;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.acquirePendingLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releasePendingLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Collections.emptyIterator;
import static java.util.Objects.requireNonNull;

final class PublisherConcatMapIterable<T, U> extends AbstractSynchronousPublisherOperator<T, U> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherConcatMapIterable.class);
    private final Function<? super T, ? extends Iterable<? extends U>> mapper;

    PublisherConcatMapIterable(Publisher<T> original, Function<? super T, ? extends Iterable<? extends U>> mapper,
                               Executor executor) {
        super(original, executor);
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
        @Nullable
        private TerminalNotification terminalNotification;
        /**
         * We only ever request a single {@link Iterable} at a time, and wait to request another {@link Iterable} until
         * {@link Iterator#hasNext()} returns {@code false}. This means we don't need to queue {@link Iterator}s, and
         * was done because we don't know how many elements will be returned by each {@link Iterator} and so we are as
         * conservative as we can be about memory consumption.
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
            tryDrainIterator(requestN, ErrorHandlingStrategyInDrain.Throw);
        }

        @Override
        public void onError(Throwable t) {
            terminalNotification = TerminalNotification.error(t);
            tryDrainIterator(requestN, ErrorHandlingStrategyInDrain.Propagate);
        }

        @Override
        public void onComplete() {
            terminalNotification = TerminalNotification.complete();
            tryDrainIterator(requestN, ErrorHandlingStrategyInDrain.Propagate);
        }

        @Override
        public void request(long n) {
            assert sourceSubscription != null;
            if (!isRequestNValid(n)) {
                sourceSubscription.request(n);
            } else {
                tryDrainIterator(requestNUpdater.accumulateAndGet(this, n,
                                    FlowControlUtils::addWithOverflowProtectionIfNotNegative),
                                 ErrorHandlingStrategyInDrain.PropagateAndCancel);
            }
        }

        @Override
        public void cancel() {
            if (requestNUpdater.getAndSet(this, -1) >= 0 && acquirePendingLock(emittingUpdater, this)) {
                doCancel();
            }
        }

        private void doCancel() {
            assert sourceSubscription != null;
            final Iterator<? extends U> currentIterator = this.currentIterator;
            this.currentIterator = EmptyIterator.instance();
            try {
                if (currentIterator instanceof AutoCloseable) {
                    closeAndReThrowUnchecked(((AutoCloseable) currentIterator));
                }
            } finally {
                sourceSubscription.cancel();
            }
        }

        private enum ErrorHandlingStrategyInDrain {
            PropagateAndCancel,
            Propagate,
            Throw
        }

        private void tryDrainIterator(long requestN, ErrorHandlingStrategyInDrain errorHandlingStrategyInDrain) {
            assert sourceSubscription != null;
            boolean hasNext = false;
            boolean terminated = false;
            boolean releasedLock = false;
            do {
                if (!acquirePendingLock(emittingUpdater, this)) {
                    break;
                }
                final long initialRequestN = requestN;
                try {
                    try {
                        while ((hasNext = currentIterator.hasNext()) && requestN > 0) {
                            --requestN;
                            target.onNext(currentIterator.next());
                        }
                    } catch (Throwable cause) {
                        switch (errorHandlingStrategyInDrain) {
                            case PropagateAndCancel:
                                terminated = true;
                                doCancel();
                                try {
                                    target.onError(cause);
                                } catch (Throwable cause2) {
                                    LOGGER.info("Ignoring exception from onError of Subscriber {}.", target, cause2);
                                }
                                break;
                            case Propagate:
                                terminated = true;
                                target.onError(cause);
                                break;
                            case Throw:
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
                    requestN = requestNUpdater.accumulateAndGet(this, requestN - initialRequestN,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                    if (requestN < 0) {
                        terminated = true;
                        // We clear out the current iterator to allow for GC, and we don't want to deliver any more data
                        // because it may be out of order or incomplete ... so simulate a terminated event.
                        doCancel();
                    } else if (!terminated) {
                        try {
                            if (terminalNotification == null && !hasNext && requestN > 0 &&
                                    currentIterator != EmptyIterator.instance()) {
                                // We only request 1 at a time, and therefore we don't have any outstanding demand, so
                                // we will not be getting an onNext call, so we write to the currentIterator variable
                                // here before we unlock emitting so visibility to other threads should be taken care of
                                // by the write to emitting below (and later read).
                                this.currentIterator = EmptyIterator.instance();
                                sourceSubscription.request(1);
                            }
                        } finally {
                            // The lock must be released after we interact with the subscription for thread safety
                            // reasons.
                            releasedLock = releasePendingLock(emittingUpdater, this);
                        }

                        // We may have been cancelled while we were holding the lock, so we need to check if we have
                        // been cancelled after we release the lock.
                        requestN = this.requestN;
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
