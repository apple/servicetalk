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
import io.servicetalk.concurrent.SingleSource.Subscriber;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Allows multiple {@link Subscriber}s to be sequentially subscribed to a {@link TestSingle2}. Attempts to subscribe
 * concurrently will throw an exception.
 *
 * @param <T> Type of the result of this {@code Subscriber}.
 */
public final class SequentialSingleSubscriberFunction<T>
        implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    private final AtomicBoolean subscribed = new AtomicBoolean();
    @Nullable
    private volatile Subscriber<? super T> subscriber;

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        if (!this.subscribed.compareAndSet(false, true)) {
            throw new IllegalStateException("Duplicate subscriber: " + subscriber);
        }
        this.subscriber = subscriber;
        return new DelegatingSingleSubscriber<T>(subscriber) {
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
            public void onSuccess(T result) {
                reset(subscriber);
                super.onSuccess(result);
            }

            private void reset(final Subscriber<? super T> subscriber) {
                if (SequentialSingleSubscriberFunction.this.subscriber == subscriber) {
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
    public Subscriber<? super T> subscriber() {
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
