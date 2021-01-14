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

import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscriptions.newEmptySubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.util.Objects.requireNonNull;

final class TakeWhilePublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private static final Subscription CANCELLED = newEmptySubscription(true);

    private final Predicate<? super T> predicate;

    TakeWhilePublisher(Publisher<T> original, Predicate<? super T> predicate, Executor executor) {
        super(original, executor);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new TakeWhileSubscriber<>(subscriber, predicate);
    }

    private static final class TakeWhileSubscriber<T> implements Subscriber<T> {

        private final Subscriber<? super T> subscriber;
        private final Predicate<? super T> predicate;

        @Nullable
        private Subscription subscription;

        TakeWhileSubscriber(Subscriber<? super T> subscriber, Predicate<? super T> predicate) {
            this.subscriber = subscriber;
            this.predicate = predicate;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (checkDuplicateSubscription(subscription, s)) {
                subscription = ConcurrentSubscription.wrap(s);
                subscriber.onSubscribe(subscription);
            }
        }

        @Override
        public void onNext(T t) {
            Subscription subscription = this.subscription;
            assert subscription != null : "Subscription can not be null.";

            if (subscription == CANCELLED) {
                return;
            }
            // If Predicate.test(...) throws we just propagate it to the caller which is responsible to terminate
            // its subscriber and cancel the subscription.
            if (!predicate.test(t)) {
                this.subscription = CANCELLED;
                // This is guarded in our Subscription implementation with a CAS operation as we use
                // ConcurrentSubscription.
                subscription.cancel();
                subscriber.onComplete();
            } else {
                subscriber.onNext(t);
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
