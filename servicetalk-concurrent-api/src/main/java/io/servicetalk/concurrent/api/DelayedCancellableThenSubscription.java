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
    private final DelayedSubscription delayedSubscription = new DelayedSubscription();
    private static final AtomicReferenceFieldUpdater<DelayedCancellableThenSubscription, Cancellable> currentUpdater =
            newUpdater(DelayedCancellableThenSubscription.class, Cancellable.class, "current");
    @Nullable
    private volatile Cancellable current;

    @Override
    public void request(final long n) {
        delayedSubscription.request(n);
    }

    @Override
    public void cancel() {
        try {
            Cancellable oldCancellable = currentUpdater.getAndSet(this, IGNORE_CANCEL);
            if (oldCancellable != null) {
                oldCancellable.cancel();
            }
        } finally {
            delayedSubscription.cancel();
        }
    }

    final void delayedCancellable(Cancellable delayedCancellable) {
        if (!currentUpdater.compareAndSet(this, null, requireNonNull(delayedCancellable))) {
            delayedCancellable.cancel();
        }
    }

    final void delayedSubscription(Subscription subscription) {
        // The operation corresponding to the first cancellable is considered done, so we dereference the Cancellable.
        // Best effort is OK as subsequent calls to cancel should be NOOPs [1][2].
        // [1] https://github.com/reactive-streams/reactive-streams-jvm#2.4
        // [2] https://github.com/reactive-streams/reactive-streams-jvm#3.7
        current = IGNORE_CANCEL;
        delayedSubscription.delayedSubscription(subscription);
    }
}
