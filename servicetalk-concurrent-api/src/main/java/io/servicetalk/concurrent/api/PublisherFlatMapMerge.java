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
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.trySetTerminal;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedMpscQueue;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * Implementation of {@link Publisher#flatMapMerge(Function)}.
 * <p>
 * This implementation makes some trade-offs that may have performance/memory impacts. Demand distributed to mapped
 * {@link Publisher}s is dynamic and correlates to downstream demand. This dynamic behavior is targeted toward use cases
 * where:
 * <ul>
 *     <li>{@link Subscriber#onNext(Object) onNext Objects} consume non trivial amount of memory relative to the memory
 *     to manage the {@link FlatMapSubscriber#hungrySubscribers} queue (e.g. network buffer, serialized POJO)</li>
 *     <li>downstream demand is available before signals from mapped sources are available
 *     (e.g. over a network boundary)</li>
 * </ul>
 * Scenarios where downstream demand is provided in small/slow increments relative to the amount of signals from mapped
 * Sources, or mapped Sources are backed by in memory content are expected to incur some additional overhead for
 * {@link FlatMapSubscriber#hungrySubscribers queue} management.
 *
 * @param <T> Type of original {@link Publisher}.
 * @param <R> Type of {@link Publisher} returned by the operator.
 */
final class PublisherFlatMapMerge<T, R> extends AbstractAsynchronousPublisherOperator<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherFlatMapMerge.class);
    private final Function<? super T, ? extends Publisher<? extends R>> mapper;
    private final int maxConcurrency;
    private final int minMappedDemand;
    private final boolean delayError;

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError, Executor executor) {
        this(original, mapper, delayError, 8, 8, executor);
    }

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError, int maxConcurrency, int minMappedDemand, Executor executor) {
        super(original, executor);
        this.mapper = requireNonNull(mapper);
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency: " + maxConcurrency + " (expected >0)");
        }
        if (minMappedDemand <= 0) {
            throw new IllegalArgumentException("minMappedDemand: " + minMappedDemand + " (expected >0)");
        }
        this.minMappedDemand = minMappedDemand;
        this.maxConcurrency = maxConcurrency;
        this.delayError = delayError;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super R> subscriber) {
        return new FlatMapSubscriber<>(this, subscriber);
    }

    private static final class FlatMapSubscriber<T, R> implements Subscriber<T>, Subscription {
        private static final Object MAPPED_SOURCE_COMPLETE = new Object();
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, CompositeException> delayedErrorUpdater =
                newUpdater(FlatMapSubscriber.class, CompositeException.class, "delayedError");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> emittingLockUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "emittingLock");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> requestingLockUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "requestingLock");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> mappedDemandUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "mappedDemand");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> pendingDemandUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "pendingDemand");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> activeMappedSourcesUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "activeMappedSources");
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, TerminalNotification>
                terminalNotificationUpdater = newUpdater(FlatMapSubscriber.class, TerminalNotification.class,
                "terminalNotification");

        @SuppressWarnings("unused")
        @Nullable
        private volatile TerminalNotification terminalNotification;
        @Nullable
        private volatile CompositeException delayedError;
        @SuppressWarnings("unused")
        private volatile int emittingLock;
        private volatile int activeMappedSources;
        @SuppressWarnings("unused")
        private volatile int requestingLock;
        private volatile long pendingDemand;
        private volatile long mappedDemand;

        // protected by emitting lock, or only accessed inside the Subscriber thread
        private boolean targetTerminated;
        @Nullable
        private Subscription subscription;

        private final Subscriber<? super R> target;
        private final Queue<Object> signals;
        private final PublisherFlatMapMerge<T, R> source;
        private final DynamicCompositeCancellable cancellableSubscribers;
        private final Queue<FlatMapPublisherSubscriber<T, R>> hungrySubscribers;

        FlatMapSubscriber(PublisherFlatMapMerge<T, R> source, Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            signals = newUnboundedMpscQueue(4);
            hungrySubscribers = newUnboundedMpscQueue(4);
            cancellableSubscribers = new SetDynamicCompositeCancellable(min(16, source.maxConcurrency));
        }

        @Override
        public void cancel() {
            doCancel(true);
        }

        @Override
        public void request(final long n) {
            assert subscription != null;
            if (!isRequestNValid(n)) {
                subscription.request(n);
            } else {
                // If we transitioned from no demand, to some demand, then we should try to drain the queues which
                // may have signals pending due to previous over-request.
                if (pendingDemandUpdater.getAndAccumulate(this, n,
                        FlowControlUtils::addWithOverflowProtectionIfNotNegative) == 0) {
                    drainPending();
                }
                if (mappedDemandUpdater.getAndAccumulate(this, n,
                        FlowControlUtils::addWithOverflowProtection) == 0) {
                    distributeMappedDemand();
                }
            }
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (!checkDuplicateSubscription(subscription, s)) {
                return;
            }

            // We assume that FlatMapSubscriber#cancel() will never be called before this method, and therefore we
            // don't have to worry about being cancelled before the onSubscribe method is called.
            subscription = ConcurrentSubscription.wrap(s);
            target.onSubscribe(this);

            subscription.request(source.maxConcurrency);
        }

        @Override
        public void onNext(@Nullable final T t) {
            final Publisher<? extends R> publisher = requireNonNull(source.mapper.apply(t));
            FlatMapPublisherSubscriber<T, R> subscriber = new FlatMapPublisherSubscriber<>(this);
            if (!cancellableSubscribers.add(subscriber)) {
                return;
            }
            for (;;) {
                final int prevActiveSources = activeMappedSources;
                if (prevActiveSources < 0) {
                    // We have been cancelled, or already completed and the active count flipped to negative, either
                    // way we don't want to Subscribe or retain a reference to this Publisher.
                    cancellableSubscribers.remove(subscriber);
                    break;
                } else if (activeMappedSourcesUpdater.compareAndSet(this, prevActiveSources, prevActiveSources + 1)) {
                    publisher.subscribeInternal(subscriber);
                    break;
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!onError0(t, false, false)) {
                LOGGER.debug("Already terminated/cancelled, ignoring error notification.", t);
            }
        }

        @Override
        public void onComplete() {
            // Setting terminal must be done before terminateActiveMappedSources to ensure visibility of the terminal.
            final boolean setTerminal = trySetTerminal(complete(), false, terminalNotificationUpdater, this);
            final boolean allSourcesTerminated = terminateActiveMappedSources();
            if (setTerminal && allSourcesTerminated) {
                enqueueAndDrain(complete());
            }
        }

        private boolean onError0(Throwable throwable, boolean overrideComplete,
                                 boolean cancelSubscriberIfNecessary) {
            final TerminalNotification notification = TerminalNotification.error(throwable);
            if (trySetTerminal(notification, overrideComplete, terminalNotificationUpdater, this)) {
                try {
                    doCancel(cancelSubscriberIfNecessary);
                } finally {
                    enqueueAndDrain(notification);
                }
                return true;
            }
            return false;
        }

        private long reserveMappedDemand() {
            return mappedDemandUpdater.getAndSet(this, 0);
        }

        private int reserveMappedDemandQuota() {
            for (;;) {
                final long prevDemand = mappedDemand;
                if (prevDemand <= 0) {
                    return 0;
                }
                final int quota = calculateRequestNQuota(prevDemand);
                if (mappedDemandUpdater.compareAndSet(this, prevDemand, prevDemand - quota)) {
                    return quota;
                }
            }
        }

        private void distributeMappedDemand(FlatMapPublisherSubscriber<T, R> hungrySubscriber) {
            final int quota = reserveMappedDemandQuota();
            if (quota > 0) {
                final int usedQuota = hungrySubscriber.request(quota);
                if (usedQuota < quota && mappedDemandUpdater.getAndAccumulate(this, quota - usedQuota,
                        FlowControlUtils::addWithOverflowProtection) == 0) {
                    // If we gave some back and transitioned from 0 demand, we need to try to distribute demand
                    // in case other hungry subscribers were added in the mean time
                    // (since we have not acquired the requestingLock).
                    distributeMappedDemand();
                }
            } else { // slow path. no demand, add to queue and process later when demand arrives.
                hungrySubscribers.add(hungrySubscriber);
                distributeMappedDemand();
            }
        }

        private void distributeMappedDemand() {
            Throwable delayedCause = null;
            boolean tryAcquire = true;
            while (tryAcquire && tryAcquireLock(requestingLockUpdater, this)) {
                try {
                    final long availableRequestN = reserveMappedDemand();
                    if (availableRequestN > 0) {
                        long remainingRequestN = availableRequestN;
                        final int quota = calculateRequestNQuota(availableRequestN);
                        FlatMapPublisherSubscriber<T, R> hungrySubscriber;
                        while ((hungrySubscriber = hungrySubscribers.poll()) != null) {
                            remainingRequestN -= hungrySubscriber.request(quota);
                        }
                        if (remainingRequestN > 0) {
                            mappedDemandUpdater.accumulateAndGet(this, remainingRequestN,
                                    FlowControlUtils::addWithOverflowProtection);
                        }
                    }
                } catch (Throwable cause) {
                    delayedCause = catchUnexpected(delayedCause, cause);
                } finally {
                    tryAcquire = !releaseLock(requestingLockUpdater, this);
                }
            }
            if (delayedCause != null) {
                throwException(delayedCause);
            }
        }

        private int calculateRequestNQuota(long availableRequestN) {
            // Get an approximate quota to distribute to each active mapped subscriber. If
            // activeMappedSources changes (subscriber added or terminated) we will re-distribute.
            final int prevActiveSources = activeMappedSources;
            return (int) min(Integer.MAX_VALUE,
                    max(prevActiveSources > 0 ? availableRequestN / prevActiveSources : availableRequestN,
                            source.minMappedDemand));
        }

        private void doCancel(boolean cancelSubscription) {
            // Prevent future onNext operations from adding to subscribers which otherwise may result in
            // not cancelling mapped Subscriptions. This should be Integer.MIN_VALUE to prevent subsequent mapped
            // source completion from incrementing the count to 0 or positive as terminateActiveMappedSources flips
            // the count to negative to signal to mapped sources we have completed.
            activeMappedSources = Integer.MIN_VALUE;
            pendingDemand = -1;
            try {
                if (cancelSubscription) {
                    assert subscription != null;
                    subscription.cancel();
                }
            } finally {
                cancellableSubscribers.cancel();
                // Don't bother clearing out hungrySubscribers or signals (which require additional concurrency control)
                // because it is assumed this Subscriber will be dereferenced and eligible for GC [1].
                // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.13
            }
        }

        private boolean tryDecrementPendingDemand() {
            for (;;) {
                final long prevDemand = pendingDemand;
                if (prevDemand <= 0) {
                    return false;
                } else if (pendingDemandUpdater.compareAndSet(this, prevDemand, prevDemand - 1)) {
                    return true;
                }
            }
        }

        private void tryEmitItem(Object item, FlatMapPublisherSubscriber<T, R> subscriber) {
            if (tryAcquireLock(emittingLockUpdater, this)) { // fast path. no concurrency, try to skip the queue.
                try {
                    if (subscriber.hasSignalsQueued() || (needsDemand(item) && !tryDecrementPendingDemand())) {
                        subscriber.markSignalsQueued();
                        enqueueItem(item); // drain isn't necessary. when demand arrives a drain will be attempted.
                    } else if (item == MAPPED_SOURCE_COMPLETE) {
                        requestMoreFromUpstream(1);
                    } else {
                        final boolean demandConsumed = sendToTarget(item);
                        assert demandConsumed == needsDemand(item);
                    }
                } finally {
                    if (!releaseLock(emittingLockUpdater, this)) {
                        drainPending();
                    }
                }
            } else { // slow path. there is concurrency, go through the queue to avoid concurrent delivery.
                subscriber.markSignalsQueued();
                enqueueAndDrain(item);
            }
        }

        private void enqueueItem(Object item) {
            if (!signals.offer(item)) {
                enqueueFailed(item);
            }
        }

        private void enqueueAndDrain(Object item) {
            enqueueItem(item);
            drainPending();
        }

        private void enqueueFailed(Object item) {
            QueueFullException exception = new QueueFullException("signals");
            if (item instanceof TerminalNotification) {
                LOGGER.error("Queue should be unbounded, but an offer failed!", exception);
                throw exception;
            } else {
                onError0(exception, true, true);
            }
        }

        private void drainPending() {
            Throwable delayedCause = null;
            boolean tryAcquire = true;
            int mappedSourcesCompleted = 0;
            while (tryAcquire && tryAcquireLock(emittingLockUpdater, this)) {
                try {
                    final long prevDemand = pendingDemandUpdater.getAndSet(this, 0);
                    long emittedCount = 0;
                    if (prevDemand < 0) {
                        // if we have been cancelled then we avoid delivering any signals, but we still deliver error
                        // terminals below by making sure emittedCount == prevDemand.
                        pendingDemand = emittedCount = prevDemand;
                        signals.clear();
                    }
                    Object t;
                    while (emittedCount < prevDemand && (t = signals.poll()) != null) {
                        try {
                            if (t == MAPPED_SOURCE_COMPLETE) {
                                ++mappedSourcesCompleted;
                            } else if (sendToTarget(t)) {
                                ++emittedCount;
                            }
                        } catch (Throwable cause) {
                            delayedCause = catchUnexpected(delayedCause, cause);
                        }
                    }

                    // check if a terminal event is pending, or give back demand.
                    if (emittedCount == prevDemand) {
                        for (;;) {
                            try {
                                t = signals.peek();
                                if (t == MAPPED_SOURCE_COMPLETE) {
                                    signals.poll();
                                    ++mappedSourcesCompleted;
                                } else if (t instanceof FlatMapPublisherSubscriber) {
                                    signals.poll();
                                    @SuppressWarnings("unchecked")
                                    final FlatMapPublisherSubscriber<T, R> hungrySubscriber =
                                            (FlatMapPublisherSubscriber<T, R>) t;
                                    distributeMappedDemand(hungrySubscriber);
                                } else {
                                    break;
                                }
                            } catch (Throwable cause) {
                                delayedCause = catchUnexpected(delayedCause, cause);
                            }
                        }

                        if (t instanceof TerminalNotification) {
                            sendToTarget((TerminalNotification) t); // if this throws its OK as we have terminated
                        } else {
                            t = terminalNotification;
                            if (t != null && t != complete()) { // don't wait for demand to process an error
                                sendToTarget((TerminalNotification) t); // if this throws its OK as we have terminated
                            }
                        }
                    } else {
                        assert emittedCount < prevDemand;
                        pendingDemandUpdater.accumulateAndGet(this, prevDemand - emittedCount,
                                FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                    }
                } finally {
                    tryAcquire = !releaseLock(emittingLockUpdater, this);
                }
            }

            if (mappedSourcesCompleted != 0) {
                requestMoreFromUpstream(mappedSourcesCompleted);
            }

            if (delayedCause != null) {
                throwException(delayedCause);
            }
        }

        private void requestMoreFromUpstream(int mappedSourcesCompleted) {
            assert mappedSourcesCompleted > 0;
            assert subscription != null;
            subscription.request(mappedSourcesCompleted);
        }

        private static boolean needsDemand(Object item) {
            return item != MAPPED_SOURCE_COMPLETE &&
                    !(item instanceof FlatMapPublisherSubscriber) && !(item instanceof TerminalNotification);
        }

        private boolean sendToTarget(Object item) {
            assert item != MAPPED_SOURCE_COMPLETE;
            if (targetTerminated) {
                return false;
            } else if (item instanceof TerminalNotification) {
                // Load the terminal notification in case an error happened after an onComplete and we override the
                // terminal value.
                TerminalNotification terminalNotification = this.terminalNotification;
                assert terminalNotification != null;
                sendToTarget(terminalNotification);
                return false;
            } else if (item instanceof FlatMapPublisherSubscriber) {
                @SuppressWarnings("unchecked")
                final FlatMapPublisherSubscriber<T, R> hungrySubscriber =
                        (FlatMapPublisherSubscriber<T, R>) item;
                distributeMappedDemand(hungrySubscriber);
                return false;
            }
            target.onNext(unwrapNullUnchecked(item));
            return true;
        }

        private void sendToTarget(TerminalNotification terminalNotification) {
            signals.clear();
            targetTerminated = true;
            CompositeException de = this.delayedError;
            if (de != null) {
                de.transferPendingToSuppressed();
                if (terminalNotification.cause() == de) {
                    terminalNotification.terminate(target);
                } else {
                    terminalNotification.terminate(target, de);
                }
            } else {
                terminalNotification.terminate(target);
            }
        }

        private boolean terminateActiveMappedSources() {
            for (;;) {
                final int prevActiveSources = activeMappedSources;
                assert prevActiveSources >= 0; // otherwise we have seen multiple onComplete signals
                if (activeMappedSourcesUpdater.compareAndSet(this, prevActiveSources, -prevActiveSources)) {
                    return prevActiveSources == 0;
                }
            }
        }

        private boolean decrementActiveMappedSources() {
            for (;;) {
                final int prevActiveSources = activeMappedSources;
                assert prevActiveSources != 0;
                if (prevActiveSources > 0) {
                    if (activeMappedSourcesUpdater.compareAndSet(this, prevActiveSources, prevActiveSources - 1)) {
                        return false;
                    }
                } else if (activeMappedSourcesUpdater.compareAndSet(this, prevActiveSources, prevActiveSources + 1)) {
                    return prevActiveSources == -1;
                }
            }
        }

        private boolean removeSubscriber(final FlatMapPublisherSubscriber<T, R> subscriber, int unusedDemand) {
            if (cancellableSubscribers.remove(subscriber) && decrementActiveMappedSources()) {
                return true;
            } else if (unusedDemand > 0 && mappedDemandUpdater.getAndAccumulate(this, unusedDemand,
                            FlowControlUtils::addWithOverflowProtection) == 0) {
                distributeMappedDemand();
            }
            return false;
        }

        private static final class FlatMapPublisherSubscriber<T, R> implements Subscriber<R>, Cancellable {
            @SuppressWarnings("rawtypes")
            private static final AtomicIntegerFieldUpdater<FlatMapPublisherSubscriber> pendingDemandUpdater =
                    AtomicIntegerFieldUpdater.newUpdater(FlatMapPublisherSubscriber.class, "innerPendingDemand");

            private final FlatMapSubscriber<T, R> parent;
            private final DelayedSubscription subscription;
            private volatile int innerPendingDemand;
            /**
             * visibility provided by the {@link Subscriber} thread in {@link #onNext(Object)}, and
             * demand is exhausted before {@link #request(long)} is called, and that method triggers
             * {@link Subscription#request(long)} which provides a
             * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.1">
             * happens-before relationship between requesting elements and receiving elements</a>.
             */
            private boolean signalsQueued;

            FlatMapPublisherSubscriber(FlatMapSubscriber<T, R> parent) {
                this.parent = parent;
                subscription = new DelayedSubscription();
            }

            @Override
            public void cancel() {
                subscription.cancel();
            }

            int request(int n) {
                assert n > 0;
                if (!pendingDemandUpdater.compareAndSet(this, 0, n)) {
                    return 0;
                }
                clearSignalsQueued();
                subscription.request(n);
                return n;
            }

            void clearSignalsQueued() {
                signalsQueued = false;
            }

            void markSignalsQueued() {
                signalsQueued = true;
            }

            boolean hasSignalsQueued() {
                return signalsQueued;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                subscription.delayedSubscription(ConcurrentSubscription.wrap(s));
                parent.distributeMappedDemand(this);
            }

            @Override
            public void onNext(@Nullable final R r) {
                parent.tryEmitItem(wrapNull(r), this);
                final int pendingDemand = pendingDemandUpdater.decrementAndGet(this);
                if (pendingDemand == 0) {
                    // Emit this item to signify this Subscriber is hungry for more demand when it is available.
                    parent.tryEmitItem(this, this);
                } else if (pendingDemand < 0) {
                    throw new IllegalStateException("Too many onNext signals for Subscriber: " + this +
                            " pendingDemand: " + pendingDemand);
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (!parent.source.delayError) {
                    parent.onError0(t, true, true);
                } else {
                    CompositeException de = parent.delayedError;
                    if (de == null) {
                        de = new CompositeException(t);
                        if (!delayedErrorUpdater.compareAndSet(parent, null, de)) {
                            de = parent.delayedError;
                            assert de != null;
                            de.add(t);
                        }
                    } else {
                        de.add(t);
                    }
                    if (parent.removeSubscriber(this, pendingDemandUpdater.getAndSet(this, -1))) {
                        trySetTerminal(TerminalNotification.error(de), true, terminalNotificationUpdater, parent);

                        // Since we have already added error to delayedError, we use complete() TerminalNotification
                        // as a dummy signal to start draining and termination.
                        parent.enqueueAndDrain(complete());
                    } else {
                        parent.tryEmitItem(MAPPED_SOURCE_COMPLETE, this);
                    }
                }
            }

            @Override
            public void onComplete() {
                if (parent.removeSubscriber(this, pendingDemandUpdater.getAndSet(this, -1))) {
                    parent.enqueueAndDrain(complete());
                } else {
                    parent.tryEmitItem(MAPPED_SOURCE_COMPLETE, this);
                }
            }
        }
    }
}
