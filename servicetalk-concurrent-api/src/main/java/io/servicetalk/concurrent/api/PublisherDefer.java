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

import org.reactivestreams.Subscriber;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#defer(Supplier)}.
 *
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class PublisherDefer<T> extends Publisher<T> {

    private final Supplier<Publisher<T>> publisherFactory;

    PublisherDefer(Supplier<Publisher<T>> publisherFactory) {
        this.publisherFactory = requireNonNull(publisherFactory);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        // There are technically two sources, one this Publisher and the other returned by publisherFactory.
        // Since, we are invoking user code (publisherFactory) we need this method to be run using an Executor
        // and also use the configured Executor for subscribing to the Publisher returned from publisherFactory
        publisherFactory.get().subscribe(subscriber);
    }
}
