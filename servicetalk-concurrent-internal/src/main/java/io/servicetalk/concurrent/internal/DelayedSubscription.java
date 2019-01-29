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

import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ConcurrentSubscription.wrap;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.lang.Long.MIN_VALUE;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link Subscription} which serves as a placeholder until the "real" {@link Subscription} is available.
 */
public final class DelayedSubscription implements Subscription {
    private static final AtomicReferenceFieldUpdater<DelayedSubscription, Subscription> currentUpdater =
            newUpdater(DelayedSubscription.class, Subscription.class, "current");
    private static final AtomicLongFieldUpdater<DelayedSubscription> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(DelayedSubscription.class, "requested");

    @SuppressWarnings("unused")
    @Nullable
    private volatile Subscription current;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile long requested;

    /**
     * Set the delayed {@link Subscription}. This method can only be called a single time and
     * subsequent calls will result in {@link #cancel()} being called on {@code delayedSubscription}.
     * @param delayedSubscription The delayed {@link Subscription}.
     */
    public void setDelayedSubscription(Subscription delayedSubscription) {
        // Temporarily wrap in a ConcurrentSubscription to prevent concurrent invocation between this thread and
        // a thread which may be interacting with this class's Subscription API.
        final Subscription concurrentSubscription = wrap(delayedSubscription);
        if (!currentUpdater.compareAndSet(this, null, concurrentSubscription)) {
            delayedSubscription.cancel();
        } else {
            tryDrainRequested(concurrentSubscription);

            // Unwrap the concurrent subscription because there will be no more concurrency.
            currentUpdater.compareAndSet(this, concurrentSubscription, delayedSubscription);
        }
    }

    @Override
    public void request(long n) {
        Subscription current = this.current;
        if (current != null) {
            current.request(n);
        } else {
            if (isRequestNValid(n)) {
                requestedUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
            } else {
                // Although 0 is invalid we use it to signify that we have drained the pending request count,
                // so in this case we use MIN_VALUE so we can still pass through an invalid number only once.
                requested = n == 0 ? MIN_VALUE : n;
            }
            current = this.current;
            if (current != null) {
                tryDrainRequested(current);
            }
        }
    }

    @Override
    public void cancel() {
        Subscription oldSubscription = currentUpdater.getAndSet(this, EMPTY_SUBSCRIPTION);
        if (oldSubscription != null) {
            oldSubscription.cancel();
        }
    }

    private void tryDrainRequested(Subscription current) {
        long pendingRequested = requestedUpdater.getAndSet(this, 0);
        // We also want to pass through invalid input to the current Subscription, so anything non-zero should
        // go through.
        if (pendingRequested != 0) {
            current.request(pendingRequested);
        }
    }
}
