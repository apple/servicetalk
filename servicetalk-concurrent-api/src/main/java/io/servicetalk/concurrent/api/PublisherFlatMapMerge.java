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

import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.SubscriberUtils;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.CompositeExceptionUtils.addPendingError;
import static io.servicetalk.concurrent.api.CompositeExceptionUtils.maxDelayedErrors;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedMpscQueue;
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
 *     for managing demand through the {@link FlatMapSubscriber#signals} queue (network buffer, serialized POJO)</li>
 *     <li>downstream demand is available before signals from mapped sources are available
 *     (e.g. over a network boundary)</li>
 * </ul>
 * Scenarios where downstream demand is provided in small/slow increments relative to the amount of signals from mapped
 * Sources, or mapped Sources are backed by in memory content are expected to incur some additional overhead for
 * managing demand through the {@link FlatMapSubscriber#signals} queue.
 *
 * @param <T> Type of original {@link Publisher}.
 * @param <R> Type of {@link Publisher} returned by the operator.
 */
final class PublisherFlatMapMerge<T, R> extends AbstractAsynchronousPublisherOperator<T, R> {
    static final int FLAT_MAP_DEFAULT_CONCURRENCY = 16;
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherFlatMapMerge.class);
    private static final int MIN_MAPPED_DEMAND = 1;
    private final Function<? super T, ? extends Publisher<? extends R>> mapper;
    private final int maxConcurrency;
    private final int maxDelayedErrors;

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError) {
        this(original, mapper, delayError, FLAT_MAP_DEFAULT_CONCURRENCY);
    }

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError, int maxConcurrency) {
        this(original, mapper, maxDelayedErrors(delayError), maxConcurrency);
    }

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          int maxDelayedErrors, int maxConcurrency) {
        super(original);
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency: " + maxConcurrency + " (expected >0)");
        }
        if (maxDelayedErrors < 0) {
            throw new IllegalArgumentException("maxDelayedErrors: " + maxDelayedErrors + " (expected >=0)");
        }
        this.mapper = requireNonNull(mapper);
        this.maxConcurrency = maxConcurrency;
        this.maxDelayedErrors = maxDelayedErrors;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super R> subscriber) {
        return new FlatMapSubscriber<>(this, subscriber);
    }

    private static final class FlatMapSubscriber<T, R> implements Subscriber<T>, Subscription {
        private static final Object MAPPED_SOURCE_COMPLETE = new Object();
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, Throwable> pendingErrorUpdater =
                newUpdater(FlatMapSubscriber.class, Throwable.class, "pendingError");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> pendingErrorCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "pendingErrorCount");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> emittingLockUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "emittingLock");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> mappedDemandUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "mappedDemand");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> pendingDemandUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "pendingDemand");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> activeMappedSourcesUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "activeMappedSources");
        @Nullable
        private volatile Throwable pendingError;
        @SuppressWarnings("UnusedDeclaration")
        private volatile int pendingErrorCount;
        @SuppressWarnings("UnusedDeclaration")
        private volatile int emittingLock;
        private volatile int activeMappedSources;
        private volatile long pendingDemand;
        private volatile long mappedDemand;
        // protected by emitting lock, or only accessed inside the Subscriber thread
        private boolean targetTerminated;
        /**
         * We optimistically request data from each mapped publisher to ensure progress is made. If there is illegal
         * usage of the subscription (e.g. request {@code <= 0}) we may not deliver the error data is queued (until
         * sufficient demand arrives, which may not happen). This state skips the queue if demand has been exhausted.
         */
        @Nullable
        private Throwable upstreamError;
        @Nullable
        private Subscription subscription;
        private final Subscriber<? super R> target;
        private final Queue<Object> signals;
        private final PublisherFlatMapMerge<T, R> source;
        private final CancellableSet cancellableSet;

        FlatMapSubscriber(PublisherFlatMapMerge<T, R> source, Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            signals = newUnboundedMpscQueue(4);
            cancellableSet = new CancellableSet(min(16, source.maxConcurrency));
        }

        @Override
        public void cancel() {
            // invalidate pendingDemand as a best effort to avoid further signal delivery.
            pendingDemand = -1;
            doCancel(true);
        }

        @Override
        public void request(final long n) {
            assert subscription != null;
            if (isRequestNValid(n)) {
                // If we transitioned from no demand, to some demand, then we should try to drain the queues which
                // may have signals pending due to previous over-request.
                if (pendingDemandUpdater.getAndAccumulate(this, n,
                        FlowControlUtils::addWithOverflowProtectionIfNotNegative) == 0) {
                    drainPending();
                }
                incMappedDemand(n);
            } else {
                subscription.request(n);
                // If the upstream source has already sent an onComplete signal, it won't be able to send an error.
                // We propagate invalid demand upstream to clean-up upstream (if necessary) and force an error here to
                // ensure we see an error.
                enqueueAndDrain(error(newExceptionForInvalidRequestN(n)));
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
            final Publisher<? extends R> publisher = requireNonNull(source.mapper.apply(t),
                    () -> "Mapper " + source.mapper + " returned null");
            for (;;) {
                final int currValue = this.activeMappedSources;
                if (currValue < 0) {
                    throw new IllegalStateException("onNext(" + t + ") after terminal signal delivered to " + this);
                } else if (currValue == Integer.MAX_VALUE) {
                    // This shouldn't happen as maxConcurrency upstream is an integer.
                    throw new IllegalStateException("Overflow of mapped Publishers for " + this);
                } else if (activeMappedSourcesUpdater.compareAndSet(this, currValue, currValue + 1)) {
                    publisher.subscribeInternal(new FlatMapPublisherSubscriber<>(this));
                    break;
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            upstreamError = t;
            try {
                doCancel(false);
            } finally {
                enqueueAndDrain(error(t));
            }
        }

        @Override
        public void onComplete() {
            if (terminateActiveMappedSources()) {
                // delayedError is checked in drain loop, and complete() is discarded if there are errors pending.
                enqueueAndDrain(complete());
            }
        }

        private void incMappedDemand(long n) {
            assert n > 0;
            mappedDemandUpdater.getAndAccumulate(this, n, FlowControlUtils::addWithUnderOverflowProtection);
        }

        private int reserveMappedDemandQuota() {
            for (;;) {
                final long prevDemand = mappedDemand;
                if (prevDemand <= 0) {
                    // mappedDemand is allowed to go negative here in order to distribute MIN_MAPPED_DEMAND demand to
                    // each source. This is to avoid a single mapped source (or set of sources) not making any progress
                    // with the demand they were given, taking demand away from sources that could make progress. The
                    // negative value ensures that if a source completes with unused demand it doesn't result in
                    // artificially giving back "negative" demand and keeps the maximum queue size should be bound
                    // to (maxConcurrency * minMappedDemand).
                    if (mappedDemandUpdater.compareAndSet(this, prevDemand, prevDemand - MIN_MAPPED_DEMAND)) {
                        return MIN_MAPPED_DEMAND;
                    }
                } else {
                    final int quota = calculateRequestNQuota(prevDemand);
                    if (mappedDemandUpdater.compareAndSet(this, prevDemand, prevDemand - quota)) {
                        return quota;
                    }
                }
            }
        }

        private void distributeMappedDemand(FlatMapPublisherSubscriber<T, R> hungrySubscriber) {
            final int quota = reserveMappedDemandQuota();
            if (!hungrySubscriber.request(quota)) {
                incMappedDemand(quota);
            }
        }

        private int calculateRequestNQuota(long availableRequestN) {
            // Get an approximate quota to distribute to each active mapped subscriber.
            return (int) min(Integer.MAX_VALUE, max(availableRequestN / source.maxConcurrency, MIN_MAPPED_DEMAND));
        }

        private void doCancel(boolean cancelUpstream) {
            try {
                if (cancelUpstream) {
                    assert subscription != null;
                    subscription.cancel();
                }
            } finally {
                cancellableSet.cancel();
                // Don't bother clearing out signals (which require additional concurrency control) because it is
                // assumed this Subscriber will be dereferenced and eligible for GC [1].
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

        private void tryEmitItem(Object item, final boolean needsDemand, FlatMapPublisherSubscriber<T, R> subscriber) {
            // We can skip the queue if the following conditions are meet:
            // 1. There is downstream requestN demand.
            // 2. The mapped subscriber doesn't have any signals already in the queue. We only need to preserve the
            //    ordering for each mapped source, and there is no "overall" ordering.
            // 3. We don't concurrently invoke the downstream subscriber. Concurrency control is provided by the
            //    emitting lock.
            if (subscriber.hasSignalsQueued() || (needsDemand && !tryDecrementPendingDemand())) {
                subscriber.markSignalsQueued();
                enqueueAndDrain(item);
            } else if (item == MAPPED_SOURCE_COMPLETE) {
                try {
                    requestMoreFromUpstream(1);
                } catch (Throwable cause) {
                    onErrorNotHoldingLock(cause);
                }
            } else if (tryAcquireLock(emittingLockUpdater, this)) { // fast path. no concurrency, try to skip the queue.
                try {
                    final boolean demandConsumed = sendToTarget(item);
                    assert demandConsumed == needsDemand || targetTerminated;
                } finally {
                    // No need to catch exception and onErrorHoldingLock. If the signal is not a terminal it's safe to
                    // propagate, if the signal is a terminal then we have already delivered a terminal down stream and
                    // can't do anything else (just let the upstream handle it).
                    if (!releaseLock(emittingLockUpdater, this)) {
                        drainPending();
                    }
                }
            } else { // slow path. there is concurrency, go through the queue to avoid concurrent delivery.
                if (needsDemand) { // give the demand back that we previously reserved
                    pendingDemandUpdater.getAndAccumulate(this, 1,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                }
                subscriber.markSignalsQueued();
                enqueueAndDrain(item);
            }
        }

        /**
         * Process an error while holding the {@link #emittingLock}. We cannot re-throw here because sources have
         * already terminated. This means the source cannot deliver another terminal or else it will violate the
         * reactive stream spec. The best we can do is cancel upstream and mapped subscribers, and propagate the error
         * downstream.
         * @param cause The cause that occurred while delivering signals down stream.
         */
        private void onErrorHoldingLock(Throwable cause) {
            try {
                doCancel(true);
            } finally {
                sendToTarget(error(cause));
            }
        }

        private void onErrorNotHoldingLock(Throwable cause) {
            if (tryAcquireLock(emittingLockUpdater, this)) { // fast path. no concurrency, try to skip the queue.
                onErrorHoldingLock(cause);
            } else {
                try {
                    doCancel(true);
                } finally {
                    // Emit the error to preserve ordering relative to onNext signals for this source.
                    enqueueAndDrain(error(cause));
                }
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

        private static void enqueueFailed(Object item) {
            LOGGER.error("Queue should be unbounded, but an offer failed for item {}!", item);
            // Note that we throw even if the item represents a terminal signal (even though we don't expect another
            // terminal signal to be delivered from the upstream source because we are already terminated). If we fail
            // to enqueue a terminal event async control flow won't be completed and the user won't be notified. This
            // is a relatively extreme failure condition and we fail loudly to clarify that signal delivery is
            // interrupted and the user may experience hangs.
            throw new QueueFullException("pending");
        }

        private void drainPending() {
            boolean tryAcquire = true;
            while (tryAcquire && tryAcquireLock(emittingLockUpdater, this)) {
                try {
                    int mappedSourcesCompleted = 0;
                    final long prevDemand = pendingDemandUpdater.getAndSet(this, 0);
                    if (prevDemand < 0) {
                        pendingDemand = prevDemand;
                    } else {
                        long emittedCount = 0;
                        Object t;
                        while (emittedCount < prevDemand && (t = signals.poll()) != null) {
                            if (t == MAPPED_SOURCE_COMPLETE) {
                                ++mappedSourcesCompleted;
                            } else if (sendToTarget(t)) {
                                ++emittedCount;
                            }
                        }

                        // check if a terminal event is pending, or give back demand.
                        if (emittedCount == prevDemand) {
                            for (;;) {
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
                            }

                            if (t instanceof TerminalNotification) {
                                sendToTarget(t);
                            } else {
                                sendToTargetIfPrematureError();
                            }
                        } else {
                            assert emittedCount < prevDemand;
                            pendingDemandUpdater.accumulateAndGet(this, prevDemand - emittedCount,
                                    FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                        }
                    }

                    if (mappedSourcesCompleted != 0) {
                        requestMoreFromUpstream(mappedSourcesCompleted);
                    }
                } catch (Throwable cause) {
                    // We can't propagate an exception if the subscriber triggering this invocation has terminated, but
                    // we don't know in which context this method is being invoked and if the subscriber invoking this
                    // method may have a terminal event queued. Also since throwing is in violation of the spec we keep
                    // the code simple and handle the exception here. This method may also be invoked from a request(..)
                    // in which we can't let the exception propagate for graceful termination.
                    onErrorHoldingLock(cause);
                    return; // Poison emittingUpdater. We prematurely terminated, other signals should be ignored.
                }
                // Release lock after we handle errors, because error handling needs to poison the lock.
                tryAcquire = !releaseLock(emittingLockUpdater, this);
            }
        }

        private void requestMoreFromUpstream(int mappedSourcesCompleted) {
            assert mappedSourcesCompleted > 0;
            assert subscription != null;
            subscription.request(mappedSourcesCompleted);
        }

        private boolean sendToTarget(Object item) {
            assert item != MAPPED_SOURCE_COMPLETE;
            if (targetTerminated) {
                return false;
            } else if (item instanceof TerminalNotification) {
                signals.clear();
                targetTerminated = true;
                final Throwable currPendingError = pendingError;
                if (currPendingError != null) {
                    target.onError(currPendingError);
                } else {
                    ((TerminalNotification) item).terminate(target);
                }
                return false;
            } else if (item instanceof FlatMapPublisherSubscriber) {
                @SuppressWarnings("unchecked")
                final FlatMapPublisherSubscriber<T, R> hungrySubscriber = (FlatMapPublisherSubscriber<T, R>) item;
                distributeMappedDemand(hungrySubscriber);
                return false;
            }
            target.onNext(unwrapNullUnchecked(item));
            return true;
        }

        private void sendToTargetIfPrematureError() {
            // Don't wait for demand to deliver the terminalNotification if present. The queued signals maybe from
            // optimistic demand, but the error is from an event that needs immediate propagation (e.g. illegal
            // requestN, failure to enqueue).
            if (upstreamError != null && !targetTerminated) {
                signals.clear();
                targetTerminated = true;
                target.onError(upstreamError);
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
            assert subscriber.subscription != null;
            if (cancellableSet.remove(subscriber.subscription) && decrementActiveMappedSources()) {
                return true;
            } else if (unusedDemand > 0) {
                incMappedDemand(unusedDemand);
            }
            return false;
        }

        private static final class FlatMapPublisherSubscriber<T, R> implements Subscriber<R> {
            @SuppressWarnings("rawtypes")
            private static final AtomicIntegerFieldUpdater<FlatMapPublisherSubscriber> pendingDemandUpdater =
                    AtomicIntegerFieldUpdater.newUpdater(FlatMapPublisherSubscriber.class, "innerPendingDemand");
            /**
             * This value must be greater than {@link Integer#MIN_VALUE} and less than {@code -1} to allow for handling
             * invalid demand, and onNext after terminal signals.
             */
            private static final int TERMINATED = -2;
            private final FlatMapSubscriber<T, R> parent;
            private volatile int innerPendingDemand;
            @Nullable
            private Subscription subscription;
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
            }

            boolean request(int n) {
                assert n > 0;
                assert subscription != null;
                // Write to signalsQueued BEFORE pendingDemand so this value is guaranteed to be visible in Subscriber
                // methods (onNext). There should be an implicit "happens before" because we need to request upstream
                // in order to get signals, but this way we don't have to rely upon external factors.
                signalsQueued = false;
                if (!pendingDemandUpdater.compareAndSet(this, 0, n)) {
                    return false;
                }
                subscription.request(n);
                return true;
            }

            void markSignalsQueued() {
                signalsQueued = true;
            }

            boolean hasSignalsQueued() {
                return signalsQueued;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                subscription = ConcurrentSubscription.wrap(s);
                // RequestN management for mapped sources is "approximate" as it is divided between mapped sources. More
                // demand may be distributed than is requested from downstream in order to avoid deadlock scenarios.
                // To accommodate for the "approximate" mapped demand we maintain a signal queue (bounded by the
                // concurrency). This presents an opportunity to decouple downstream requestN requests from iterating
                // all active mapped sources and instead optimistically give out demand here and replenish demand after
                // signals are delivered to the downstream subscriber (based upon available demand).
                if (parent.cancellableSet.add(subscription)) {
                    parent.distributeMappedDemand(this);
                }
            }

            @Override
            public void onNext(@Nullable final R r) {
                // Write to pendingDemand BEFORE calling tryEmitItem because signalsQueued will be read by tryEmitItem
                // and we want the value written to signalsQueued in request(..) to be visible.
                final int pendingDemand = pendingDemandUpdater.decrementAndGet(this);
                if (pendingDemand < 0) {
                    handleInvalidDemand(pendingDemand, r);
                }
                try {
                    parent.tryEmitItem(wrapNull(r), true, this);
                } finally {
                    if (pendingDemand == 0) {
                        // Emit this item to signify this Subscriber is hungry for more demand when it is available.
                        parent.tryEmitItem(this, false, this);
                    }
                }
            }

            private void handleInvalidDemand(int pendingDemand, @Nullable final R r) {
                // Reset pendingDemand because we want to allow for a terminal event to be propagated. This is safe
                // because request(..) won't be called until demand is exhausted. If the atomic operation fails
                // either there is concurrency on the Subscriber (violating reactive streams spec) or request(..) has
                // been called on another thread (demand was still violated, we drain demand to 0 before requesting).
                pendingDemandUpdater.compareAndSet(this, pendingDemand, (pendingDemand > TERMINATED) ? 0 : TERMINATED);
                throw new IllegalStateException("Too many onNext signals for Subscriber: " + this +
                        " pendingDemand: " + pendingDemand + " discarding: " + r);
            }

            @Override
            public void onError(final Throwable t) {
                final int unusedDemand = pendingDemandUpdater.getAndSet(this, TERMINATED);
                if (unusedDemand < 0) {
                    SubscriberUtils.logDuplicateTerminal(this, t);
                    return;
                }
                Throwable currPendingError = parent.pendingError;
                if (parent.source.maxDelayedErrors == 0) {
                    if (currPendingError == null && pendingErrorUpdater.compareAndSet(parent, null, t)) {
                        try {
                            parent.doCancel(true);
                        } finally {
                            // Emit the error to preserve ordering relative to onNext signals for this source.
                            parent.tryEmitItem(error(t), false, this);
                        }
                    }
                } else {
                    if (currPendingError == null) {
                        if (pendingErrorUpdater.compareAndSet(parent, null, t)) {
                            currPendingError = t;
                        } else {
                            currPendingError = parent.pendingError;
                            assert currPendingError != null;
                            addPendingError(pendingErrorCountUpdater, parent,
                                    parent.source.maxDelayedErrors, currPendingError, t);
                        }
                    } else {
                        addPendingError(pendingErrorCountUpdater, parent,
                                parent.source.maxDelayedErrors, currPendingError, t);
                    }
                    if (parent.removeSubscriber(this, unusedDemand)) {
                        parent.enqueueAndDrain(error(currPendingError));
                    } else {
                        parent.tryEmitItem(MAPPED_SOURCE_COMPLETE, false, this);
                    }
                }
            }

            @Override
            public void onComplete() {
                final int unusedDemand = pendingDemandUpdater.getAndSet(this, TERMINATED);
                if (unusedDemand < 0) {
                    SubscriberUtils.logDuplicateTerminal(this);
                } else if (parent.removeSubscriber(this, unusedDemand)) {
                    parent.enqueueAndDrain(complete());
                } else {
                    parent.tryEmitItem(MAPPED_SOURCE_COMPLETE, false, this);
                }
            }
        }
    }
}
