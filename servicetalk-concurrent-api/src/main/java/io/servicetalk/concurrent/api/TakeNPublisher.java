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

import io.servicetalk.concurrent.internal.ConcurrentSubscription;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscriptions.newEmptySubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;

/**
 * {@link Publisher} that will request a fixed number of elements.
 *
 * @param <T> the type of the elements.
 */
final class TakeNPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private static final Subscription CANCELLED = newEmptySubscription();

    private final long numElements;

    TakeNPublisher(Publisher<T> original, long numElements, Executor executor) {
        super(original, executor);
        if (numElements <= 0) {
            throw new IllegalArgumentException("numElements: " + numElements + " (expected >= 0)");
        }
        this.numElements = numElements;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new TakeNSubscriber<>(subscriber, numElements);
    }

    private static final class TakeNSubscriber<T> implements Subscriber<T> {

        private final Subscriber<? super T> subscriber;
        private final long numElements;

        @Nullable
        private Subscription subscription;
        private long receivedElements;

        TakeNSubscriber(Subscriber<? super T> subscriber, long numElements) {
            this.subscriber = subscriber;
            this.numElements = numElements;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (!checkDuplicateSubscription(subscription, s)) {
                return;
            }

            subscription = new ConcurrentSubscription(s) {
                private long num = numElements;

                @Override
                public void request(long n) {
                    if (isRequestNValid(n)) {
                        if (num == 0) {
                            return;
                        }
                        if (n >= num) {
                            n = num;
                            num = 0;
                        } else {
                            num -= n;
                        }
                    }
                    super.request(n);
                }
            };
            subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(T t) {
            Subscription subscription = this.subscription;
            assert subscription != null : "Subscription can not be null.";
            subscriber.onNext(t);

            // Also guard against overflow as numElements may be Long.MAX_VALUE
            if (++receivedElements >= numElements || receivedElements <= 0) {
                this.subscription = CANCELLED;

                // This is guarded in our Subscription implementation with a CAS operation as we extend
                // ConcurrentSubscription.
                subscription.cancel();
                subscriber.onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (subscription != CANCELLED) {
                subscriber.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (subscription != CANCELLED) {
                subscriber.onComplete();
            }
        }
    }
}
