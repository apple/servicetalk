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
 * If this {@link Subscription} is cancelled, then any other {@link Subscription} set via
 * {@link #switchTo(Subscription)} will be cancelled.
 */
final class SequentialSubscription implements Subscription {
    private static final AtomicLongFieldUpdater<SequentialSubscription> requestedUpdater =
            newUpdater(SequentialSubscription.class, "requested");
    private static final AtomicIntegerFieldUpdater<SequentialSubscription> emittingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SequentialSubscription.class, "emitting");
    private static final AtomicReferenceFieldUpdater<SequentialSubscription, Subscription> pendingSubscriptionUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SequentialSubscription.class, Subscription.class,
                    "pendingSubscription");

    // Only the drain owner changes the active subscription and its demand accounting.
    private Subscription subscription;
    private long sourceRequested;
    // Subscriber signals are serialized. Publishing the next subscription also publishes this count to the drain.
    private long sourceEmitted;
    private volatile long requested;
    private volatile int emitting;
    private volatile boolean cancelled;
    @Nullable
    private volatile Subscription pendingSubscription;

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
        drain();
    }

    @Override
    public void cancel() {
        cancelled = true;
        drain();
    }

    /**
     * Switches <strong>current</strong> {@link Subscription} to {@code next}. The previous source must no longer call
     * {@link #itemReceived()}. Subscriber signals must be serialized, but a previous request callback may still be
     * unwinding on another thread.
     * @param next {@link Subscription} that should now be <strong>current</strong>.
     */
    void switchTo(Subscription next) {
        final Subscription previous = pendingSubscriptionUpdater.getAndSet(this, requireNonNull(next));
        try {
            // A source may terminate without demand before the drain reaches it. Only the latest source needs demand,
            // but a displaced subscription must still observe a pending terminal action.
            if (previous != null) {
                if (cancelled) {
                    previous.cancel();
                } else {
                    final long n = requested;
                    if (n < 0) {
                        previous.request(n);
                    }
                }
            }
        } finally {
            drain();
        }
    }

    private void drain() {
        Throwable delayedCause = null;
        boolean tryAcquire = true;
        while (tryAcquire && tryAcquireLock(emittingUpdater, this)) {
            try {
                final Subscription next = pendingSubscriptionUpdater.getAndSet(this, null);
                if (cancelled) {
                    final Subscription current = subscription;
                    subscription = EMPTY_SUBSCRIPTION_NO_THROW;
                    try {
                        current.cancel();
                    } finally {
                        if (next != null) {
                            next.cancel();
                        }
                    }
                } else {
                    if (next != null) {
                        subscription = next;
                        sourceRequested = sourceEmitted;
                    }
                    final long n = requested;
                    if (sourceRequested >= 0) {
                        if (n < 0) {
                            sourceRequested = n;
                            subscription.request(n);
                        } else {
                            final long delta = n - sourceRequested;
                            if (delta != 0) {
                                // Commit before the callback: reentrant request/switch calls only enqueue more work.
                                sourceRequested = n;
                                subscription.request(delta);
                            }
                        }
                    }
                }
            } catch (Throwable cause) {
                delayedCause = catchUnexpected(delayedCause, cause);
            } finally {
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
