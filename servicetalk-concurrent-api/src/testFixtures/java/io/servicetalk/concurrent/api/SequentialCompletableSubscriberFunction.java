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
import io.servicetalk.concurrent.CompletableSource.Subscriber;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Allows multiple {@link Subscriber}s to be sequentially subscribed to a {@link TestCompletable}. Attempts to
 * subscribe concurrently will throw an exception.
 */
public final class SequentialCompletableSubscriberFunction
        implements Function<Subscriber, Subscriber> {

    private final AtomicBoolean subscribed = new AtomicBoolean();
    @Nullable
    private volatile Subscriber subscriber;

    @Override
    public Subscriber apply(final Subscriber subscriber) {
        if (!this.subscribed.compareAndSet(false, true)) {
            throw new IllegalStateException("Duplicate subscriber: " + subscriber);
        }
        this.subscriber = subscriber;
        return new DelegatingCompletableSubscriber(subscriber) {
            @Override
            public void onSubscribe(final Cancellable s) {
                super.onSubscribe(() -> {
                    reset(subscriber);
                    s.cancel();
                });
            }

            @Override
            public void onError(final Throwable t) {
                reset(subscriber);
                super.onError(t);
            }

            @Override
            public void onComplete() {
                reset(subscriber);
                super.onComplete();
            }

            private void reset(final Subscriber subscriber) {
                if (SequentialCompletableSubscriberFunction.this.subscriber == subscriber) {
                    subscribed.set(false);
                }
            }
        };
    }

    /**
     * Returns the most recently subscribed {@link Subscriber}.
     *
     * @return the most recently subscribed {@link Subscriber}.
     */
    @Nullable
    public Subscriber subscriber() {
        return subscriber;
    }

    /**
     * Returns {@code true} if a {@link Subscriber} is currently active (has been subscribed, and not terminated), or
     * {@code false} otherwise.
     *
     * @return {@code true} if a {@link Subscriber} is currently active (has been subscribed, and not terminated), or
     * {@code false} otherwise.
     */
    public boolean isSubscribed() {
        return subscribed.get();
    }
}
