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

final class WhenSubscriptionPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {

    private final Supplier<? extends Subscription> subscriptionSupplier;
    private final boolean before;

    WhenSubscriptionPublisher(Publisher<T> original, Supplier<? extends Subscription> subscriptionSupplier,
                              boolean before) {
        super(original);
        this.subscriptionSupplier = requireNonNull(subscriptionSupplier);
        this.before = before;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new DoSubscriptionPublisherSubscriber<>(subscriber, this);
    }

    private static final class DoSubscriptionPublisherSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final WhenSubscriptionPublisher<T> parent;

        DoSubscriptionPublisherSubscriber(Subscriber<? super T> original, WhenSubscriptionPublisher<T> parent) {
            this.original = original;
            this.parent = parent;
        }

        @Override
        public void onSubscribe(Subscription s) {
            Subscription subscription = parent.subscriptionSupplier.get();
            original.onSubscribe(parent.before ? new BeforeSubscription(subscription, s) :
                    new BeforeSubscription(s, subscription));
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
        }

        @Override
        public void onComplete() {
            original.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            original.onError(t);
        }

        private static final class BeforeSubscription implements Subscription {

            private final Subscription first;
            private final Subscription second;

            BeforeSubscription(Subscription first, Subscription second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public void request(long n) {
                try {
                    first.request(n);
                } finally {
                    second.request(n);
                }
            }

            @Override
            public void cancel() {
                try {
                    first.cancel();
                } finally {
                    second.cancel();
                }
            }
        }
    }
}
