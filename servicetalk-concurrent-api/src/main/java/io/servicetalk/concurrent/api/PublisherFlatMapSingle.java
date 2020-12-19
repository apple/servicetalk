/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
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

import static io.servicetalk.concurrent.api.CompositeException.maxDelayedErrors;
import static io.servicetalk.concurrent.api.PublisherFlatMapMerge.FLAT_MAP_DEFAULT_CONCURRENCY;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.calculateSourceRequested;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.trySetTerminal;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedMpscQueue;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * As returned by {@link Publisher#flatMapMergeSingle(Function, int)} and its variants.
 *
 * @param <R> Type of items emitted by this {@link Publisher}
 * @param <T> Type of items emitted by source {@link Publisher}
 */
final class PublisherFlatMapSingle<T, R> extends AbstractAsynchronousPublisherOperator<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherFlatMapSingle.class);
    private final Function<? super T, ? extends Single<? extends R>> mapper;
    private final int maxConcurrency;
    private final int maxDelayedErrors;

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, ? extends Single<? extends R>> mapper,
                           boolean delayError, Executor executor) {
        this(original, mapper, delayError, FLAT_MAP_DEFAULT_CONCURRENCY, executor);
    }

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, ? extends Single<? extends R>> mapper,
                           boolean delayError, int maxConcurrency, Executor executor) {
        this(original, mapper, maxDelayedErrors(delayError), maxConcurrency, executor);
    }

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, ? extends Single<? extends R>> mapper,
                           int maxDelayedErrors, int maxConcurrency, Executor executor) {
        super(original, executor);
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency: " + maxConcurrency + " (expected > 0)");
        }
        if (maxDelayedErrors < 0) {
            throw new IllegalArgumentException("maxDelayedErrors: " + maxDelayedErrors + " (expected >=0)");
        }
        this.mapper = requireNonNull(mapper);
        this.maxConcurrency = maxConcurrency;
        this.maxDelayedErrors = maxDelayedErrors;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super R> subscriber) {
        return new FlatMapSubscriber<>(this, subscriber);
    }

    private static final class FlatMapSubscriber<T, R> implements Subscriber<T>, Subscription {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, CompositeException> delayedErrorUpdater =
                newUpdater(FlatMapSubscriber.class, CompositeException.class, "delayedError");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> emittingUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "emitting");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> requestedUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "requested");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> sourceRequestedUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "sourceRequested");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> sourceEmittedUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "sourceEmitted");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> activeMappedSourcesUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "activeMappedSources");
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, TerminalNotification>
                terminalNotificationUpdater = newUpdater(FlatMapSubscriber.class, TerminalNotification.class,
                "terminalNotification");

        @SuppressWarnings("unused")
        @Nullable
        private volatile CompositeException delayedError;
        @SuppressWarnings("unused")
        private volatile int emitting;
        @SuppressWarnings("unused")
        private volatile long requested;
        @SuppressWarnings("unused")
        private volatile long sourceEmitted;
        @SuppressWarnings("unused")
        private volatile long sourceRequested;
        private volatile int activeMappedSources;
        @SuppressWarnings("unused")
        @Nullable
        private volatile TerminalNotification terminalNotification;
        /**
         * This variable is only accessed within the "emitting lock" so we rely upon this to provide visibility to
         * other threads.
         */
        private boolean targetTerminated;
        @Nullable
        private Subscription subscription;

        private final Queue<Object> pending;
        private final CancellableSet cancellable = new CancellableSet();
        private final PublisherFlatMapSingle<T, R> source;
        private final Subscriber<? super R> target;

        /*
         * An indicator in the pending queue that a Single terminated with error.
         */
        private static final Object SINGLE_ERROR = new Object();

        FlatMapSubscriber(PublisherFlatMapSingle<T, R> source, Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            // Start with a small capacity as maxConcurrency can be large.
            pending = newUnboundedMpscQueue(min(2, source.maxConcurrency));
        }

        @Override
        public void request(long n) {
            assert subscription != null;
            if (!isRequestNValid(n)) {
                subscription.request(n);
                return;
            }

            requestedUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtection);
            int actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                    sourceEmittedUpdater, source.maxConcurrency, this);
            if (actualSourceRequestN != 0) {
                subscription.request(actualSourceRequestN);
            }
        }

        @Override
        public void cancel() {
            doCancel(true);
        }

        @Override
        public void onSubscribe(Subscription s) {
            // Subscription is volatile, but there will be no access to the subscription until we call onSubscribe below
            // so we don't have to worry about atomic operations here.
            if (!checkDuplicateSubscription(subscription, s)) {
                return;
            }
            // We assume that FlatMapSubscriber#cancel() will never be called before this method, and therefore we
            // don't have to worry about being cancelled before the onSubscribe method is called.
            subscription = ConcurrentSubscription.wrap(s);
            target.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            final Single<? extends R> next = requireNonNull(source.mapper.apply(t));

            // Requested count will be decremented after this single completes.
            // If we are cancelled the activeUpdater count is best effort depending upon which sources finish. This best
            // effort behavior mimics the semantics of cancel though so we don't take any special action to try to
            // adjust the count or prematurely terminate.
            final int activeSources = activeMappedSourcesUpdater.incrementAndGet(this);
            assert activeSources > 0; // otherwise onComplete was previously invoked or concurrency on this Subscriber.
            next.subscribeInternal(new FlatMapSingleSubscriber());
        }

        @Override
        public void onError(Throwable t) {
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

        private void tryEmitItem(final Object item) {
            if (tryAcquireLock(emittingUpdater, this)) { // fast path. no concurrency, avoid the queue and emit.
                try {
                    sendToTarget(item);
                    doDrainPostProcessing(1);
                } finally {
                    if (!releaseLock(emittingUpdater, this)) {
                        drainPending();
                    }
                }
            } else { // slow path. there is concurrency, so just go through the queue.
                enqueueAndDrain(item);
            }
        }

        private void enqueueAndDrain(Object item) {
            if (!pending.offer(item)) {
                enqueueAndDrainFailed(item);
            }
            drainPending();
        }

        private void drainPending() {
            long drainCount = 0;
            Throwable delayedCause = null;
            boolean tryAcquire = true;
            while (tryAcquire && tryAcquireLock(emittingUpdater, this)) {
                try {
                    Object t;
                    while ((t = pending.poll()) != null) {
                        ++drainCount;
                        try {
                            sendToTarget(t);
                        } catch (Throwable cause) {
                            delayedCause = catchUnexpected(delayedCause, cause);
                        }
                    }
                } finally {
                    tryAcquire = !releaseLock(emittingUpdater, this);
                }
            }

            if (delayedCause != null) {
                throwException(delayedCause);
            }

            if (drainCount != 0) {
                doDrainPostProcessing(drainCount);
            }
        }

        private void doDrainPostProcessing(final long drainCount) {
            assert subscription != null;
            // We ignore overflow here because once we get to this extreme, we won't be able to account for more
            // data anyways.
            sourceEmittedUpdater.addAndGet(this, drainCount);
            final int actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                    sourceEmittedUpdater, source.maxConcurrency, this);
            if (actualSourceRequestN != 0) {
                subscription.request(actualSourceRequestN);
            }
        }

        private void enqueueAndDrainFailed(Object item) {
            QueueFullException exception = new QueueFullException("pending");
            if (item instanceof TerminalNotification) {
                LOGGER.error("Queue should be unbounded, but an offer failed!", exception);
                throw exception;
            } else {
                onError0(exception, true, true);
            }
        }

        /**
         * Cancel and cleanup.
         * @param cancelSubscription enforces the
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.3">
         *     reactive streams rule 2.3</a>.
         */
        private void doCancel(boolean cancelSubscription) {
            cancellable.cancel();
            if (cancelSubscription) {
                Subscription subscription = this.subscription;
                assert subscription != null;
                subscription.cancel();
            }
        }

        private void sendToTarget(Object item) {
            if (targetTerminated || item == SINGLE_ERROR) {
                // No notifications past terminal/cancelled
                return;
            }
            if (item instanceof TerminalNotification) {
                targetTerminated = true;
                // Load the terminal notification in case an error happened after an onComplete and we override the
                // terminal value.
                TerminalNotification terminalNotification = this.terminalNotification;
                assert terminalNotification != null;
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
            } else {
                target.onNext(unwrapNullUnchecked(item));
            }
        }

        private final class FlatMapSingleSubscriber implements SingleSource.Subscriber<R> {
            @Nullable
            private Cancellable singleCancellable;

            @Override
            public void onSubscribe(Cancellable singleCancellable) {
                // It is possible we have been cancelled at this point, and cancellable/FlatMapSingleSubscriber will
                // take care of propagating the cancel to next. The activeUpdater count is now best effort depending
                // upon which sources finish. This best effort behavior mimics the semantics of cancel though so we
                // don't take any special action to try to adjust the count or prematurely terminate.
                this.singleCancellable = singleCancellable;
                cancellable.add(singleCancellable);
            }

            @Override
            public void onSuccess(@Nullable R result) {
                // First enqueue the result and then decrement active count. Since onComplete() checks for active count,
                // if we decrement count before enqueuing, onComplete() may emit the terminal event without emitting
                // the result.
                tryEmitItem(wrapNull(result));
                if (onSingleTerminated()) {
                    enqueueAndDrain(complete());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (source.maxDelayedErrors == 0) {
                    onError0(t, true, true);
                } else {
                    CompositeException de = FlatMapSubscriber.this.delayedError;
                    if (de == null) {
                        de = new CompositeException(t, source.maxDelayedErrors);
                        if (!delayedErrorUpdater.compareAndSet(FlatMapSubscriber.this, null, de)) {
                            de = FlatMapSubscriber.this.delayedError;
                            assert de != null;
                            de.add(t);
                        }
                    } else {
                        de.add(t);
                    }
                    if (onSingleTerminated()) {
                        if (trySetTerminal(TerminalNotification.error(de), true, terminalNotificationUpdater,
                                FlatMapSubscriber.this)) {
                            // Since we have already added error to delayedError, we use complete() TerminalNotification
                            // as a dummy signal to start draining and termination.
                            enqueueAndDrain(complete());
                        }
                    } else {
                        // Queueing/draining may result in requestN more data.
                        tryEmitItem(SINGLE_ERROR);
                    }
                }
            }

            private boolean onSingleTerminated() {
                assert singleCancellable != null;
                cancellable.remove(singleCancellable);
                return decrementActiveMappedSources();
            }
        }
    }
}
