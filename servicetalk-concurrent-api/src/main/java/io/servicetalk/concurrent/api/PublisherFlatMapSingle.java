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

import static io.servicetalk.concurrent.api.CompositeExceptionUtils.addPendingError;
import static io.servicetalk.concurrent.api.CompositeExceptionUtils.maxDelayedErrors;
import static io.servicetalk.concurrent.api.PublisherFlatMapMerge.FLAT_MAP_DEFAULT_CONCURRENCY;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.calculateSourceRequested;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
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
                           boolean delayError) {
        this(original, mapper, delayError, FLAT_MAP_DEFAULT_CONCURRENCY);
    }

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, ? extends Single<? extends R>> mapper,
                           boolean delayError, int maxConcurrency) {
        this(original, mapper, maxDelayedErrors(delayError), maxConcurrency);
    }

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, ? extends Single<? extends R>> mapper,
                           int maxDelayedErrors, int maxConcurrency) {
        super(original);
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
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, Throwable> pendingErrorUpdater =
                newUpdater(FlatMapSubscriber.class, Throwable.class, "pendingError");
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> pendingErrorCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "pendingErrorCount");
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
        /**
         * An indicator in the pending queue that a Single terminated with error.
         */
        private static final Object SINGLE_ERROR = new Object();
        @Nullable
        private volatile Throwable pendingError;
        @SuppressWarnings("UnusedDeclaration")
        private volatile int pendingErrorCount;
        @SuppressWarnings("unused")
        private volatile int emitting;
        @SuppressWarnings("unused")
        private volatile long requested;
        @SuppressWarnings("unused")
        private volatile long sourceEmitted;
        @SuppressWarnings("unused")
        private volatile long sourceRequested;
        private volatile int activeMappedSources;
        /**
         * This variable is only accessed within the "emitting lock" so we rely upon this to provide visibility to
         * other threads.
         */
        private boolean targetTerminated;
        @Nullable
        private Subscription subscription;
        private final Queue<Object> pending;
        private final CancellableSet cancellableSet = new CancellableSet();
        private final PublisherFlatMapSingle<T, R> source;
        private final Subscriber<? super R> target;

        FlatMapSubscriber(PublisherFlatMapSingle<T, R> source, Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            // Start with a small capacity as maxConcurrency can be large.
            pending = newUnboundedMpscQueue(min(2, source.maxConcurrency));
        }

        @Override
        public void request(long n) {
            assert subscription != null;
            if (isRequestNValid(n)) {
                requestedUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtection);
                int actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                        sourceEmittedUpdater, source.maxConcurrency, this);
                if (actualSourceRequestN != 0) {
                    subscription.request(actualSourceRequestN);
                }
            } else {
                subscription.request(n);
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
            if (activeMappedSourcesUpdater.incrementAndGet(this) > 0) {
                next.subscribeInternal(new FlatMapSingleSubscriber());
            }
            // else we have already terminated and onNext isn't valid!
        }

        @Override
        public void onError(Throwable t) {
            onError0(t, false);
        }

        @Override
        public void onComplete() {
            if (terminateActiveMappedSources()) {
                // delayedError is checked in drain loop, and complete() is discarded if there are errors pending.
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

        private void onError0(Throwable throwable, boolean cancelUpstream) {
            try {
                doCancel(cancelUpstream);
            } finally {
                enqueueAndDrain(error(throwable));
            }
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
                enqueueFailed(item);
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

        private void enqueueFailed(Object item) {
            LOGGER.error("Queue should be unbounded, but an offer failed for item {}!", item);
            // Note that we throw even if the item represents a terminal signal (even though we don't expect another
            // terminal signal to be delivered from the upstream source because we are already terminated). If we fail
            // to enqueue a terminal event async control flow won't be completed and the user won't be notified. This
            // is a relatively extreme failure condition and we fail loudly to clarify that signal delivery is
            // interrupted and the user may experience hangs.
            throw new QueueFullException("pending");
        }

        /**
         * Cancel and cleanup.
         * @param cancelUpstream enforces the
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.3">
         *     reactive streams rule 2.3</a>.
         */
        private void doCancel(boolean cancelUpstream) {
            try {
                if (cancelUpstream) {
                    Subscription subscription = this.subscription;
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

        private void sendToTarget(Object item) {
            if (targetTerminated || item == SINGLE_ERROR) { // No notifications past terminal/cancelled
                return;
            }
            if (item instanceof TerminalNotification) {
                targetTerminated = true;
                final Throwable currPendingError = pendingError;
                if (currPendingError != null) {
                    target.onError(currPendingError);
                } else {
                    ((TerminalNotification) item).terminate(target);
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
                cancellableSet.add(singleCancellable);
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
                Throwable currPendingError = pendingError;
                if (source.maxDelayedErrors == 0) {
                    if (currPendingError == null &&
                            pendingErrorUpdater.compareAndSet(FlatMapSubscriber.this, null, t)) {
                        onError0(t, true);
                    }
                } else {
                    if (currPendingError == null) {
                        if (pendingErrorUpdater.compareAndSet(FlatMapSubscriber.this, null, t)) {
                            currPendingError = t;
                        } else {
                            currPendingError = pendingError;
                            assert currPendingError != null;
                            addPendingError(pendingErrorCountUpdater, FlatMapSubscriber.this, source.maxDelayedErrors,
                                    currPendingError, t);
                        }
                    } else {
                        addPendingError(pendingErrorCountUpdater, FlatMapSubscriber.this, source.maxDelayedErrors,
                                currPendingError, t);
                    }
                    if (onSingleTerminated()) {
                        enqueueAndDrain(error(currPendingError));
                    } else {
                        // Queueing/draining may result in requestN more data.
                        tryEmitItem(SINGLE_ERROR);
                    }
                }
            }

            private boolean onSingleTerminated() {
                if (singleCancellable == null) {
                    logDuplicateTerminal();
                    return false;
                }
                cancellableSet.remove(singleCancellable);
                singleCancellable = null;
                return decrementActiveMappedSources();
            }

            private void logDuplicateTerminal() {
                LOGGER.warn("onSubscribe not called before terminal or duplicate terminal on Subscriber {}", this,
                        new IllegalStateException(
                                "onSubscribe not called before terminal or duplicate terminal on Subscriber " + this +
                                " forbidden see: " +
                                "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.9" +
                                "https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1.7"));
            }
        }
    }
}
