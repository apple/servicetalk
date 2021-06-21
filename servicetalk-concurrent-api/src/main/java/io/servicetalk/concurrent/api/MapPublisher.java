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

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * As returned from {@link Publisher#map(Function)}.
 *
 * @param <R> Type of items emitted by this {@link Publisher}
 * @param <T> Type of items emitted by source {@link Publisher}
 */
final class MapPublisher<R, T> extends AbstractSynchronousPublisherOperator<T, R> {
    private final Function<? super T, ? extends R> mapper;

    MapPublisher(Publisher<T> source, Function<? super T, ? extends R> mapper) {
        super(source);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super R> originalSubscriber) {
        return new MapSubscriber<>(originalSubscriber, mapper);
    }

    private static final class MapSubscriber<T, R> implements Subscriber<T> {

        private final Subscriber<? super R> subscriber;
        private final Function<T, R> mapper;

        MapSubscriber(Subscriber<? super R> subscriber, Function<T, R> mapper) {
            this.subscriber = requireNonNull(subscriber);
            this.mapper = requireNonNull(mapper);
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscriber.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            // If Function.apply(...) throws we just propagate it to the caller which is responsible to terminate
            // its subscriber and cancel the subscription.
            subscriber.onNext(mapper.apply(t));
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}
