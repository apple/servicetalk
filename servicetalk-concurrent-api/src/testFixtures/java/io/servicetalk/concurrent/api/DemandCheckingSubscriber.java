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
import io.servicetalk.concurrent.internal.FlowControlUtil;

import java.util.concurrent.atomic.AtomicLong;

public final class DemandCheckingSubscriber<T> implements Subscriber<T> {

    private static final int NO_ON_SUBSCRIBE = -1;
    private static final int CANCELLED = -2;
    private final Subscriber<? super T> delegate;

    private final AtomicLong pending = new AtomicLong(NO_ON_SUBSCRIBE);

    public DemandCheckingSubscriber(final Subscriber<? super T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onSubscribe(final Subscription s) {
        pending.set(0);
        delegate.onSubscribe(new Subscription() {
            @Override
            public void request(final long n) {
                pending.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
                s.request(n);
            }

            @Override
            public void cancel() {
                pending.set(CANCELLED);
                s.cancel();
            }
        });
    }

    @Override
    public void onNext(final T t) {
        long pending = this.pending.getAndAccumulate(-1, FlowControlUtil::addWithOverflowProtectionIfPositive);
        if (pending > 0) {
            delegate.onNext(t);
        } else if (pending == NO_ON_SUBSCRIBE) {
            throw new AssertionError(
                    "Demand check failure: No subscription available to check demand. Ignoring item: " + t);
        } else if (pending == CANCELLED) {
            throw new AssertionError("Demand check failure: Subscription is cancelled. Ignoring item: " + t);
        } else {
            assert pending == 0;
            throw new AssertionError("Demand check failure: No outstanding demand. Ignoring item: " + t);
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
