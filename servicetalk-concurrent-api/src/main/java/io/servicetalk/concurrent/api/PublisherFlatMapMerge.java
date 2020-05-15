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
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import static java.lang.Math.min;
import static java.util.Collections.newSetFromMap;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class PublisherFlatMapMerge<T, R> extends AbstractAsynchronousPublisherOperator<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherFlatMapMerge.class);
    private final Function<? super T, ? extends Publisher<? extends R>> mapper;
    private final int maxConcurrency;
    private final int maxMappedDemand;
    private final boolean delayError;

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError, Executor executor) {
        this(original, mapper, delayError, 16, 64, executor);
    }

    PublisherFlatMapMerge(Publisher<T> original, Function<? super T, ? extends Publisher<? extends R>> mapper,
                          boolean delayError, int maxConcurrency, int maxMappedDemand, Executor executor) {
        super(original, executor);
        this.mapper = requireNonNull(mapper);
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency: " + maxConcurrency + " (expected >0)");
        }
        if (maxMappedDemand <= 0) {
            throw new IllegalArgumentException("maxMappedDemand: " + maxMappedDemand + " (expected >0)");
        }
        this.maxMappedDemand = maxMappedDemand;
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
        private volatile long pendingDemand;

        // protected by emitting lock, or only accessed inside the Subscriber thread
        private boolean targetTerminated;
        private int upstreamDemand;
        @Nullable
        private Subscription subscription;

        private final Subscriber<? super R> target;
        private final Queue<Object> signals;
        private final PublisherFlatMapMerge<T, R> source;
        private final Set<FlatMapPublisherSubscriber<T, R>> subscribers;

        FlatMapSubscriber(PublisherFlatMapMerge<T, R> source, Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            // Start with a small capacity as maxConcurrency can be large.
            signals = newUnboundedMpscQueue(min(2, source.maxConcurrency * source.maxMappedDemand));
            subscribers = newSetFromMap(new ConcurrentHashMap<>(min(16, source.maxConcurrency)));
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
            } else if (pendingDemandUpdater.getAndAccumulate(this, n,
                    FlowControlUtils::addWithOverflowProtectionIfNotNegative) == 0) {
                drainPending();
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

            upstreamDemand = source.maxConcurrency;
            subscription.request(upstreamDemand);
        }

        @Override
        public void onNext(@Nullable final T t) {
            final Publisher<? extends R> publisher = requireNonNull(source.mapper.apply(t));
            FlatMapPublisherSubscriber<T, R> subscriber = new FlatMapPublisherSubscriber<>(this);
            final boolean added = subscribers.add(subscriber);
            assert added; // FlatMapPublisherSubscriber relies upon object equality and should never fail.
            for (;;) {
                final int prevActiveSources = activeMappedSources;
                if (prevActiveSources < 0) {
                    // We have been cancelled, or already completed and the active count flipped to negative, either way
                    // we don't want to Subscribe or retain a reference to this Publisher.
                    subscribers.remove(subscriber);
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

        private void doCancel(boolean cancelSubscription) {
            Throwable delayedCause = null;
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
                for (FlatMapPublisherSubscriber<T, R> subscriber : subscribers) {
                    try {
                        subscriber.cancelFromUpstream();
                    } catch (Throwable cause) {
                        delayedCause = catchUnexpected(delayedCause, cause);
                    }
                }
                subscribers.clear();
            }
            if (delayedCause != null) {
                throwException(delayedCause);
            }
        }

        private boolean tryDecrementDemand() {
            for (;;) {
                final long prevPendingDemand = pendingDemand;
                if (prevPendingDemand <= 0) {
                    return false;
                } else if (pendingDemandUpdater.compareAndSet(this, prevPendingDemand, prevPendingDemand - 1)) {
                    return true;
                }
            }
        }

        private void tryEmitItem(Object item, FlatMapPublisherSubscriber<T, R> subscriber) {
            if (tryAcquireLock(emittingLockUpdater, this)) { // fast path. no concurrency, try to skip the queue.
                try {
                    if (subscriber.hasSignalsQueued() || (needsDemand(item) && !tryDecrementDemand())) {
                        subscriber.markSignalsQueued();
                        enqueueItem(item); // drain isn't necessary. when demand arrives a drain will be attempted.
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
            while (tryAcquire && tryAcquireLock(emittingLockUpdater, this)) {
                try {
                    final long prevPendingDemand = pendingDemandUpdater.getAndSet(this, 0);
                    long emittedCount = 0;
                    if (prevPendingDemand < 0) {
                        // if we have been cancelled then we avoid delivering any signals, but we still deliver error
                        // terminals below by making sure emittedCount == prevPendingDemand.
                        pendingDemand = emittedCount = prevPendingDemand;
                        signals.clear();
                    }
                    Object t;
                    while (emittedCount < prevPendingDemand && (t = signals.poll()) != null) {
                        try {
                            if (sendToTarget(t)) {
                                ++emittedCount;
                            }
                        } catch (Throwable cause) {
                            delayedCause = catchUnexpected(delayedCause, cause);
                        }
                    }

                    // check if a terminal event is pending, or give back demand.
                    if (emittedCount == prevPendingDemand) {
                        for (;;) {
                            try {
                                t = signals.peek();
                                if (t == MAPPED_SOURCE_COMPLETE) {
                                    signals.poll();
                                    tryRequestMoreFromUpstream();
                                } else if (t instanceof FlatMapPublisherSubscriber) {
                                    signals.poll();
                                    ((FlatMapPublisherSubscriber<?, ?>) t).replenishDemandAndClearSignalsQueued();
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
                        assert emittedCount < prevPendingDemand;
                        pendingDemandUpdater.accumulateAndGet(this, prevPendingDemand - emittedCount,
                                FlowControlUtils::addWithOverflowProtection);
                    }
                } finally {
                    tryAcquire = !releaseLock(emittingLockUpdater, this);
                }
            }

            if (delayedCause != null) {
                throwException(delayedCause);
            }
        }

        private void tryRequestMoreFromUpstream() {
            if (--upstreamDemand == 0) { // heuristic to replenish demand when it is exhausted.
                assert subscription != null;
                upstreamDemand = source.maxConcurrency;
                subscription.request(source.maxConcurrency);
            }
        }

        private static boolean needsDemand(Object item) {
            return item != MAPPED_SOURCE_COMPLETE &&
                    !(item instanceof FlatMapPublisherSubscriber) && !(item instanceof TerminalNotification);
        }

        private boolean sendToTarget(Object item) {
            if (targetTerminated) {
                // No notifications past terminal/cancelled
                return false;
            } else if (item == MAPPED_SOURCE_COMPLETE) {
                tryRequestMoreFromUpstream();
                return false;
            } else if (item instanceof TerminalNotification) {
                // Load the terminal notification in case an error happened after an onComplete and we override the
                // terminal value.
                TerminalNotification terminalNotification = this.terminalNotification;
                assert terminalNotification != null;
                sendToTarget(terminalNotification);
                return false;
            } else if (item instanceof FlatMapPublisherSubscriber) {
                ((FlatMapPublisherSubscriber<?, ?>) item).replenishDemandAndClearSignalsQueued();
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

        private boolean removeSubscriber(final FlatMapPublisherSubscriber<T, R> subscriber) {
            return subscribers.remove(subscriber) && decrementActiveMappedSources();
        }

        private static final class FlatMapPublisherSubscriber<T, R> implements Subscriber<R> {
            @SuppressWarnings("rawtypes")
            private static final AtomicIntegerFieldUpdater<FlatMapPublisherSubscriber> pendingOnNextUpdater =
                    AtomicIntegerFieldUpdater.newUpdater(FlatMapPublisherSubscriber.class, "pendingOnNext");

            private final FlatMapSubscriber<T, R> parent;
            private final DelayedSubscription subscription;
            private volatile int pendingOnNext;
            /**
             * visibility provided by the {@link Subscriber} thread in {@link #onNext(Object)}, and then by
             * {@link #pendingDemand} when written to from {@link #replenishDemandAndClearSignalsQueued()} (potentially
             * called by a different thread). Demand is exhausted before {@link #replenishDemandAndClearSignalsQueued()}
             * is called, and that method triggers {@link #request(long)} and
             * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.1">there’s a
             * happens-before relationship between requesting elements and receiving elements</a>.
             */
            private boolean signalsQueued;

            FlatMapPublisherSubscriber(FlatMapSubscriber<T, R> parent) {
                this.parent = parent;
                subscription = new DelayedSubscription();
            }

            void cancelFromUpstream() {
                subscription.cancel();
            }

            void replenishDemandAndClearSignalsQueued() {
                // clearSignalsQueued must be before pendingOnNext for visibility with onNext.
                clearSignalsQueued();
                final int prevPendingOnNext = pendingOnNextUpdater.getAndSet(this, 0);
                if (prevPendingOnNext > 0) {
                    subscription.request(prevPendingOnNext);
                }
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
                subscription.request(parent.source.maxMappedDemand);
            }

            @Override
            public void onNext(@Nullable final R r) {
                // pendingOnNext must be updated before tryEmitItem for visibility of signalsQueued. signalsQueued
                // is updated in replenishDemandAndClearSignalsQueued (potentially from another thread) before
                // pendingOnNext is reset and we want signalsQueued to be visible to this thread.
                //
                // Heuristic which triggers replenishDemandAndClearSignalsQueued when demand is consumed. It must be
                // when all demand is consumed in order for clearSignalsQueued to work properly.
                final boolean doReplenish = pendingOnNextUpdater.incrementAndGet(this) == parent.source.maxMappedDemand;
                parent.tryEmitItem(wrapNull(r), this);
                if (doReplenish) {
                    parent.tryEmitItem(this, this);
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
                    if (parent.removeSubscriber(this)) {
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
                if (parent.removeSubscriber(this)) {
                    parent.enqueueAndDrain(complete());
                } else {
                    parent.tryEmitItem(MAPPED_SOURCE_COMPLETE, this);
                }
            }
        }
    }
}
