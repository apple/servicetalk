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

public final class ConcurrentPublisherSubscriberFunction<T> implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    private final List<Subscriber<? super T>> subscribers = new CopyOnWriteArrayList<>();
    private final Subscriber<T> listSubscriber = new Subscriber<T>() {
        @Override
        public void onSubscribe(final Subscription s) {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onSubscribe(s);
            }
        }

        @Override
        public void onNext(final T t) {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onNext(t);
            }
        }

        @Override
        public void onError(final Throwable t) {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onError(t);
            }
        }

        @Override
        public void onComplete() {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onComplete();
            }
        }
    };

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        subscribers.add(subscriber);
        return listSubscriber;
    }

    public List<Subscriber<? super T>> subscribers() {
        return new ArrayList<>(subscribers);
    }
}
