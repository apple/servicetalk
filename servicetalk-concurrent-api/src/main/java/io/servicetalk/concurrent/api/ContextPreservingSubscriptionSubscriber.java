/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscriptionSubscriber<T> implements Subscriber<T> {
    final ContextMap saved;
    final Subscriber<T> subscriber;

    ContextPreservingSubscriptionSubscriber(Subscriber<T> subscriber, ContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        subscriber.onSubscribe(ContextPreservingSubscription.wrap(subscription, saved));
    }

    @Override
    public void onNext(final T t) {
        subscriber.onNext(t);
    }

    @Override
    public void onError(final Throwable throwable) {
        subscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
