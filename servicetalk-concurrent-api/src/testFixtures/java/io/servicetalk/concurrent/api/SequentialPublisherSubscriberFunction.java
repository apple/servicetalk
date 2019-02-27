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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class SequentialPublisherSubscriberFunction<T> implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    private final AtomicReference<Subscriber<? super T>> subscriberRef = new AtomicReference<>();

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        if (!subscriberRef.compareAndSet(null, subscriber)) {
            throw new IllegalStateException("Duplicate subscriber: " + subscriber);
        }
        return new DelegatingSubscriber<T>(subscriber) {
            @Override
            public void onSubscribe(final Subscription s) {
                super.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        s.request(n);
                    }

                    @Override
                    public void cancel() {
                        subscriberRef.compareAndSet(subscriber, null);
                        s.cancel();
                    }
                });
            }

            @Override
            public void onError(final Throwable t) {
                subscriberRef.compareAndSet(subscriber, null);
                super.onError(t);
            }

            @Override
            public void onComplete() {
                subscriberRef.compareAndSet(subscriber, null);
                super.onComplete();
            }
        };
    }

    @Nullable
    public Subscriber<? super T> subscriber() {
        return subscriberRef.get();
    }
}
