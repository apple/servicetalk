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
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static java.lang.Long.MIN_VALUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * A {@link Subscription} which serves as a placeholder until the "real" {@link Subscription} is available.
 */
public final class DelayedSubscription implements Subscription {
    private static final long SUBSCRIPTION_SETTING = MIN_VALUE;
    private static final long SUBSCRIPTION_SET = MIN_VALUE + 1;
    private static final long SUBSCRIPTION_CANCEL_PENDING = MIN_VALUE + 2;
    private static final long GREATEST_CONTROL_VALUE = SUBSCRIPTION_CANCEL_PENDING;
    private static final AtomicLongFieldUpdater<DelayedSubscription> requestedUpdater =
            newUpdater(DelayedSubscription.class, "requested");

    @Nullable
    private Subscription subscription;
    private volatile long requested;

    /**
     * Set the delayed {@link Subscription}. This method can only be called a single time and
     * subsequent calls will result in {@link #cancel()} being called on {@code delayedSubscription}.
     *
     * @param delayedSubscription The delayed {@link Subscription}.
     */
    public void delayedSubscription(Subscription delayedSubscription) {
        requireNonNull(delayedSubscription);
        for (;;) {
            final long prevRequested = requested;
            if (prevRequested <= GREATEST_CONTROL_VALUE) {
                delayedSubscription.cancel();
                break;
            } else if (requestedUpdater.compareAndSet(this, prevRequested, SUBSCRIPTION_SETTING)) {
                if (prevRequested != 0) {
                    delayedSubscription.request(prevRequested);
                }
                // Set the subscription before CAS to make it visible to the thread interacting with the Subscription.
                // The Subscription thread won't use the state unless the CAS to SUBSCRIPTION_SET is successful, so
                // there will be no concurrency introduced by this operation.
                subscription = delayedSubscription;
                if (requestedUpdater.compareAndSet(this, SUBSCRIPTION_SETTING, SUBSCRIPTION_SET)) {
                    break;
                }
            }
        }
    }

    @Override
    public void request(long n) {
        for (;;) {
            final long prevRequested = requested;
            if (prevRequested == SUBSCRIPTION_SET) {
                assert subscription != null;
                subscription.request(n);
                break;
            } else if (prevRequested == SUBSCRIPTION_SETTING) {
                if (requestedUpdater.compareAndSet(this, SUBSCRIPTION_SETTING, addRequestN(0, n))) {
                    break;
                }
            } else if (prevRequested < 0 ||
                    requestedUpdater.compareAndSet(this, prevRequested, addRequestN(prevRequested, n))) {
                // prevRequested < 0 covers the following cases:
                //  SUBSCRIPTION_CANCEL_PENDING - delayedSubscription(..) is responsible for propagating cancel()
                //  prior invalid request(n) - this value should be preserved
                break;
            }
        }
    }

    @Override
    public void cancel() {
        for (;;) {
            final long prevRequested = requested;
            if (prevRequested == SUBSCRIPTION_SET) {
                assert subscription != null;
                subscription.cancel();
                break;
            } else if (prevRequested == SUBSCRIPTION_SETTING) {
                if (requestedUpdater.compareAndSet(this, SUBSCRIPTION_SETTING, SUBSCRIPTION_CANCEL_PENDING)) {
                    break;
                }
            } else if (prevRequested < 0 ||
                    requestedUpdater.compareAndSet(this, prevRequested, SUBSCRIPTION_CANCEL_PENDING)) {
                // prevRequested < 0 covers the following cases:
                //  SUBSCRIPTION_CANCEL_PENDING - delayedSubscription(..) is responsible for propagating cancel()
                //  prior invalid request(n) - this value should be preserved
                break;
            }
        }
    }

    private static long addRequestN(long prevRequested, long n) {
        return n <= GREATEST_CONTROL_VALUE || n == 0 ? -1 :
                n < 0 ? n : addWithOverflowProtection(prevRequested, n);
    }
}
