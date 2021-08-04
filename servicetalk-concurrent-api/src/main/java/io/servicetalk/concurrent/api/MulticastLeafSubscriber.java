/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.calculateSourceRequested;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;

abstract class MulticastLeafSubscriber<T> implements Subscriber<T>, Subscription {
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<MulticastLeafSubscriber> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(MulticastLeafSubscriber.class, "requested");
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<MulticastLeafSubscriber> sourceRequestedUpdater =
            AtomicLongFieldUpdater.newUpdater(MulticastLeafSubscriber.class, "sourceRequested");
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<MulticastLeafSubscriber> sourceEmittedUpdater =
            AtomicLongFieldUpdater.newUpdater(MulticastLeafSubscriber.class, "sourceEmitted");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<MulticastLeafSubscriber> emittingLockUpdater =
            AtomicIntegerFieldUpdater.newUpdater(MulticastLeafSubscriber.class, "emittingLock");
    /**
     * Protected by {@link #emittingLock}.
     */
    @Nullable
    private Queue<Object> signalQueue;
    private volatile long requested;
    @SuppressWarnings("unused")
    private volatile long sourceRequested;
    private volatile long sourceEmitted;
    @SuppressWarnings("unused")
    private volatile int emittingLock;
    private boolean cancelled;

    MulticastLeafSubscriber() {
        // Hold the lock to ensure onSubscribe is the first signal. subscribersSet() and triggerOnSubscribe() must
        // be called before we can deliver onSubscribe.
        tryAcquireLock(emittingLockUpdater, this);
    }

    /**
     * Get the {@link Subscriber} to emit events to from the {@link Subscriber} thread.
     * @return the {@link Subscriber} to emit events to from the {@link Subscriber} thread.
     */
    @Nullable
    abstract Subscriber<? super T> subscriber();

    /**
     * Get the {@link Subscriber} to emit events to from the {@link Subscription} thread.
     * @return the {@link Subscriber} to emit events to from the {@link Subscription} thread.
     */
    @Nullable
    abstract Subscriber<? super T> subscriberOnSubscriptionThread();

    /**
     * {@link Subscription#request(long)} has been invoked on the {@link Subscription} associated with this
     * {@link Subscriber}.
     * @param n The amount that has been requested.
     */
    abstract void requestUpstream(long n);

    /**
     * {@link Subscription#cancel()} has been invoked on the {@link Subscription} associated with this
     * {@link Subscriber}. Cleanup state associated with this {@link Subscriber} and potentially
     * {@link Subscription#cancel()} upstream.
     */
    abstract void cancelUpstream();

    /**
     * Get the amount to limit outstanding demand.
     * @return the amount to limit outstanding demand.
     */
    abstract int outstandingDemandLimit();

    final void triggerOnSubscribe() {
        final Subscriber<? super T> subscriber = subscriberOnSubscriptionThread();
        assert subscriber != null;
        try { // emittingLock is already held specifically so that onSubscribe is the first signal.
            subscriber.onSubscribe(this);
        } finally {
            if (!releaseLock(emittingLockUpdater, this)) {
                drainSignalQueue(subscriber);
            }
        }
    }

    @Override
    public final void request(long n) {
        if (cancelled) {
            return;
        }
        if (isRequestNValid(n)) {
            requestedUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtection);
            drainSignalQueueSupplier(subscriberOnSubscriptionThread(), this::subscriberOnSubscriptionThread);
        } else {
            requestUpstream(n);
        }
    }

    @Override
    public final void cancel() {
        // Best effort to make the Subscription noop after this method is called.
        // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.6
        cancelled = true;
        cancelUpstream();
    }

    @Override
    public final void onSubscribe(final Subscription subscription) {
        // only triggerOnSubscribe is supported.
        throw new UnsupportedOperationException();
    }

    @Override
    public final void onNext(@Nullable final T t) {
        final Subscriber<? super T> subscriber = subscriber();
        if (subscriber == null) {
            getOrCreateSignalQueue(8).add(wrapNull(t));
            drainSignalQueueSupplier(null, this::subscriber);
        } else if (hasSignalsQueued()) {
            getOrCreateSignalQueue(8).add(wrapNull(t));
            drainSignalQueue(subscriber);
        } else if (tryAcquireLock(emittingLockUpdater, this)) {
            // The queue is empty, and we acquired the lock so we can try to directly deliver to target
            // (assuming there is request(n) demand).
            if (sourceEmitted < requested) {
                try {
                    // We ignore overflow here because once we get to this extreme, we won't be able to account for
                    // more data anyways.
                    sourceEmittedUpdater.getAndIncrement(this);
                    subscriber.onNext(t);
                } finally {
                    if (releaseLock(emittingLockUpdater, this)) {
                        updateRequestN();
                    } else {
                        drainSignalQueue(subscriber);
                    }
                }
            } else {
                releaseLock(emittingLockUpdater, this);
                getOrCreateSignalQueue(8).add(wrapNull(t));
                drainSignalQueue(subscriber);
            }
        } else {
            getOrCreateSignalQueue(8).add(wrapNull(t));
            drainSignalQueue(subscriber);
        }
    }

    @Override
    public final void onError(final Throwable t) {
        onTerminal(t, (cause, sub) -> sub.onError(cause), TerminalNotification::error);
    }

    @Override
    public final void onComplete() {
        onTerminal(null, (cause, sub) -> sub.onComplete(), cause -> TerminalNotification.complete());
    }

    private void updateRequestN() {
        final long actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                sourceEmittedUpdater, outstandingDemandLimit(), this);
        if (actualSourceRequestN > 0) {
            requestUpstream(actualSourceRequestN);
        }
    }

    private Queue<Object> getOrCreateSignalQueue(int size) {
        if (signalQueue == null) {
            signalQueue = newUnboundedSpscQueue(size);
        }
        return signalQueue;
    }

    private void onTerminal(@Nullable Throwable t,
                            BiConsumer<Throwable, Subscriber<? super T>> emitter,
                            Function<Throwable, TerminalNotification> terminalFunc) {
        final Subscriber<? super T> subscriber = subscriber();
        if (subscriber == null) {
            getOrCreateSignalQueue(1).add(terminalFunc.apply(t));
            drainSignalQueueSupplier(null, this::subscriber);
        } else if (hasSignalsQueued()) {
            getOrCreateSignalQueue(1).add(terminalFunc.apply(t));
            drainSignalQueue(subscriber);
        } else if (tryAcquireLock(emittingLockUpdater, this)) {
            emitter.accept(t, subscriber);
            // poison the lock
        } else {
            getOrCreateSignalQueue(1).add(terminalFunc.apply(t));
            drainSignalQueue(subscriber);
        }
    }

    private boolean hasSignalsQueued() {
        return signalQueue != null && !signalQueue.isEmpty();
    }

    private void drainSignalQueue(Subscriber<? super T> subscriber) {
        drainSignalQueueSupplier(subscriber, () -> null);
    }

    private void drainSignalQueueSupplier(@Nullable Subscriber<? super T> subscriber,
                                          Supplier<Subscriber<? super T>> subFunc) {
        Throwable delayedCause = null;
        boolean tryAcquire = true;
        boolean acquired = false;
        while (tryAcquire && tryAcquireLock(emittingLockUpdater, this)) {
            try {
                acquired = true;
                if (subscriber == null) {
                    subscriber = subFunc.get();
                }
                if (subscriber != null && signalQueue != null && !signalQueue.isEmpty()) {
                    long innerOnNextCount = 0;
                    final long outstandingDemand = requested - sourceEmitted;
                    Object signal;
                    while (innerOnNextCount < outstandingDemand && (signal = signalQueue.poll()) != null) {
                        try {
                            if (signal instanceof TerminalNotification) {
                                ((TerminalNotification) signal).terminate(subscriber);
                            } else {
                                // reentry is not allowed, we can update sourceEmitted in bulk after queue drain.
                                ++innerOnNextCount;
                                subscriber.onNext(unwrapNullUnchecked(signal));
                            }
                        } catch (Throwable cause) {
                            delayedCause = catchUnexpected(delayedCause, cause);
                        }
                    }

                    if (innerOnNextCount != 0) {
                        // We always increase the demand (even if we terminate) because we may have stolen demand
                        // from another peer source and need to return it in updateRequestN.
                        sourceEmittedUpdater.addAndGet(this, innerOnNextCount);
                    }
                    if (innerOnNextCount == outstandingDemand &&
                            (signal = signalQueue.peek()) instanceof TerminalNotification) {
                        signalQueue.poll();
                        ((TerminalNotification) signal).terminate(subscriber);
                    }
                }
            } finally {
                tryAcquire = !releaseLock(emittingLockUpdater, this);
            }
        }
        // Call updateRequestN outside the lock if it has been acquired. If we do it inside the lock on the
        // Subscription thread it is possible the Subscription thread hasn't yet seen items enqueued on the
        // Subscriber thread until it re-acquires the lock (would lead to over requesting demand). Requesting
        // outside the lock ensures that the Subscription thread has dequeued all available items at the time the
        // requestN operation occurred.
        if (acquired) {
            updateRequestN();
        }
        if (delayedCause != null) {
            throwException(delayedCause);
        }
    }
}
