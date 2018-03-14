/**
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.lang.Long.MIN_VALUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A {@link Subscription} that delegates all {@link Subscription} calls to a <em>current</em> {@link Subscription} instance
 * which can be changed using {@link #switchTo(Subscription)}.
 *
 * <h2>Request-N</h2>
 * Between two {@link Subscription}s, any pending requested items, i.e. items requested via {@link #request(long)} and not received via {@link #itemReceived()},
 * will be requested from the next {@link Subscription}.
 *
 * <h2>Cancel</h2>
 *
 * If this {@link Subscription} is cancelled, then any other {@link Subscription} set via {@link #switchTo(Subscription)} will be cancelled.
 */
final class SequentialSubscription implements Subscription, Cancellable {

    private static final long REQUESTED_WHEN_CANCELLED = Long.MIN_VALUE;

    private static final AtomicIntegerFieldUpdater<SequentialSubscription> processingUpdater =
            newUpdater(SequentialSubscription.class, "processing");
    private static final AtomicLongFieldUpdater<SequentialSubscription> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(SequentialSubscription.class, "requested");
    private static final AtomicLongFieldUpdater<SequentialSubscription> receivedUpdater =
            AtomicLongFieldUpdater.newUpdater(SequentialSubscription.class, "received");
    private static final AtomicReferenceFieldUpdater<SequentialSubscription, Subscription> pendingSwitchUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SequentialSubscription.class, Subscription.class, "pendingSwitch");

    /**
     * Only accessed while holding "processing lock", so does not need to be volatile.
     */
    private long sourceRequested;

    @SuppressWarnings("unused") private volatile Subscription current;
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) private volatile int processing;
    @SuppressWarnings("unused") private volatile long requested;
    @SuppressWarnings("unused") private volatile long received;
    @SuppressWarnings("unused") @Nullable private volatile Subscription pendingSwitch;

    /**
     * New instance.
     */
    SequentialSubscription() {
        this(EMPTY_SUBSCRIPTION);
    }

    /**
     * New instance.
     *
     * @param delegate {@link Subscription} to use as <em>current</em>.
     */
    SequentialSubscription(Subscription delegate) {
        this.current = requireNonNull(delegate);
    }

    @Override
    public void request(long n) {
        if (!isRequestNValid(n)) {
            // With invalid input we don't attempt to enforce concurrency and rely upon the subscription
            // to enforce the specification rules [1].
            // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#3.9.
            current.request(n);
            return;
        }
        requestedUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
        process();
    }

    @Override
    public void cancel() {
        requested = REQUESTED_WHEN_CANCELLED;
        process();
    }

    /**
     * Switches <em>current</em> {@link Subscription} to {@code next}.
     *
     * @param next {@link Subscription} that should now be <em>current</em>.
     */
    void switchTo(Subscription next) {
        requireNonNull(next);
        Subscription earlierPending = pendingSwitchUpdater.getAndSet(this, next);
        if (earlierPending != null) {
            // If there is a subscription which is pending and now we are switching to a new Subscription,
            // already pending Subscription will never be used. These situations may occur when a switch can be done without requesting data via request(n)
            // which typically is for terminal signals.
            earlierPending.cancel();
        }
        process();
    }

    /**
     * Callback when an item is received by the associated {@link Subscriber}.
     */
    void itemReceived() {
        receivedUpdater.incrementAndGet(this);
        process();
    }

    /**
     * Returns {@code true} if this {@link Subscription} is cancelled.
     *
     * @return {@code true} if this {@link Subscription} is cancelled.
     */
    boolean isCancelled() {
        return requested == REQUESTED_WHEN_CANCELLED;
    }

    private void process() {
        for (;;) {
            if (!processingUpdater.compareAndSet(this, 0, 1)) {
                break;
            }
            long recv = MIN_VALUE;
            final Subscription subscription;
            Subscription pending = pendingSwitchUpdater.getAndSet(this, null);
            if (pending != null) {
                try {
                    current.cancel();
                } catch (Throwable t) {
                    requested = REQUESTED_WHEN_CANCELLED;
                    processing = 0; // release lock
                    // Since we have swapped out pending already, no one else will see this and hence accessing it after
                    // releasing lock would not lead to concurrent access.
                    pending.cancel();
                    // If pending.cancel() above throws then we may hide the original t, but in such a case we are already
                    // in a fatal state and mostly the additional cause is not useful.
                    throw t;
                } finally {
                    current = pending; // Do the switch
                    sourceRequested = recv = received;
                }
                subscription = pending;
            } else {
                subscription = current;
            }
            final long r = requested;
            if (r == REQUESTED_WHEN_CANCELLED) {
                try {
                    subscription.cancel();
                } finally {
                    processing = 0; // release lock
                }
                if (pendingSwitch == null) {
                    return;
                }
            } else {
                final long sr = sourceRequested;
                try {
                    if (r != sr) {
                        sourceRequested = r;
                        subscription.request(r - sr);
                    }
                } catch (Throwable t) {
                    // subscription.request() has unexpectedly thrown so give up on further processing.
                    requested = REQUESTED_WHEN_CANCELLED;
                    throw t;
                } finally {
                    processing = 0; // release lock
                }
                if (r == requested && (recv == MIN_VALUE || received == recv) && pendingSwitch == null) {
                    return;
                }
            }
        }
    }
}
