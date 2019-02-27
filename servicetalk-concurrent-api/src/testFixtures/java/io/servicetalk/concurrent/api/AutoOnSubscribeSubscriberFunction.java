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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class AutoOnSubscribeSubscriberFunction<T> implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        final SequentialSubscription subscription = new SequentialSubscription();
        subscriptions.add(subscription);
        subscriber.onSubscribe(subscription);
        return new DelegatingSubscriber<T>(subscriber) {
            @Override
            public void onSubscribe(final Subscription s) {
                subscription.switchTo(s);
            }

            @Override
            public void onNext(final T t) {
                subscription.itemReceived();
                super.onNext(t);
            }
        };
    }

    public List<Subscription> subscriptions() {
        return new ArrayList<>(subscriptions);
    }
}
