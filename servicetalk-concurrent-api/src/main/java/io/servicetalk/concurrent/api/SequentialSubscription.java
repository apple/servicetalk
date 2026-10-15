/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION_NO_THROW;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * A {@link Subscription} that delegates all {@link Subscription} calls to a <strong>current</strong>
 * {@link Subscription} instance which can be changed using {@link #switchTo(Subscription)}.
 *
 * <h2>Request-N</h2>
 * Between two {@link Subscription}s, any pending requested items, i.e. items requested via {@link #request(long)} and
 * not received via {@link #itemReceived()}, will be requested from the next {@link Subscription}.
 *
 * <h2>Cancel</h2>
 * If this {@link Subscription} is cancelled, then the <strong>current</strong> {@link Subscription} and any
 * {@link Subscription} that becomes <strong>current</strong> later via {@link #switchTo(Subscription)} will be
 * cancelled.
 */
final class SequentialSubscription implements Subscription {
    private static final AtomicLongFieldUpdater<SequentialSubscription> requestedUpdater =
            newUpdater(SequentialSubscription.class, "requested");
    private static final AtomicIntegerFieldUpdater<SequentialSubscription> emittingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SequentialSubscription.class, "emitting");
    private static final AtomicReferenceFieldUpdater<SequentialSubscription, Subscription> subscriptionUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SequentialSubscription.class, Subscription.class, "subscription");
    private static final AtomicReferenceFieldUpdater<SequentialSubscription, Subscription> pendingSubscriptionUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SequentialSubscription.class, Subscription.class,
                    "pendingSubscription");

    // Only the drain owner installs the next subscription and changes the demand accounting. cancel() may swap the
    // current subscription out concurrently.
    private volatile Subscription subscription;
    private long sourceRequested;
    // Subscriber signals are serialized. Publishing the next subscription also publishes this count to the drain.
    private long sourceEmitted;
    private volatile long requested;
    private volatile int emitting;
    private volatile boolean cancelled;
    @Nullable
    private volatile Subscription pendingSubscription;
    // Set while a drain started by switchTo() owns the lock. cancel() on another thread then leaves the cancel to it.
    @Nullable
    private volatile Thread switchDrainer;

    /**
     * New instance.
     */
    SequentialSubscription() {
        this(EMPTY_SUBSCRIPTION_NO_THROW);
    }

    /**
     * New instance.
     *
     * @param delegate {@link Subscription} to use as <em>current</em>.
     */
    SequentialSubscription(Subscription delegate) {
        this.subscription = requireNonNull(delegate);
    }

    @Override
    public void request(long n) {
        if (isRequestNValid(n)) {
            requestedUpdater.accumulateAndGet(this, n,
                    FlowControlUtils::addWithOverflowProtectionIfNotNegative);
        } else {
            requested = sanitizeInvalidRequestN(n);
        }
        drain(false);
    }

    @Override
    public void cancel() {
        cancelled = true;
        try {
            // Must run after cancelled is set to be race free with switchTo(). A drain owner blocked in request(n)
            // would otherwise never pick up the pending subscription to cancel it.
            cancelPending();
            // As on main, cancel the current subscription directly instead of waiting for the drain owner, whose
            // request(n) may only return once it is cancelled. Also as on main, while another thread drains for
            // switchTo(), leave the cancel to that thread, so cancel() never overlaps a request(n) it issued.
            final Thread drainer = switchDrainer;
            if (drainer == null || drainer == Thread.currentThread()) {
                // Swapping in EMPTY cancels each subscription once.
                subscriptionUpdater.getAndSet(this, EMPTY_SUBSCRIPTION_NO_THROW).cancel();
            }
        } finally {
            // Cancels a pending subscription. If a switching thread already read cancelled, this makes it run
            // another round, which cancels.
            drain(false);
        }
    }

    /**
     * Switches <strong>current</strong> {@link Subscription} to {@code next}. Callers must only switch after the
     * previous source has terminated, or after they cancelled it, and the previous source must no longer call
     * {@link #itemReceived()}. Subscriber signals must be serialized, but a previous request callback may still be
     * unwinding on another thread.
     * <p>
     * If a later call replaces {@code next} before a drain picks it up, {@code next} receives no calls.
     * @param next {@link Subscription} that should now be <strong>current</strong>.
     */
    void switchTo(Subscription next) {
        pendingSubscription = requireNonNull(next);
        if (cancelled) {
            cancelPending();
        }
        drain(true);
    }

    private void cancelPending() {
        final Subscription pending = pendingSubscriptionUpdater.getAndSet(this, null);
        if (pending != null) {
            pending.cancel();
        }
    }

    private void drain(boolean fromSwitch) {
        Throwable delayedCause = null;
        boolean tryAcquire = true;
        while (tryAcquire && tryAcquireLock(emittingUpdater, this)) {
            if (fromSwitch) {
                switchDrainer = Thread.currentThread();
            }
            try {
                // switchTo() writes pendingSubscription before it calls drain(). If this reads null just before that
                // write, that drain() call takes the lock or marks it pending, so another round picks next up.
                final Subscription next = pendingSubscription == null ? null :
                        pendingSubscriptionUpdater.getAndSet(this, null);
                if (next != null) {
                    sourceRequested = sourceEmitted;
                }
                try {
                    final long n = requested;
                    if (!cancelled && sourceRequested >= 0) {
                        final Subscription current = next == null ? subscription : next;
                        if (n < 0) {
                            sourceRequested = n;
                            current.request(n);
                        } else {
                            final long delta = n - sourceRequested;
                            assert delta >= 0 : "Source emitted more than requested: requested=" + n +
                                    ", delta=" + delta;
                            if (delta != 0) {
                                // Commit before the callback: reentrant request/switch calls only enqueue more work.
                                sourceRequested = n;
                                current.request(delta);
                            }
                        }
                    }
                } finally {
                    if (next != null) {
                        // next becomes current only after its first request(n) returns.
                        subscription = next;
                    }
                    // cancel() sets cancelled before it reads switchDrainer and swaps subscription. This sets both
                    // before it reads cancelled, so cancel() or this cancels the current subscription.
                    if (cancelled) {
                        subscriptionUpdater.getAndSet(this, EMPTY_SUBSCRIPTION_NO_THROW).cancel();
                    }
                }
            } catch (Throwable cause) {
                delayedCause = catchUnexpected(delayedCause, cause);
            } finally {
                if (fromSwitch) {
                    switchDrainer = null;
                }
                // A publisher racing with release either marks pending work or becomes the next drain owner.
                tryAcquire = !releaseLock(emittingUpdater, this);
            }
        }
        if (delayedCause != null) {
            throwException(delayedCause);
        }
    }

    /**
     * Callback when an item is received by the associated {@link Subscriber}.
     * <p>
     * Only can be called in the {@link Subscriber} thread!
     */
    void itemReceived() {
        ++sourceEmitted;
        // There is no limit to how much we request from the current Subscription, so no need to check if we need to
        // request any more here.
    }

    private static long sanitizeInvalidRequestN(long n) {
        return n == 0 ? Long.MIN_VALUE : n;
    }
}
