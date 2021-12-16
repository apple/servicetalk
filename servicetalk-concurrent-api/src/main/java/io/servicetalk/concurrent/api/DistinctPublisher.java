/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.util.Objects.requireNonNull;

final class DistinctPublisher<T, K> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Function<? super T, K> keySelector;
    private final Supplier<? extends Collection<? super K>> collectionSupplier;

    DistinctPublisher(final Publisher<T> source, final Function<? super T, K> keySelector,
                      final Supplier<? extends Collection<? super K>> collectionSupplier) {
        super(source);
        this.keySelector = keySelector;
        this.collectionSupplier = collectionSupplier;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new Subscriber<T>() {
            @Nullable
            private Subscription subscription;
            private final Collection<? super K> collection = requireNonNull(collectionSupplier.get());

            @Override
            public void onSubscribe(Subscription s) {
                if (checkDuplicateSubscription(subscription, s)) {
                    subscription = ConcurrentSubscription.wrap(s);
                    subscriber.onSubscribe(subscription);
                }
            }

            @Override
            public void onNext(T t) {
                // If collection or keySelector throws we propagate to the caller which is responsible to terminate
                // its subscriber and cancel the subscription.
                if (collection.add(keySelector.apply(t))) {
                    subscriber.onNext(t);
                } else {
                    assert subscription != null;
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
