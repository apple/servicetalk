/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.DelayedSubscription;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * An implementation of {@link Subscription} that starts as a {@link Cancellable} but then is replaced with an actual
 * {@link Subscription}. The expected sequence of events is:
 * <ul>
 *     <li>{@link #delayedCancellable(Cancellable)}</li>
 *     <li>{@link #delayedSubscription(Subscription)}</li>
 * </ul>
 * The {@link Subscription} methods can be invoked at any time.
 */
class DelayedCancellableThenSubscription implements Subscription {
    private static final Cancellable CANCELLED = () -> { };
    private static final AtomicReferenceFieldUpdater<DelayedCancellableThenSubscription, Cancellable> currentUpdater =
            newUpdater(DelayedCancellableThenSubscription.class, Cancellable.class, "current");

    private final DelayedSubscription delayedSubscription = new DelayedSubscription();
    @Nullable
    private volatile Cancellable current;

    @Override
    public void request(final long n) {
        delayedSubscription.request(n);
    }

    @Override
    public void cancel() {
        Cancellable oldCancellable = currentUpdater.getAndSet(this, CANCELLED);
        if (oldCancellable != null) {
            oldCancellable.cancel();
        }
    }

    final void delayedCancellable(Cancellable delayedCancellable) {
        if (!currentUpdater.compareAndSet(this, null, requireNonNull(delayedCancellable))) {
            delayedCancellable.cancel();
        }
    }

    final void delayedSubscription(Subscription subscription) {
        if (currentUpdater.getAndAccumulate(this, delayedSubscription,
                (prev, next) -> prev == CANCELLED ? CANCELLED : next) == CANCELLED) {
            subscription.cancel();
        } else {
            delayedSubscription.delayedSubscription(subscription);
        }
    }
}
