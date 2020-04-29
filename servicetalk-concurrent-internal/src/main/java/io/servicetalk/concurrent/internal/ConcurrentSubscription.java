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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseReentrantLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireReentrantLock;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * Wraps another {@link Subscription} and guards against multiple calls of {@link Subscription#cancel()}.
 * <p>
 * This class exists to enforce the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.7">Reactive Streams, 2.7
 * </a> rule. It also allows a custom {@link Cancellable} to be used in the event that there maybe multiple cancel
 * operations which are linked, but we still need to prevent concurrent invocation of the {@link Subscription#cancel()}
 * and {@link Subscription#cancel()} methods.
 */
public class ConcurrentSubscription implements Subscription {
    private static final AtomicLongFieldUpdater<ConcurrentSubscription> pendingDemandUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentSubscription.class, "pendingDemand");
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
        if (!isRequestNValid(n)) {
            pendingDemand = mapInvalidRequestN(n);
        } else {
            pendingDemandUpdater.accumulateAndGet(this, n, FlowControlUtils::addWithOverflowProtectionIfNotNegative);
        }
        Throwable delayedCause = null;
        boolean tryAcquire;
        do {
            final long acquireId = tryAcquireReentrantLock(subscriptionLockUpdater, this);
            if (acquireId == 0) {
                break;
            }

            try {
                final long prevPendingDemand = pendingDemandUpdater.getAndSet(this, 0);
                if (prevPendingDemand == CANCELLED) {
                    subscription.cancel();
                } else if (prevPendingDemand != 0) {
                    subscription.request(prevPendingDemand);
                }
            } catch (Throwable cause) {
                delayedCause = catchUnexpected(delayedCause, cause);
            } finally {
                tryAcquire = !releaseReentrantLock(subscriptionLockUpdater, acquireId, this);
            }
        } while (tryAcquire);

        if (delayedCause != null) {
            throwException(delayedCause);
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

    private static long mapInvalidRequestN(long n) {
        // We map zero to a negative number because zero could later be overwritten by a subsequent legit value of
        // n, and we want to ensure the invalid use gets propagated.
        return n == CANCELLED ? CANCELLED + 1 : n == 0 ? -1 : n;
    }
}
