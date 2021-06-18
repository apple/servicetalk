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
package io.servicetalk.concurrent.api;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class BeforeSubscriberPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Supplier<? extends Subscriber<? super T>> subscriberSupplier;

    BeforeSubscriberPublisher(Publisher<T> original, Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        super(original);
        this.subscriberSupplier = requireNonNull(subscriberSupplier);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new BeforeSubscriberPublisherSubscriber<>(subscriber, subscriberSupplier.get());
    }

    private static final class BeforeSubscriberPublisherSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Subscriber<? super T> subscriber;

        BeforeSubscriberPublisherSubscriber(Subscriber<? super T> original, Subscriber<? super T> subscriber) {
            this.original = original;
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(Subscription s) {
            // If this throws we expect the source to bail on this Subscriber.
            subscriber.onSubscribe(s);
            original.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            subscriber.onNext(t);
            original.onNext(t);
        }

        @Override
        public void onComplete() {
            try {
                subscriber.onComplete();
            } catch (Throwable cause) {
                original.onError(cause);
                return;
            }
            original.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            try {
                subscriber.onError(t);
            } catch (Throwable cause) {
                t.addSuppressed(cause);
                original.onError(t);
                return;
            }
            original.onError(t);
        }
    }
}
