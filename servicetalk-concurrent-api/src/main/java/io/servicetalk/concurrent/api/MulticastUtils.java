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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.SUBSCRIBER_STATE_IDLE;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.SUBSCRIBER_STATE_ON_NEXT;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.SubscriberUtils.calculateSourceRequested;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static java.util.Objects.requireNonNull;

final class MulticastUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MulticastUtils.class);

    private MulticastUtils() {
        // no instances
    }

    abstract static class IndividualMulticastSubscriber<T> implements Subscription {
        private static final Logger LOGGER = LoggerFactory.getLogger(IndividualMulticastSubscriber.class);
        private static final AtomicIntegerFieldUpdater<IndividualMulticastSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(IndividualMulticastSubscriber.class, "subscriberState");
        private static final AtomicLongFieldUpdater<IndividualMulticastSubscriber> requestedUpdater =
                AtomicLongFieldUpdater.newUpdater(IndividualMulticastSubscriber.class, "requested");
        private static final AtomicLongFieldUpdater<IndividualMulticastSubscriber> sourceRequestedUpdater =
                AtomicLongFieldUpdater.newUpdater(IndividualMulticastSubscriber.class, "sourceRequested");
        private static final AtomicLongFieldUpdater<IndividualMulticastSubscriber> sourceEmittedUpdater =
                AtomicLongFieldUpdater.newUpdater(IndividualMulticastSubscriber.class, "sourceEmitted");

        private boolean terminatedPrematurely;
        @SuppressWarnings("unused")
        private volatile int subscriberState;
        @SuppressWarnings("unused")
        private volatile long requested;
        @SuppressWarnings("unused")
        private volatile long sourceRequested;
        @SuppressWarnings("unused")
        private volatile long sourceEmitted;
        @SuppressWarnings("unused")
        @Nullable
        volatile Subscriber<? super T> target;
        @Nullable
        private volatile SpscQueue<T> subscriberQueue;
        private final int maxQueueSize;

        IndividualMulticastSubscriber(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
        }

        IndividualMulticastSubscriber(int maxQueueSize, Subscriber<? super T> target) {
            this.maxQueueSize = maxQueueSize;
            this.target = requireNonNull(target);
        }

        @Nullable
        final SpscQueue<T> subscriberQueue() {
            return subscriberQueue;
        }

        private void drainPendingFromSource(SpscQueue<T> subscriberQueue) {
            drainPendingFromSource(subscriberQueue, target);
        }

        private void drainPendingFromSource(SpscQueue<T> subscriberQueue,
                                            @Nullable Subscriber<? super T> groupSinkTarget) {
            if (groupSinkTarget == null) {
                return;
            }
            final long drainCount = drainToSubscriber(subscriberQueue, groupSinkTarget, subscriberStateUpdater,
                    () -> requested - sourceEmitted, terminalNotification -> { }, this::cancelSourceFromSource,
                    this::drainPendingHandleEmitted, this);
            if (drainCount > 0) {
                updateRequestN();
            }
        }

        final void drainPendingFromExternal(SpscQueue<T> subscriberQueue, Subscriber<? super T> groupSinkTarget) {
            // When draining from external source we should always call updateRequestN, because we have increased the
            // requestedUpdater.
            drainToSubscriber(subscriberQueue, groupSinkTarget, subscriberStateUpdater,
                    () -> requested - sourceEmitted, terminalNotification -> { }, this::cancelSourceFromExternal,
                    this::drainPendingHandleEmitted, this);
            updateRequestN();
        }

        private void drainPendingHandleEmitted(int onNextCount) {
            // We ignore overflow here because once we get to this extreme, we won't be able to account for more data
            // anyways.
            sourceEmittedUpdater.addAndGet(this, onNextCount);
        }

        private void cancelSourceFromSource(Throwable cause) {
            SpscQueue<T> subscriberQueue = this.subscriberQueue;
            assert subscriberQueue != null;
            cancelSourceFromSource(true, cause, target, subscriberQueue);
        }

        private void cancelSourceFromSource(boolean subscriberLockAcquired, Throwable cause,
                                            @Nullable Subscriber<? super T> groupSinkTarget,
                                            @Nullable SpscQueue<T> groupSinkQueue) {
            terminatedPrematurely = true;
            cancelSourceFromSource(subscriberLockAcquired, cause);
            if (groupSinkTarget == null) {
                assert groupSinkQueue != null;
                // If there is no target it is likely this object will just be GC'ed, but it is possible someone could
                // subscribe in a racy fashion and eventually get the data.
                groupSinkQueue.addTerminal(TerminalNotification.error(cause));
                drainPendingFromSource(groupSinkQueue);
            } else if (groupSinkQueue == null || groupSinkQueue.isEmpty()) {
                if (subscriberLockAcquired || subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE,
                        SUBSCRIBER_STATE_ON_NEXT)) {
                    try {
                        groupSinkTarget.onError(cause);
                    } catch (Throwable onErrorError) {
                        LOGGER.error("Subscriber {} threw from onError for exception {}", groupSinkTarget, cause,
                                onErrorError);
                    } finally {
                        if (!subscriberLockAcquired) {
                            subscriberState = SUBSCRIBER_STATE_IDLE;
                            // No need to drain the queue because we are in the Subscriber's thread and nothing else
                            // will be queued because the Subscriber is the only producer for the queue.
                        }
                    }
                }
            } else {
                groupSinkQueue.addTerminal(TerminalNotification.error(cause));
                drainPendingFromSource(groupSinkQueue);
            }
        }

        /**
         * Send {@code next} as {@link Subscriber#onNext(Object)}.
         * @param next Next item to emit.
         */
        public final void onNext(@Nullable T next) {
            if (terminatedPrematurely) {
                return;
            }

            final Subscriber<? super T> target = this.target;
            SpscQueue<T> subscriberQueue = this.subscriberQueue;
            if (target == null) {
                // There is no target to deliver the data to yet, we must queue.
                if (subscriberQueue == null) {
                    this.subscriberQueue = subscriberQueue = new SpscQueue<>(maxQueueSize);
                }
                if (!subscriberQueue.offerNext(next)) {
                    cancelSourceFromSource(false, new QueueFullException(queueIdentifier(), maxQueueSize), this.target,
                            subscriberQueue);
                }
                drainPendingFromSource(subscriberQueue);
            } else if (subscriberQueue != null && !subscriberQueue.isEmpty()) {
                // The queue is not empty. We have to go through the queue to ensure ordering is preserved.
                if (!subscriberQueue.offerNext(next)) {
                    cancelSourceFromSource(false, new QueueFullException(queueIdentifier(), maxQueueSize), this.target,
                            subscriberQueue);
                }
                drainPendingFromSource(subscriberQueue, target);
            } else if (subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_ON_NEXT)) {
                // The queue is empty, and we acquired the lock so we can try to directly deliver to target (assuming
                // there is request(n) demand).
                if (sourceEmitted != requested) {
                    try {
                        // We ignore overflow here because once we get to this extreme, we won't be able to account for
                        // more data anyways.
                        sourceEmittedUpdater.incrementAndGet(this);
                        target.onNext(next);
                        // This may deliver more data. It must be called after data is delivered to ensure ordering is
                        // preserved. Re-entry is OK because the lock will fail to be acquired, and the item will be
                        // added to the queue and processed below.
                        updateRequestN();
                    } catch (Throwable cause) {
                        cancelSourceFromSource(true, new IllegalStateException(
                                        "Unexpected exception thrown from onNext for identifier " + queueIdentifier(),
                                        cause),
                                target, this.subscriberQueue);
                    } finally {
                        this.subscriberState = SUBSCRIBER_STATE_IDLE;
                    }
                    // It is possible that re-entry condition resulted in creating a queue and appending data, so we
                    // should re-read the subscriberQueue member variable just in case.
                    if (subscriberQueue == null) {
                        subscriberQueue = this.subscriberQueue;
                    }
                } else {
                    this.subscriberState = SUBSCRIBER_STATE_IDLE;
                    if (subscriberQueue == null) {
                        this.subscriberQueue = subscriberQueue = new SpscQueue<>(maxQueueSize);
                    }
                    if (!subscriberQueue.offerNext(next)) {
                        cancelSourceFromSource(true, new QueueFullException(queueIdentifier(), maxQueueSize),
                                this.target, subscriberQueue);
                    }
                }
                if (subscriberQueue != null && !subscriberQueue.isEmpty()) {
                    // After we release the lock we have to try to drain from the queue in case there was any additional
                    // request(n) calls, or additional data was added in a re-entry fashion.
                    drainPendingFromSource(subscriberQueue, target);
                }
            } else {
                // If we failed to acquired the lock there is concurrency with request(n) and we have to go through the
                // queue.
                if (subscriberQueue == null) {
                    this.subscriberQueue = subscriberQueue = new SpscQueue<>(maxQueueSize);
                }
                if (!subscriberQueue.offerNext(next)) {
                    cancelSourceFromSource(false, new QueueFullException(queueIdentifier(), maxQueueSize), this.target,
                            subscriberQueue);
                }
                drainPendingFromSource(subscriberQueue, target);
            }
        }

        public final void onError(Throwable cause) {
            terminateFromSource(TerminalNotification.error(cause));
        }

        public final void onComplete() {
            terminateFromSource(TerminalNotification.complete());
        }

        private void terminateFromSource(TerminalNotification terminalNotification) {
            if (terminatedPrematurely) {
                return;
            }
            final Subscriber<? super T> target = this.target;
            SpscQueue<T> subscriberQueue = this.subscriberQueue;
            if (target == null) {
                // There is no target to deliver the data to yet, we must queue.
                if (subscriberQueue == null) {
                    this.subscriberQueue = subscriberQueue = new SpscQueue<>(maxQueueSize);
                }
                subscriberQueue.addTerminal(terminalNotification);
                drainPendingFromSource(subscriberQueue);
            } else if (subscriberQueue != null && !subscriberQueue.isEmpty()) {
                // The queue is not empty. We have to go through the queue to ensure ordering is preserved.
                subscriberQueue.addTerminal(terminalNotification);
                drainPendingFromSource(subscriberQueue);
            } else if (subscriberStateUpdater.compareAndSet(this, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_ON_NEXT)) {
                // If there is no queue then we can not be emitting as we only create a queue from on* methods which are
                // never concurrent.
                try {
                    terminalNotification.terminate(target);
                } catch (Throwable onErrorError) {
                    LOGGER.error("Subscriber {} threw for terminal {}", target, terminalNotification, onErrorError);
                }
            } else {
                // If we failed to acquired the lock there is concurrency with request(n) and we have to go through the
                // queue.
                if (subscriberQueue == null) {
                    this.subscriberQueue = subscriberQueue = new SpscQueue<>(maxQueueSize);
                }
                subscriberQueue.addTerminal(terminalNotification);
                drainPendingFromSource(subscriberQueue, target);
            }
        }

        /**
         * Update the requestN count after {@code drainedCount} have been delivered to the {@link #target}.
         */
        private void updateRequestN() {
            int actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                    sourceEmittedUpdater, maxQueueSize, this);
            if (actualSourceRequestN > 0) {
                requestFromSource(actualSourceRequestN);
            }
        }

        final long sourceRequested() {
            return sourceRequested;
        }

        abstract String queueIdentifier();

        abstract void requestFromSource(int requestN);

        abstract void handleInvalidRequestN(long n);

        abstract void cancelSourceFromExternal(Throwable cause);

        abstract void cancelSourceFromSource(boolean subscriberLockAcquired, Throwable cause);

        @Override
        public void request(long n) {
            if (!isRequestNValid(n)) {
                handleInvalidRequestN(n);
                return;
            }
            requestedUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtection);

            // We have to load the queue variable after we increment the request count in case the queue becomes
            // non-null after we increment the request count and we need to drain the queue.
            final SpscQueue<T> subscriberQueue = this.subscriberQueue;
            final Subscriber<? super T> target;
            if (subscriberQueue != null && (target = this.target) != null) {
                drainPendingFromExternal(subscriberQueue, target);
            } else {
                updateRequestN();
            }
        }
    }

    static final class SpscQueue<T> {
        private static final AtomicIntegerFieldUpdater<SpscQueue> sizeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(SpscQueue.class, "size");

        private final int maxCapacity;
        private final Queue<Object> unboundedSpsc;
        @SuppressWarnings("unused")
        private volatile int size;

        SpscQueue(int maxCapacity) {
            this.maxCapacity = maxCapacity;
            unboundedSpsc = newUnboundedSpscQueue(2);
        }

        boolean offerNext(@Nullable T item) {
            for (;;) {
                int currentSize = size;
                if (currentSize == maxCapacity) {
                    return false;
                }
                if (sizeUpdater.compareAndSet(this, currentSize, currentSize + 1)) {
                    break;
                }
            }
            unboundedSpsc.offer(wrapNull(item));
            return true;
        }

        void addTerminal(TerminalNotification terminalNotification) {
            //always allow terminal event addition, which in theory should be a single event.
            unboundedSpsc.offer(terminalNotification);
        }

        void decrementSize() {
            sizeUpdater.decrementAndGet(this);
        }

        boolean isEmpty() {
            return unboundedSpsc.isEmpty();
        }
    }

    /**
     * Drains the {@code toDrain} {@link Queue} to the {@code target} {@link Subscriber}.
     * <p>
     * This will only drain as many items found in the queue to match {@link Subscriber}s demand as reflected by
     * {@code requestedUpdater}.
     * If a {@link TerminalNotification} is found in the queue (while draining), draining is terminated.
     * This also means that if we have satisfied {@link Subscriber}s demand ({@code requestedUpdater} set to {@code 0})
     * and the next item available in the {@link Queue} is a {@link TerminalNotification},
     * we will terminate the {@link Subscriber}, without waiting for more demand.
     * <p>
     * <b>Any error thrown from {@link Subscriber#onNext(Object)} will call {@code nonTerminalErrorConsumer} and
     * terminate the drain loop.</b>
     *
     * @param toDrain {@link SpscQueue} that could contain objects (of type {@link T}) or an optional
     * {@link TerminalNotification}. Any objects other than {@link TerminalNotification} are sent as
     * {@link Subscriber#onNext(Object)}.
     * @param target {@link Subscriber} that receives {@link Subscriber#onNext(Object)} signal for each object other
     * than {@link TerminalNotification} found in the queue. It receives at most one {@link TerminalNotification} if
     * found in the queue.
     * @param subscriberStateUpdater An {@link AtomicIntegerFieldUpdater} for updating {@link Subscriber} state.
     * @param requestedSupplier Provides the current outstanding demand from the {@link Subscriber}.
     * @param terminalConsumer A {@link Consumer} that is called while the lock is acquired and a
     * {@link TerminalNotification} is popped from the queue. Invoked after {@code target} has been terminated.
     * @param nonTerminalErrorConsumer Called when an exception is thrown from methods other than
     * {@link Subscriber#onError(Throwable)} or {@link Subscriber#onComplete()}. Will be invoked before the consuming
     * lock is released and the return value is the state of the lock.
     * @param onNextCountConsumer The number of times {@link Subscriber#onNext(Object)} is called. This may be invoked
     * multiple times because this method has a loop for concurrency reasons. The value will always be {@code >0}.
     * @param flagOwner Holding instance for {@code requestedUpdater}.
     * @param <T> Type of items stored in the {@link Queue}.
     * @param <R> Type of the object holding {@code subscriberStateUpdater} and {@code requestedUpdater}.
     *
     * @return the total number of elements that were delivered to {@link Subscriber#onNext target}. The negative value
     * will be returned if a terminal event was delivered.
     */
    static <T, R> long drainToSubscriber(final SpscQueue<T> toDrain, final Subscriber<? super T> target,
                                         final AtomicIntegerFieldUpdater<R> subscriberStateUpdater,
                                         final LongSupplier requestedSupplier,
                                         final Consumer<TerminalNotification> terminalConsumer,
                                         final Consumer<Throwable> nonTerminalErrorConsumer,
                                         final IntConsumer onNextCountConsumer,
                                         final R flagOwner) {
        long totalDrainCount = 0;
        long requestCount = requestedSupplier.getAsLong();
        do {
            if (!subscriberStateUpdater.compareAndSet(flagOwner, SUBSCRIBER_STATE_IDLE, SUBSCRIBER_STATE_ON_NEXT)) {
                break;
            }
            int i = 0;
            try {
                while (i < requestCount) {
                    final Object next = toDrain.unboundedSpsc.poll();
                    if (next == null) {
                        // Items can be added to the queue after we polled, so release-lock, acquire and run the loop
                        // again.
                        break;
                    }

                    if (next instanceof TerminalNotification) {
                        TerminalNotification terminalNotification = (TerminalNotification) next;
                        try {
                            terminalNotification.terminate(target);
                        } catch (Throwable throwable) {
                            LOGGER.error("Error from terminal callbacks to subscriber {}", target, throwable);
                        } finally {
                            terminalConsumer.accept(terminalNotification);
                        }
                        totalDrainCount += i;
                        return -totalDrainCount;
                    }

                    ++i;
                    // We should always keep the queue count accurate because otherwise if there is re-entry we may
                    // throw an exception due to the queue being full, but there should be at least 1 space remaining.
                    toDrain.decrementSize();
                    try {
                        target.onNext(unwrapNullUnchecked(next));
                    } catch (Throwable cause) {
                        nonTerminalErrorConsumer.accept(cause);
                        break;
                    }
                }

                totalDrainCount += i;
                if (toDrain.unboundedSpsc.peek() instanceof TerminalNotification) {
                    TerminalNotification terminalNotification = (TerminalNotification) toDrain.unboundedSpsc.poll();
                    try {
                        terminalNotification.terminate(target);
                    } catch (Throwable throwable) {
                        LOGGER.error("Error from terminal callbacks to subscriber {}", target, throwable);
                    } finally {
                        terminalConsumer.accept(terminalNotification);
                    }

                    return -totalDrainCount;
                }
            } finally {
                // We have to call the consumer before we release the lock, as otherwise another thread could enter this
                // method and see an incorrect requestN count and deliver more data than requested.
                if (i != 0) {
                    onNextCountConsumer.accept(i);
                }
                subscriberStateUpdater.set(flagOwner, SUBSCRIBER_STATE_IDLE);
            }
            // If the queue is not empty, and the request count is non-zero we should try to drain again.
        } while (!toDrain.isEmpty() && (requestCount = requestedSupplier.getAsLong()) != 0);

        return totalDrainCount;
    }
}
