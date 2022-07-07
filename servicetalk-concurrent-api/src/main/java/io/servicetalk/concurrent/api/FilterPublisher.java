/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.util.Objects.requireNonNull;

final class FilterPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Supplier<? extends Predicate<? super T>> filterSupplier;

    FilterPublisher(final Publisher<T> source, final Supplier<? extends Predicate<? super T>> filterSupplier) {
        super(source);
        this.filterSupplier = filterSupplier;
    }

    static <T> Supplier<? extends Predicate<? super T>> newDistinctSupplier() {
        return () -> new Predicate<T>() {
            private final Set<T> set = new HashSet<>();
            @Override
            public boolean test(final T t) {
                return set.add(t);
            }
        };
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new Subscriber<T>() {
            @Nullable
            private Subscription subscription;
            private final Predicate<? super T> predicate = requireNonNull(filterSupplier.get(),
                    () -> "Supplier " + filterSupplier + " returned null");

            @Override
            public void onSubscribe(Subscription s) {
                if (checkDuplicateSubscription(subscription, s)) {
                    subscription = ConcurrentSubscription.wrap(s);
                    subscriber.onSubscribe(subscription);
                }
            }

            @Override
            public void onNext(T t) {
                // If predicate throws we propagate to the caller which is responsible to terminate its subscriber and
                // cancel the subscription.
                if (predicate.test(t)) {
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
