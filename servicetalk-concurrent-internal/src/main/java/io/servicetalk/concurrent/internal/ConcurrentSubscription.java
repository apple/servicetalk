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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseReentrantLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireReentrantLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Objects.requireNonNull;

/**
 * This class prevents concurrent invocation of {@link Subscription} methods and preserves the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2.7">Reactive Streams, 2.7
 * </a> rule when there is a possibility of concurrency.
 */
public class ConcurrentSubscription implements Subscription {
    private static final AtomicLongFieldUpdater<ConcurrentSubscription> pendingDemandUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentSubscription.class, "pendingDemand");
    /**
     * Typically usage of this lock follows the acquire/try/finally{release} usage pattern. However in this case we do
     * not follow this pattern for the following reasons:
     * <ol>
     *     <li>If {@link #subscription} throws, the associated asynchronous source is considered invalid and should be
     *     cleaned up externally relative to this {@link ConcurrentSubscription}.</li>
     *     <li>If the previous item is true, then it is OK to poison the lock if {@link #subscription} throws, because
     *     it is considered invalid and no further interaction is required for clean (it is done externally).</li>
     * </ol>
     */
    private static final AtomicLongFieldUpdater<ConcurrentSubscription> subscriptionLockUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentSubscription.class, "subscriptionLock");
    private static final long CANCELLED = Long.MIN_VALUE;

    private final Subscription subscription;
    private volatile long pendingDemand;
    @SuppressWarnings("unused")
    private volatile long subscriptionLock;

    /**
     * New instance.
     * @param subscription {@link Subscription} to wrap.
     */
    protected ConcurrentSubscription(Subscription subscription) {
        this.subscription = requireNonNull(subscription);
    }

    /**
     * Wrap a {@link Subscription} to make it thread safe when concurrent access may exists between
     * {@link Subscription#request(long)} and {@link Subscription#cancel()}.
     * @param subscription The subscription to wrap.
     * @return A {@link Subscription} that will enforce the threading constraints in a concurrent environment.
     */
    public static ConcurrentSubscription wrap(Subscription subscription) {
        return subscription instanceof ConcurrentSubscription ? (ConcurrentSubscription) subscription :
                new ConcurrentSubscription(subscription);
    }

    @Override
    public void request(long n) {
        final long acquireId = tryAcquireReentrantLock(subscriptionLockUpdater, this);
        if (acquireId != 0) { // fast path (no concurrency) just deliver demand without adding to pending.
            subscription.request(n);
            if (!releaseReentrantLock(subscriptionLockUpdater, acquireId, this)) {
                // if we failed to release the lock there was concurrent invocation, and we need to drain.
                drainPending();
            }
        } else { // slow path (concurrency detected) add pending demand and try to re-acquire lock and process demand.
            addPending(n);
            drainPending();
        }
    }

    @Override
    public void cancel() {
        pendingDemand = CANCELLED;
        if (tryAcquireReentrantLock(subscriptionLockUpdater, this) != 0) {
            subscription.cancel();
            // poison subscriptionLockUpdater
        }
    }

    private void addPending(long n) {
        if (!isRequestNValid(n)) {
            pendingDemand = mapInvalidRequestN(n);
        } else {
            pendingDemandUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtectionIfNotNegative);
        }
    }

    private void drainPending() {
        long acquireId;
        do {
            acquireId = tryAcquireReentrantLock(subscriptionLockUpdater, this);
            if (acquireId == 0) {
                break;
            }
            final long prevPendingDemand = pendingDemandUpdater.getAndSet(this, 0);
            if (prevPendingDemand == CANCELLED) {
                subscription.cancel();
            } else if (prevPendingDemand != 0) {
                subscription.request(prevPendingDemand);
            }
        } while (!releaseReentrantLock(subscriptionLockUpdater, acquireId, this));
    }

    private static long mapInvalidRequestN(long n) {
        // We map zero to a negative number because zero could later be overwritten by a subsequent legit value of
        // n, and we want to ensure the invalid use gets propagated.
        return n == CANCELLED ? CANCELLED + 1 : n == 0 ? -1 : n;
    }
}
