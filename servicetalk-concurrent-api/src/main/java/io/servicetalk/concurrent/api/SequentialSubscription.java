/*
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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtil;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Subscription} that delegates all {@link Subscription} calls to a <strong>current</strong>
 * {@link Subscription} instance which can be changed using {@link #switchTo(Subscription)}.
 *
 * <h2>Request-N</h2>
 * Between two {@link Subscription}s, any pending requested items, i.e. items requested via {@link #request(long)} and
 * not received via {@link #itemReceived()}, will be requested from the next {@link Subscription}.
 *
 * <h2>Cancel</h2>
 *
 * If this {@link Subscription} is cancelled, then any other {@link Subscription} set via
 * {@link #switchTo(Subscription)} will be cancelled.
 */
final class SequentialSubscription implements Subscription, Cancellable {
    private static final AtomicLongFieldUpdater<SequentialSubscription> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(SequentialSubscription.class, "requested");
    private static final AtomicLongFieldUpdater<SequentialSubscription> sourceRequestedUpdater =
            AtomicLongFieldUpdater.newUpdater(SequentialSubscription.class, "sourceRequested");

    private long sourceEmitted;
    @SuppressWarnings("unused")
    private volatile long requested;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile long sourceRequested;
    @SuppressWarnings("unused")
    private volatile Subscription subscription;

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
        this.subscription = requireNonNull(delegate);
    }

    @Override
    public void request(long n) {
        if (isRequestNValid(n)) {
            requestedUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
            requestFromSubscription();
        } else {
            // With invalid input we don't attempt to enforce concurrency and rely upon the subscription
            // to enforce the specification rules [1].
            // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#3.9.
            subscription.request(n);
        }
    }

    @Override
    public void cancel() {
        // Invalidate requested here so that it can be seen by switchTo later.
        requested = -1;

        // We may concurrently interact with the Subscription here if another thread is in switchTo. However switchTo
        // takes care to wrap the Subscription in ConcurrentSubscription while concurrent invocation is possible.
        // Note that the order of instructions is important between invalidating requested and reading the subscription
        // volatile variable! requested is set first so that the switchTo thread will observe that cancel has been
        // called and call cancel on any new (or concurrently switched) Subscriptions.
        subscription.cancel();
    }

    /**
     * Switches <strong>current</strong> {@link Subscription} to {@code next}. It is assumed the {@link Subscriber}
     * associated with the previous {@link Subscription} will no longer call {@link #itemReceived()}.
     * <p>
     * Only can be called in the {@link Subscriber} thread!
     * @param next {@link Subscription} that should now be <strong>current</strong>.
     */
    void switchTo(Subscription next) {
        requireNonNull(next);
        // Make the sourceRequested invalid so no other thread will interact with the next Subscription.
        sourceRequested = -1;

        // Switch the subscription, it is assumed the previous Subscription is completed, so no need to interact with
        // the previous subscription.
        // We wrap in a ConcurrentSubscription because it is possible this method may interact with the Subscription
        // while the Subscription thread does too (in order to catch up the current Subscription).
        subscription = ConcurrentSubscription.wrap(next);

        // If requested is non-negative, then subscription will be cancelled if the outer Subscription is
        // cancelled. However if it is negative that means we have already been cancelled and we should cancel the new
        // Subscription.
        if (requested >= 0) {
            // We previously invalidated the sourceRequested to prevent other threads from interacting with the
            // Subscription before we had a chance to reset it, which may result in double requesting. Now that we have
            // swapped the subscription we can reset the sourceRequested value to sourceEmitted (the amount we have
            // observed) because the new Subscription hasn't delivered any data yet.
            sourceRequested = sourceEmitted;

            requestFromSubscription();
        } else {
            subscription.cancel();
        }

        // We can unwrap the Subscription because there will no longer be any concurrent invocation on the Subscription.
        subscription = next;
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

    private void requestFromSubscription() {
        for (;;) {
            // We have to read the Subscription before sourceRequested because sourceRequested is set to a negative
            // value before swapping the subscription. So the subscription will be usable if sourceRequested is valid.
            final Subscription subscription = this.subscription;
            final long sourceRequested = this.sourceRequested;
            final long requested = this.requested;
            if (requested == sourceRequested || sourceRequested < 0 || requested < 0) {
                break;
            }

            // sourceRequested ...[delta]... requested
            final long delta = requested - sourceRequested;
            if (sourceRequestedUpdater.compareAndSet(this, sourceRequested, sourceRequested + delta)) {
                subscription.request(delta);
                break;
            }
        }
    }
}
