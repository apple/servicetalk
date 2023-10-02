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

import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

final class ReplayPublisher<T> extends MulticastPublisher<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<ReplayPublisher.ReplayState> signalQueuedUpdater =
            AtomicLongFieldUpdater.newUpdater(ReplayPublisher.ReplayState.class, "signalsQueued");
    private final Supplier<ReplayAccumulator<T>> accumulatorSupplier;

    private ReplayPublisher(
            Publisher<T> original, Supplier<ReplayAccumulator<T>> accumulatorSupplier, int minSubscribers,
            boolean cancelUpstream, int maxQueueSize, Function<Throwable, Completable> terminalResubscribe) {
        super(original, minSubscribers, false, cancelUpstream, maxQueueSize, terminalResubscribe);
        this.accumulatorSupplier = requireNonNull(accumulatorSupplier);
    }

    static <T> MulticastPublisher<T> newReplayPublisher(
            Publisher<T> original, Supplier<ReplayAccumulator<T>> accumulatorSupplier, int minSubscribers,
            boolean cancelUpstream, int maxQueueSize, Function<Throwable, Completable> terminalResubscribe) {
        ReplayPublisher<T> publisher = new ReplayPublisher<>(original, accumulatorSupplier, minSubscribers,
                cancelUpstream, minSubscribers, terminalResubscribe);
        publisher.resetState(maxQueueSize, minSubscribers);
        return publisher;
    }

    @Override
    void resetState(int maxQueueSize, int minSubscribers) {
        state = new ReplayState(maxQueueSize, minSubscribers, accumulatorSupplier.get());
    }

    private final class ReplayState extends MulticastPublisher<T>.State {
        private final ReplayAccumulator<T> accumulator;
        /**
         * We could check {@link #subscriptionEvents} is empty, but there are events outside of {@link Subscriber}
         * signals in this queue that we don't care about in terms of preserving order, so we keep this count instead
         * to only queue when necessary.
         */
        volatile long signalsQueued;

        ReplayState(final int maxQueueSize, final int minSubscribers,
                    ReplayAccumulator<T> accumulator) {
            super(maxQueueSize, minSubscribers);
            this.accumulator = requireNonNull(accumulator);
        }

        @Override
        public void onNext(@Nullable final T t) {
            // signalsQueued must be 0 or else items maybe delivered out of order. The value will only be increased
            // on the Subscriber thread (no concurrency) and decreased on the draining thread. Optimistically check
            // the value here and worst case if the queue has been drained of signals and this thread hasn't yet
            // observed the value we will queue but still see correct ordering.
            if (signalsQueued == 0 && tryAcquireLock(subscriptionLockUpdater, this)) {
                try {
                    // All subscribers must either see this direct onNext signal, or see it through the accumulator.
                    // Therefore, we accumulate and deliver onNext while locked to avoid either delivering the signal
                    // twice (accumulator, addSubscriber, and onNext) or not at all (missed due to concurrency).
                    accumulator.accumulate(t);
                    super.onNext(t);
                } finally {
                    if (!releaseLock(subscriptionLockUpdater, this)) {
                        processSubscriptionEvents();
                    }
                }
            } else {
                queueOnNext(t);
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (signalsQueued == 0 && tryAcquireLock(subscriptionLockUpdater, this)) {
                try {
                    super.onError(t);
                } finally {
                    if (!releaseLock(subscriptionLockUpdater, this)) {
                        processSubscriptionEvents();
                    }
                }
            } else {
                queueTerminal(error(t));
            }
        }

        @Override
        public void onComplete() {
            if (signalsQueued == 0 && tryAcquireLock(subscriptionLockUpdater, this)) {
                try {
                    super.onComplete();
                } finally {
                    if (!releaseLock(subscriptionLockUpdater, this)) {
                        processSubscriptionEvents();
                    }
                }
            } else {
                queueTerminal(complete());
            }
        }

        @Override
        void processOnNextEvent(Object wrapped) {
            // subscriptionLockUpdater is held
            signalQueuedUpdater.decrementAndGet(this);
            final T unwrapped = unwrapNullUnchecked(wrapped);
            accumulator.accumulate(unwrapped);
            super.onNext(unwrapped);
        }

        @Override
        void processTerminal(TerminalNotification terminalNotification) {
            // subscriptionLockUpdater is held
            signalQueuedUpdater.decrementAndGet(this);
            if (terminalNotification.cause() != null) {
                super.onError(terminalNotification.cause());
            } else {
                super.onComplete();
            }
        }

        @Override
        boolean processSubscribeEvent(MulticastFixedSubscriber<T> subscriber,
                                      @Nullable TerminalSubscriber<?> terminalSubscriber) {
            // subscriptionLockUpdater is held
            if (terminalSubscriber == null) {
                // Only call the super class if no terminal event. We don't want the super class to terminate
                // the subscriber because we need to deliver any accumulated signals, and we also don't want to
                // track state in demandQueue because it isn't necessary to manage upstream demand, and we don't want
                // to hold a reference to the subscriber unnecessarily.
                super.processSubscribeEvent(subscriber, null);
            }
            Throwable caughtCause = null;
            try {
                // It's safe to call onNext before onSubscribe bcz the base class expects onSubscribe to be async and
                // queues/reorders events to preserve ReactiveStreams semantics.
                accumulator.deliverAccumulation(subscriber::onNext);
            } catch (Throwable cause) {
                caughtCause = cause;
            } finally {
                if (terminalSubscriber != null) {
                    if (caughtCause != null) {
                        if (terminalSubscriber.terminalError != null) {
                            // Use caughtCause as original otherwise we keep appending to the cached Throwable.
                            safeOnError(subscriber, addSuppressed(caughtCause, terminalSubscriber.terminalError));
                        } else {
                            safeOnError(subscriber, caughtCause);
                        }
                    } else if (terminalSubscriber.terminalError != null) {
                        safeOnError(subscriber, terminalSubscriber.terminalError);
                    } else {
                        safeOnComplete(subscriber);
                    }
                } else if (caughtCause != null) {
                    safeOnError(subscriber, caughtCause);
                }
            }
            // Even if we terminated we always want to continue processing to trigger onSubscriber and allow queued
            // signals from above to be processed when demand arrives.
            return true;
        }

        @Override
        void upstreamCancelled() {
            // subscriptionLockUpdater is held
            accumulator.cancelAccumulation();
        }

        private void queueOnNext(@Nullable T t) {
            signalQueuedUpdater.incrementAndGet(this);
            subscriptionEvents.add(wrapNull(t));
            processSubscriptionEvents();
        }

        private void queueTerminal(TerminalNotification terminalNotification) {
            signalQueuedUpdater.incrementAndGet(this);
            subscriptionEvents.add(terminalNotification);
            processSubscriptionEvents();
        }
    }
}
