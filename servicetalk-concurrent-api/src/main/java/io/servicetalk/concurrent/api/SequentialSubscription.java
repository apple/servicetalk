/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.FlowControlUtils;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
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
final class SequentialSubscription implements Subscription, Cancellable {
    private static final long SWITCHING = -1;
    private static final long REQUESTED = -2;
    private static final long CANCELLED = -3;
    private static final AtomicLongFieldUpdater<SequentialSubscription> requestedUpdater =
            newUpdater(SequentialSubscription.class, "requested");
    private static final AtomicLongFieldUpdater<SequentialSubscription> sourceRequestedUpdater =
            newUpdater(SequentialSubscription.class, "sourceRequested");

    private Subscription subscription;
    private long sourceEmitted;
    @SuppressWarnings("unused")
    private volatile long requested;
    @SuppressWarnings({"unused"})
    private volatile long sourceRequested;

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
        final long currRequested;
        if (isRequestNValid(n)) {
            currRequested = requestedUpdater.accumulateAndGet(this, n,
                    FlowControlUtils::addWithOverflowProtectionIfNotNegative);
        } else {
            currRequested = sanitizeInvalidRequestN(n);
            requested = currRequested;
        }

        for (;;) {
            final long currSourceRequested = sourceRequested;
            if (currSourceRequested == CANCELLED) {
                break;
            } else if (currSourceRequested < 0) {
                assert currSourceRequested == SWITCHING || currSourceRequested == REQUESTED;
                if (sourceRequestedUpdater.compareAndSet(this, currSourceRequested, REQUESTED)) {
                    break;
                }
            } else {
                // We must read the subscription BEFORE the CAS (which involves a read barrier). This ensures if the
                // sourceRequested value is visible then the subscription (which may have been switched by
                // another thread) is also visible.
                final Subscription currSubscription = subscription;
                if (isRequestNValid(currRequested)) {
                    // sourceRequested ...[delta]... requested
                    final long delta = currRequested - currSourceRequested;
                    if (sourceRequestedUpdater.compareAndSet(this, currSourceRequested, currSourceRequested + delta)) {
                        // sourceRequested is either monotonically increasing, or set to an invalid value
                        // (e.g. negative) if a Subscription switch is on going and atomically set to requestN to
                        // preserve the monotonic increasing property. If the CAS worked that means the value of
                        // subscription before will be visible if there was previously a switch. We also know there is
                        // no concurrent interaction on the subscription because currSourceRequested is known not to be
                        // SWITCHING, and the value would have increased.
                        if (delta != 0) {
                            currSubscription.request(delta);
                        }
                        break;
                    }
                } else if (sourceRequestedUpdater.compareAndSet(this, currSourceRequested, CANCELLED)) {
                    currSubscription.request(currRequested);
                    break;
                }
            }
        }
    }

    @Override
    public void cancel() {
        final Subscription currSubscription = subscription;
        final long currSourceRequested = sourceRequestedUpdater.getAndSet(this, CANCELLED);
        // To avoid concurrent invocation with the switch thread we defer to that thread to cancel.
        if (currSourceRequested >= 0) {
            currSubscription.cancel();
        }
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
        // No special concurrency considerations for sourceEmitted access is required in this method because we are
        // on the Subscriber thread in this method. We want to track the effective source requested for the purposes of
        // how much more request(n) is necessary below.
        long effectiveSourceRequested = sourceEmitted;
        for (;;) {
            final long currSourceRequested = sourceRequested;
            assert currSourceRequested != SWITCHING; // no concurrency on this method allowed
            if (currSourceRequested == CANCELLED) {
                next.cancel();
                break;
            } else if (sourceRequestedUpdater.compareAndSet(this, currSourceRequested, SWITCHING)) {
                assert currSourceRequested >= 0 || currSourceRequested == REQUESTED;
                final long currRequested = requested;
                if (currRequested < 0) { // invalid requestN is pending.
                    sourceRequested = CANCELLED;
                    next.request(currRequested);
                    break;
                }

                // sourceEmitted is stable here because we are on the Subscriber thread. We want to request the
                // difference between total requested and what has been emitted from the new subscription. We also
                // need to set the value of total requested below to make sure it is monotonically increasing.
                // effectiveSourceRequested ...[delta]... requested
                final long delta = currRequested - effectiveSourceRequested;
                assert delta >= 0;
                if (delta != 0) {
                    // There maybe concurrency with the Subscription thread, or synchronous delivery of data from
                    // request(n). In these cases we want to avoid "double request" from requested, so we track how much
                    // we have already requested and decrement it on future loop iterations.
                    effectiveSourceRequested = currRequested;
                    next.request(delta);
                }

                // Make the subscription visible before restoring the state of sourceRequested. If the Subscription
                // thread observes the sourceRequested change it will also observe the subscription change. The
                // Subscription thread also uses sourceRequested to make sure there is no concurrent invocation of
                // the switched Subscription.
                subscription = next;
                // We want to set sourceRequested to currRequested because we have already requested the delta between
                // the two above, and we want to ensure sourceRequested is always monotonically increasing
                // (besides control values) to prevent the Subscription thread from requesting from an old subscription.
                if (sourceRequestedUpdater.compareAndSet(this, SWITCHING, currRequested)) {
                    break;
                }
                // else the Subscription thread was active in the mean time, we need to process the pending event(s).
                // if the state is cancelled the Subscription thread defers to this thread to do the cancel on the
                // next loop invocation.
            }
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
