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

import io.servicetalk.concurrent.internal.FlowControlUtil;
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
import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_EMITTING;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_IDLE;
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
        private static final AtomicLongFieldUpdater<FlatMapIterableSubscriber> requestNUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapIterableSubscriber.class, "requestN");
        private static final AtomicIntegerFieldUpdater<FlatMapIterableSubscriber> emittingUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapIterableSubscriber.class, "emitting");
        private final Function<? super T, ? extends Iterable<? extends U>> mapper;
        private final Subscriber<? super U> target;
        @Nullable
        private volatile Subscription sourceSubscription;
        @Nullable
        private volatile TerminalNotification terminalNotification;
        /**
         * We only ever request a single {@link Iterable} at a time, and wait to request another {@link Iterable} until
         * {@link Iterator#hasNext()} returns {@code false}. This means we don't need to queue {@link Iterator}s, and
         * was done because we don't know how many elements will be returned by each {@link Iterator} and so we are as
         * conservative as we can be about memory consumption.
         */
        private volatile Iterator<? extends U> currentIterator = emptyIterator();
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
            Subscription sourceSubscription = this.sourceSubscription;
            assert sourceSubscription != null;
            // If Function.apply(...) throws we just propagate it to the caller which is responsible to terminate
            // its subscriber and cancel the subscription.
            Iterator<? extends U> currentIterator = requireNonNull(mapper.apply(u).iterator());
            this.currentIterator = currentIterator;
            tryDrainIterator(currentIterator, sourceSubscription, terminalNotification, requestN,
                    ErrorHandlingStrategyInDrain.Throw);
        }

        @Override
        public void onError(Throwable t) {
            Subscription sourceSubscription = this.sourceSubscription;
            assert sourceSubscription != null;
            TerminalNotification terminalNotification = TerminalNotification.error(t);
            this.terminalNotification = terminalNotification;
            tryDrainIterator(currentIterator, sourceSubscription, terminalNotification, requestN,
                    ErrorHandlingStrategyInDrain.Propagate);
        }

        @Override
        public void onComplete() {
            Subscription sourceSubscription = this.sourceSubscription;
            assert sourceSubscription != null;
            TerminalNotification terminalNotification = TerminalNotification.complete();
            this.terminalNotification = terminalNotification;
            tryDrainIterator(currentIterator, sourceSubscription, terminalNotification, requestN,
                    ErrorHandlingStrategyInDrain.Propagate);
        }

        @Override
        public void request(long n) {
            Subscription sourceSubscription = this.sourceSubscription;
            assert sourceSubscription != null;
            if (!isRequestNValid(n)) {
                sourceSubscription.request(n);
            } else {
                tryDrainIterator(currentIterator, sourceSubscription, terminalNotification,
                        requestNUpdater.accumulateAndGet(this, n,
                                FlowControlUtil::addWithOverflowProtectionIfNotNegative),
                        ErrorHandlingStrategyInDrain.PropagateAndCancel);
            }
        }

        @Override
        public void cancel() {
            Subscription sourceSubscription = this.sourceSubscription;
            assert sourceSubscription != null;
            if (requestNUpdater.getAndSet(this, -1) >= 0 && emittingUpdater.compareAndSet(this, CONCURRENT_IDLE,
                    CONCURRENT_EMITTING)) {
                doCancel(sourceSubscription);
            }
        }

        private void doCancel(Subscription sourceSubscription) {
            Iterator<? extends U> currentIterator = this.currentIterator;
            this.currentIterator = EmptyIterator.instance();
            if (currentIterator instanceof AutoCloseable) {
                closeAndReThrowUnchecked(((AutoCloseable) currentIterator));
            }
            sourceSubscription.cancel();
        }

        private enum ErrorHandlingStrategyInDrain {
            PropagateAndCancel,
            Propagate,
            Throw
        }

        private void tryDrainIterator(Iterator<? extends U> currentIterator, Subscription sourceSubscription,
                                      @Nullable TerminalNotification terminalNotification, long requestN,
                                      ErrorHandlingStrategyInDrain errorHandlingStrategyInDrain) {
            boolean hasNext = false;
            boolean terminated = false;
            do {
                if (!emittingUpdater.compareAndSet(this, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
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
                                doCancel(sourceSubscription);
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
                            FlowControlUtil::addWithOverflowProtectionIfNotNegative);
                    if (requestN < 0) {
                        terminated = true;
                        // We clear out the current iterator to allow for GC, and we don't want to deliver any more data
                        // because it may be out of order or incomplete ... so simulate a terminated event.
                        doCancel(sourceSubscription);
                    } else if (!terminated) {
                        final Iterator<? extends U> previousIterator;
                        try {
                            if (terminalNotification == null && !hasNext && requestN > 0 &&
                                    currentIterator != EmptyIterator.instance()) {
                                // We only request 1 at a time, and therefore we don't have any outstanding demand, so
                                // we will not be getting an onNext call, so we write to the currentIterator variable
                                // here before we unlock emitting so visibility to other threads should be taken care of
                                // by the write to emitting below (and later read).
                                this.currentIterator = previousIterator = EmptyIterator.instance();
                                sourceSubscription.request(1);
                            } else {
                                previousIterator = currentIterator;
                            }
                        } finally {
                            // The lock must be released after we interact with the subscription for thread safety
                            // reasons.
                            emitting = CONCURRENT_IDLE;
                        }

                        // We may have been cancelled while we were holding the lock, so we need to check if we have
                        // been cancelled after we release the lock.
                        requestN = this.requestN;

                        // We may requested more data while we held the lock. If data was delivered in a re-entry
                        // fashion, or if data was delivered on another thread while we held the lock we may have to try
                        // to re-acquire the lock and drain the new Iterator.
                        currentIterator = this.currentIterator;
                        if (previousIterator != currentIterator) {
                            // We only want to interact with the iterator inside the lock, and we don't want to exit
                            // early if we have seen a terminal event in the mean time, so if there is demand we assume
                            // for now that hasNext is true, and we will find out if there is really data if we
                            // re-acquire the lock.
                            hasNext = requestN > 0;
                        }
                    }
                }
            } while (!terminated &&
                    (requestN < 0 || (requestN > 0 && hasNext) ||
                    (!hasNext && (terminalNotification = this.terminalNotification) != null)));
        }

        /**
         * This exists instead of using {@link Collections#emptyIterator()} so we can differentiate between user data
         * and internal state based upon empty iterator.
         * @param <U> The type of element in the iterator.
         */
        private static final class EmptyIterator<U> implements Iterator<U> {
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
