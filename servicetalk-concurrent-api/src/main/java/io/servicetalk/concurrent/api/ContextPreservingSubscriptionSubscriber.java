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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscriptionSubscriber<T> implements Subscriber<T> {
    private final AsyncContextMap saved;
    private final Subscriber<? super T> subscriber;

    private ContextPreservingSubscriptionSubscriber(Subscriber<? super T> subscriber, AsyncContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    static <T> Subscriber<T> wrap(Subscriber<T> subscriber, AsyncContextMap current) {
        // Manual calls to subscribe in operators (e.g. Splice operator) may generate duplicate wrapping.
        return (subscriber instanceof ContextPreservingSubscriptionSubscriber &&
                ((ContextPreservingSubscriptionSubscriber) subscriber).saved == current) ? subscriber :
                new ContextPreservingSubscriptionSubscriber<>(subscriber, current);
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
}
