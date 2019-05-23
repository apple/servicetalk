/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtectionIfNotNegative;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * An implementation of {@link Subscription} that starts as a {@link Cancellable} but then is replaced with an actual
 * {@link Subscription}.
 * It is assumed that the order of methods called on this class is as follows:
 * <ul>
 *     <li>{@link #setCancellable(Cancellable)} is always the first method that is called before any other method.</li>
 *     <li>Zero or more calls to {@link #request(long)} or {@link #cancel()}</li>
 *     <li>Exactly one call to {@link #setSubscription(Subscription)}</li>
 * </ul>
 */
abstract class CancellableThenSubscription implements Subscription {

    private static final Long AWAITING_SUBSCRIPTION_SET = 0L;
    private static final Long INVALID_REQUEST_N = Long.MIN_VALUE;
    private static final Cancellable CANCELLED = () -> { };
    /**
     * Can be the following:
     * <ul>
     *     <li>{@code null}: till {@link #setCancellable(Cancellable)} is called.</li>
     *     <li>{@link Long} till {@link #setSubscription(Subscription)} is called.</li>
     *     <li>{@link Subscription} after {@link #setSubscription(Subscription)} is called.</li>
     *     <li>Stays at {@link CancellableThenSubscription#CANCELLED} after {@link #cancel()} is called.</li>
     * </ul>
     */
    private static final AtomicReferenceFieldUpdater<CancellableThenSubscription, Object> stateUpdater =
            newUpdater(CancellableThenSubscription.class, Object.class, "state");

    @Nullable
    private volatile Object state;
    @Nullable
    private Cancellable firstCancellable;

    @Override
    public void request(final long n) {
        final Object currentState = state;
        if (currentState == CANCELLED ||
                // Pre-existing invalid request-n, should be sent as-is to the original Subscription
                currentState instanceof Long && (long) currentState == INVALID_REQUEST_N) {
            return;
        }

        final boolean shouldAdjustRequestNBy1 = shouldAdjustRequestNBy1();
        final long adjustedN = isRequestNValid(n) ? shouldAdjustRequestNBy1 ? n - 1 : n : INVALID_REQUEST_N;

        if (shouldAdjustRequestNBy1) {
            emitAdjustedItemIfAvailable();
            if (n == 1) {
                // Only 1 item was requested and we emitted (or scheduled to emit) that item, nothing else is required
                // to be done.
                return;
            }
        }

        for (;;) {
            final Object s = state;
            if (s instanceof Subscription) {
                Subscription subscription = (Subscription) s;
                subscription.request(adjustedN);
                break;
            } else if (s == null || s instanceof Long) {
                if (isRequestNValid(n) && s != null) {
                    if (stateUpdater.compareAndSet(this, s,
                            addWithOverflowProtectionIfNotNegative((long) s, adjustedN))) {
                        break;
                    }
                } else if (stateUpdater.compareAndSet(this, s, adjustedN)) {
                    break;
                }
            }
        }
    }

    boolean shouldAdjustRequestNBy1() {
        return false; // Noop by default
    }

    void emitAdjustedItemIfAvailable() {
        // Noop by default
    }

    @Override
    public void cancel() {
        Object oldState = stateUpdater.getAndSet(this, CANCELLED);
        if (oldState instanceof Cancellable) {
            ((Cancellable) oldState).cancel();
        }
        if (oldState == null || oldState instanceof Long) {
            // according to the contract, setCancellable() should happen-before call to any other method.
            assert firstCancellable != null;
            firstCancellable.cancel();
        }
    }

    void setCancellable(Cancellable cancellable) {
        firstCancellable = cancellable;
        if (state == CANCELLED) {
            cancellable.cancel();
        }
    }

    void setSubscription(Subscription subscription) {
        for (;;) {
            final Object s = state;
            if (s instanceof Long) {
                long toRequest = (long) s;
                if (toRequest == AWAITING_SUBSCRIPTION_SET &&
                        stateUpdater.compareAndSet(this, AWAITING_SUBSCRIPTION_SET, subscription)) {
                    break;
                }
                if (stateUpdater.compareAndSet(this, s, AWAITING_SUBSCRIPTION_SET)) {
                    subscription.request(toRequest);
                    if (stateUpdater.compareAndSet(this, AWAITING_SUBSCRIPTION_SET, subscription)) {
                        break;
                    }
                }
            } else if (s == null && stateUpdater.compareAndSet(this, null, subscription)) {
                break;
            } else if (s == CANCELLED) {
                subscription.cancel();
                break;
            }
        }
    }
}
