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

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
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
    public void request(long n) {
        for (;;) {
            final Object s = state;
            if (s instanceof Subscription) {
                ((Subscription) s).request(n);
                break;
            } else if (s == null || !isRequestNValid(n)) {
                if (stateUpdater.compareAndSet(this, s, n)) {
                    break;
                }
            } else if (s instanceof Long) {
                final long longS = (long) s;
                if (longS < 0 || stateUpdater.compareAndSet(this, s, addWithOverflowProtection(longS, n))) {
                    break;
                }
            } else {
                assert s == CANCELLED;
                break;
            }
        }
    }

    @Override
    public void cancel() {
        Object oldState = stateUpdater.getAndSet(this, CANCELLED);
        if (oldState instanceof Cancellable) {
            ((Cancellable) oldState).cancel();
        } else if (oldState != CANCELLED && firstCancellable != null) {
            firstCancellable.cancel();
            firstCancellable = null;
        }
    }

    final void setCancellable(Cancellable cancellable) {
        firstCancellable = cancellable;
    }

    final void setSubscription(Subscription subscription) {
        // Make a best effort to null firstCancellable. We no longer need to interact with it at this point because the
        // action associated with the firstCancellable has completed when this method is called. If we do cancel that is
        // still permitted by the spec [1].
        // [1] https://github.com/reactive-streams/reactive-streams-jvm#3.5
        firstCancellable = null;
        for (;;) {
            final Object s = state;
            if (s instanceof Long) {
                final long toRequest = (long) s;
                // we must reset to 0 before we try to request any data. this ensures that we don't double count
                // any n value that is added in request(n) above from another thread.
                if (stateUpdater.compareAndSet(this, s, 0L)) {
                    subscription.request(toRequest);
                    if (stateUpdater.compareAndSet(this, 0L, subscription)) {
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
