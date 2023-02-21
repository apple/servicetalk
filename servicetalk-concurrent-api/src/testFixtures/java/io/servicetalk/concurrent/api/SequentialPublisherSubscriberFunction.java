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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Allows multiple {@link Subscriber}s to be sequentially subscribed to a {@link TestPublisher}. Attempts to subscribe
 * concurrently will throw an exception.
 *
 * @param <T> Type of items received by the {@code Subscriber}.
 */
public final class SequentialPublisherSubscriberFunction<T>
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
        return new DelegatingPublisherSubscriber<T>(subscriber) {
            @Override
            public void onSubscribe(final Subscription s) {
                super.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        s.request(n);
                    }

                    @Override
                    public void cancel() {
                        reset(subscriber);
                        s.cancel();
                    }
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

            private void reset(final Subscriber<? super T> subscriber) {
                if (SequentialPublisherSubscriberFunction.this.subscriber == subscriber) {
                    SequentialPublisherSubscriberFunction.this.subscriber = null;
                    subscribed.set(false);
                }
            }
        };
    }

    /**
     * Returns the currently subscribed {@link Subscriber}.
     *
     * @return the currently subscribed {@link Subscriber}.
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
