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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Allows only a single {@link Subscriber} to subscribe to a {@link TestPublisher}. Subsequent attempts to subscribe
 * will throw an exception.
 *
 * @param <T> Type of items received by the {@code Subscriber}.
 */
public final class NonResubscribeablePublisherSubscriberFunction<T>
        implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    private final AtomicBoolean subscribed = new AtomicBoolean();

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        if (!subscribed.compareAndSet(false, true)) {
            throw new IllegalStateException("Duplicate subscriber: " + subscriber);
        }
        return subscriber;
    }
}
