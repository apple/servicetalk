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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Allows multiple {@link Subscriber}s to be concurrently subscribed to a {@link TestSingle}, and multicasts signals
 * to them all.
 *
 * @param <T> Type of items received by the {@code Subscriber}.
 */
public final class ConcurrentSingleSubscriberFunction<T>
        implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    private final List<Subscriber<? super T>> subscribers = new CopyOnWriteArrayList<>();
    private final Subscriber<T> listSubscriber = new Subscriber<T>() {
        @Override
        public void onSubscribe(final Cancellable c) {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onSubscribe(c);
            }
        }

        @Override
        public void onSuccess(final T result) {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onSuccess(result);
            }
        }

        @Override
        public void onError(final Throwable t) {
            for (final Subscriber<? super T> subscriber : subscribers) {
                subscriber.onError(t);
            }
        }
    };

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        subscribers.add(subscriber);
        return listSubscriber;
    }

    /**
     * Returns a list of all {@link Subscriber}s that have subscribed.
     *
     * @return a list of all {@link Subscriber}s that have subscribed.
     */
    public List<Subscriber<? super T>> subscribers() {
        return new ArrayList<>(subscribers);
    }
}
