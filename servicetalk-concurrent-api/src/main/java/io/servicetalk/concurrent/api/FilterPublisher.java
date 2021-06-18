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

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.util.Objects.requireNonNull;

/**
 * As returned from {@link Publisher#filter(Predicate)}.
 *
 * @param <T> Type of items emitted by source {@link Publisher}
 */
final class FilterPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Predicate<? super T> predicate;

    FilterPublisher(Publisher<T> source, Predicate<? super T> predicate) {
        super(source);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new Subscriber<T>() {
            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                if (checkDuplicateSubscription(subscription, s)) {
                    subscription = ConcurrentSubscription.wrap(s);
                    subscriber.onSubscribe(subscription);
                }
            }

            @Override
            public void onNext(T t) {
                // If Predicate.test(...) throws we just propagate it to the caller which is responsible to terminate
                // its subscriber and cancel the subscription.
                if (predicate.test(t)) {
                    subscriber.onNext(t);
                } else {
                    assert subscription != null : "Subscription can not be null in onNext.";
                    subscription.request(1); // Since we filtered one item.
                }
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        };
    }
}
