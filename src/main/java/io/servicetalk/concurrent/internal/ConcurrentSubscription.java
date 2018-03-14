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
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Objects.requireNonNull;

/**
 * Wraps another {@link Subscription} and guards against multiple calls of {@link Subscription#cancel()}.
 * <p>
 * This class exists to enforce the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.7">Reactive Streams, 2.7</a> rule.
 * It also allows a custom {@link Cancellable} to be used in the event that there maybe multiple cancel operations which
 * are linked, but we still need to prevent concurrent invocation of the {@link Subscription#cancel()} and
 * {@link Subscription#cancel()} methods.
 * <p>
 * Be aware with invalid input to {@link #request(long)} we don't attempt to enforce concurrency and rely upon the subscription
 * to enforce the specification <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#3.9">3.9</a> rule.
 */
public class ConcurrentSubscription implements Subscription {
    private static final AtomicLongFieldUpdater<ConcurrentSubscription> subscriptionRequestQueueUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentSubscription.class, "subscriptionRequestQueue");
    private static final AtomicReferenceFieldUpdater<ConcurrentSubscription, Thread> subscriptionLockOwnerUpdater =
            AtomicReferenceFieldUpdater.newUpdater(ConcurrentSubscription.class, Thread.class, "subscriptionLockOwner");
    private final Subscription subscription;
    @SuppressWarnings("unused")
    private volatile long subscriptionRequestQueue;
    @Nullable
    private volatile Thread subscriptionLockOwner;

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
        return subscription instanceof ConcurrentSubscription ? (ConcurrentSubscription) subscription : new ConcurrentSubscription(subscription);
    }

    @Override
    public void request(long n) {
        if (!isRequestNValid(n)) {
            // With invalid input we don't attempt to enforce concurrency and rely upon the subscription
            // to enforce the specification rules [1].
            // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#3.9.
            subscription.request(n);
            return;
        }
        final Thread currentThread = Thread.currentThread();
        if (currentThread == subscriptionLockOwner) {
            subscriptionRequestQueueUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
            return;
        }
        do {
            if (!subscriptionLockOwnerUpdater.compareAndSet(this, null, currentThread)) {
                // It is possible that we picked up a negative value from the queue on the previous iteration because
                // we have been cancelled in another thread, and in this case we don't want to increment the queue and instead
                // we just set to MIN_VALUE again and try to re-acquire the lock in case we raced again.
                if (n < 0) {
                    subscriptionRequestQueueUpdater.set(this, Long.MIN_VALUE);
                } else {
                    subscriptionRequestQueueUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
                }
                if (!subscriptionLockOwnerUpdater.compareAndSet(this, null, currentThread)) {
                    return;
                }
                // We previously added our n contribution to the queue, but now that we have acquired the lock
                // we are responsible for draining the queue.
                n = subscriptionRequestQueueUpdater.getAndSet(this, 0);
                if (n == 0) {
                    // It is possible that the previous consumer has released the lock, and drained the queue before we
                    // acquired the lock and drained the queue. This means we have acquired the lock, but the queue has
                    // already been drained to 0. We should release the lock, try to drain the queue again, and then loop
                    // to acquire the lock if there are elements to drain.
                    subscriptionLockOwner = null;
                    n = subscriptionRequestQueueUpdater.getAndSet(this, 0);
                    if (n == 0) {
                        return;
                    } else {
                        continue;
                    }
                }
            }
            if (n < 0) {
                subscription.cancel();
                return; // Don't set subscriptionLockOwner = 0 ... we don't want to request any more!
            }
            try {
                subscription.request(n);
            } finally {
                subscriptionLockOwner = null;
            }
            n = subscriptionRequestQueueUpdater.getAndSet(this, 0);
        } while (n != 0);
    }

    @Override
    public void cancel() {
        // Set the queue to MIN_VALUE and this will be detected in request(n).
        // We unconditionally set this value just in case there is re-entry with request(n) we will avoid calling
        // the subscription's request(n) after cancel().
        subscriptionRequestQueueUpdater.set(this, Long.MIN_VALUE);

        final Thread currentThread = Thread.currentThread();
        final Thread subscriptionLockOwner = this.subscriptionLockOwner;
        if (subscriptionLockOwner == currentThread || subscriptionLockOwnerUpdater.compareAndSet(this, null, currentThread)) {
            subscription.cancel();
        }
    }
}
