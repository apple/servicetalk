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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Subscriber} that wraps another, and asserts that items are not delivered without sufficient demand.
 *
 * @param <T> Type of items received by the {@code Subscriber}.
 */
public final class DemandCheckingSubscriber<T> implements Subscriber<T> {

    private static final long NO_ON_SUBSCRIBE = Long.MIN_VALUE;
    private final Subscriber<? super T> delegate;

    private final AtomicLong pending = new AtomicLong(NO_ON_SUBSCRIBE);

    /**
     * Create a new {@link DemandCheckingSubscriber} that delegates to {@code delegate}.
     *
     * @param delegate the {@link Subscriber} to delegate to.
     */
    public DemandCheckingSubscriber(final Subscriber<? super T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onSubscribe(final Subscription s) {
        pending.set(0);
        delegate.onSubscribe(new Subscription() {
            @Override
            public void request(final long n) {
                if (n == NO_ON_SUBSCRIBE) {
                    // NO_ON_SUBSCRIBE is special value and not eligible to use because it signals a condition.
                    pending.set(NO_ON_SUBSCRIBE + 1);
                } else {
                    pending.accumulateAndGet(n, FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                }
                s.request(n);
            }

            @Override
            public void cancel() {
                s.cancel();
            }
        });
    }

    @Override
    public void onNext(final T t) {
        long pending = this.pending.getAndAccumulate(-1, FlowControlUtils::addWithOverflowProtectionIfPositive);
        if (pending > 0) {
            delegate.onNext(t);
        } else if (pending == NO_ON_SUBSCRIBE) {
            throw new AssertionError(
                    "Demand check failure: No subscription available to check demand. Ignoring item: " + t);
        } else {
            throw new AssertionError("Demand check failure: Invalid outstanding demand " + pending +
                    ". Ignoring item: " + t);
        }
    }

    @Override
    public void onError(final Throwable t) {
        delegate.onError(t);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }
}
