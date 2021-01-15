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

import static io.servicetalk.concurrent.api.CompositeExceptionUtils.addPendingError;
import static io.servicetalk.concurrent.api.CompositeExceptionUtils.maxDelayedErrors;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
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
                          boolean delayError, Executor executor) {
        this(original, mapper, delayError, FLAT_MAP_DEFAULT_CONCURRENCY, executor);
    }

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError, int maxConcurrency, Executor executor) {
        this(original, mapper, maxDelayedErrors(delayError), maxConcurrency, executor);
    }

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          int maxDelayedErrors, int maxConcurrency, Executor executor) {
        super(original, executor);
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
        private final CancellableSet cancellableSubscribers;

        FlatMapSubscriber(PublisherFlatMapMerge<T, R> source, Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            signals = newUnboundedMpscQueue(4);
            cancellableSubscribers = new CancellableSet(min(16, source.maxConcurrency));
        }

        @Override
        public void cancel() {
            doCancel(true, true);
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
            if (cancellableSubscribers.add(subscriber) && activeMappedSourcesUpdater.incrementAndGet(this) > 0) {
                publisher.subscribeInternal(subscriber);
            }
            // else if activeMapped <=0 after increment we have already terminated and onNext isn't valid!
        }

        @Override
        public void onError(final Throwable t) {
            try {
                upstreamError = t;
                enqueueAndDrain(error(t));
            } finally {
                doCancel(false, true);
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

        private void doCancel(boolean cancelSubscription, boolean invalidatePendingDemand) {
            if (invalidatePendingDemand) {
                pendingDemand = -1;
            }
            try {
                if (cancelSubscription) {
                    assert subscription != null;
                    subscription.cancel();
                }
            } finally {
                cancellableSubscribers.cancel();
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

        private void tryEmitItem(Object item, FlatMapPublisherSubscriber<T, R> subscriber) {
            // We can skip the queue if the following conditions are meet:
            // 1. There is downstream requestN demand.
            // 2. The mapped subscriber doesn't have any signals already in the queue. We only need to preserve the
            //    ordering for each mapped source, and there is no "overall" ordering.
            // 3. We don't concurrently invoke the downstream subscriber. Concurrency control is provided by the
            //    emitting lock.
            final boolean needsDemand;
            if (subscriber.hasSignalsQueued() || ((needsDemand = needsDemand(item)) && !tryDecrementPendingDemand())) {
                subscriber.markSignalsQueued();
                enqueueAndDrain(item);
            } else if (item == MAPPED_SOURCE_COMPLETE) {
                requestMoreFromUpstream(1);
            } else if (tryAcquireLock(emittingLockUpdater, this)) { // fast path. no concurrency, try to skip the queue.
                try {
                    final boolean demandConsumed = sendToTarget(item);
                    assert demandConsumed == needsDemand;
                } finally {
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
            LOGGER.error("Queue should be unbounded, but an offer failed for item {}!", item);
            throw new QueueFullException("pending");
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
                        pendingDemand = prevDemand;
                        sendToTargetIfPrematureError();
                    } else {
                        Object t;
                        while (emittedCount < prevDemand && (t = signals.poll()) != null) {
                            try {
                                if (t == MAPPED_SOURCE_COMPLETE) {
                                    ++mappedSourcesCompleted;
                                } else if (sendToTarget(t)) {
                                    ++emittedCount;
                                }
                            } catch (Throwable cause) {
                                ++emittedCount;
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
                                sendToTarget(t); // if this throws its OK as we have terminated
                            } else {
                                sendToTargetIfPrematureError();
                            }
                        } else {
                            assert emittedCount < prevDemand;
                            pendingDemandUpdater.accumulateAndGet(this, prevDemand - emittedCount,
                                    FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                        }
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
            if (cancellableSubscribers.remove(subscriber) && decrementActiveMappedSources()) {
                return true;
            } else if (unusedDemand > 0) {
                incMappedDemand(unusedDemand);
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

            boolean request(int n) {
                assert n > 0;
                if (!pendingDemandUpdater.compareAndSet(this, 0, n)) {
                    return false;
                }
                signalsQueued = false;
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
                subscription.delayedSubscription(ConcurrentSubscription.wrap(s));
                // RequestN management for mapped sources is "approximate" as it is divided between mapped sources. More
                // demand may be distributed than is requested from downstream in order to avoid deadlock scenarios.
                // To accommodate for the "approximate" mapped demand we maintain a signal queue (bounded by the
                // concurrency). This presents an opportunity to decouple downstream requestN requests from iterating
                // all active mapped sources and instead optimistically give out demand here and replenish demand after
                // signals are delivered to the downstream subscriber (based upon available demand is available).
                parent.distributeMappedDemand(this);
            }

            @Override
            public void onNext(@Nullable final R r) {
                final int pendingDemand;
                try {
                    parent.tryEmitItem(wrapNull(r), this);
                } finally {
                    pendingDemand = pendingDemandUpdater.decrementAndGet(this);
                    if (pendingDemand == 0) {
                        // Emit this item to signify this Subscriber is hungry for more demand when it is available.
                        parent.tryEmitItem(this, this);
                    }
                }
                if (pendingDemand < 0) { // avoid putting this in finally block because it throws.
                    throwInvalidDemand(pendingDemand);
                }
            }

            private void throwInvalidDemand(int pendingDemand) {
                throw new IllegalStateException("Too many onNext signals for Subscriber: " + this +
                        " pendingDemand: " + pendingDemand);
            }

            @Override
            public void onError(final Throwable t) {
                Throwable currPendingError = parent.pendingError;
                if (parent.source.maxDelayedErrors == 0) {
                    if (currPendingError == null && pendingErrorUpdater.compareAndSet(parent, null, t)) {
                        try {
                            parent.doCancel(true, false);
                        } finally {
                            // Emit the error to preserve ordering relative to onNext signals for this source.
                            parent.tryEmitItem(error(t), this);
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
                    if (parent.removeSubscriber(this, pendingDemandUpdater.getAndSet(this, -1))) {
                        parent.enqueueAndDrain(error(currPendingError));
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
