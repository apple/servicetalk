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

final class AfterSubscriberPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Supplier<? extends Subscriber<? super T>> subscriberSupplier;

    AfterSubscriberPublisher(Publisher<T> original, Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        super(original);
        this.subscriberSupplier = requireNonNull(subscriberSupplier);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new AfterSubscriberPublisherSubscriber<>(subscriber, subscriberSupplier.get());
    }

    private static final class AfterSubscriberPublisherSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Subscriber<? super T> subscriber;

        AfterSubscriberPublisherSubscriber(Subscriber<? super T> original, Subscriber<? super T> subscriber) {
            this.original = original;
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(Subscription s) {
            original.onSubscribe(s);
            // If this throws we expect the source to bail on this Subscriber.
            subscriber.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
            subscriber.onNext(t);
        }

        @Override
        public void onComplete() {
            try {
                original.onComplete();
            } finally {
                subscriber.onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            try {
                original.onError(t);
            } finally {
                subscriber.onError(t);
            }
        }
    }
}
