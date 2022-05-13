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

import io.servicetalk.concurrent.Cancellable;

import java.util.function.Supplier;

import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

final class BeforeSubscriberSingle<T> extends AbstractSynchronousSingleOperator<T, T> {
    private final Supplier<? extends Subscriber<? super T>> subscriberSupplier;

    BeforeSubscriberSingle(Single<T> original, Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        super(original);
        this.subscriberSupplier = requireNonNull(subscriberSupplier);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new BeforeSubscriberSingleSubscriber<>(subscriber, subscriberSupplier.get());
    }

    private static final class BeforeSubscriberSingleSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Subscriber<? super T> subscriber;

        BeforeSubscriberSingleSubscriber(Subscriber<? super T> original, Subscriber<? super T> subscriber) {
            this.original = original;
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            // If this throws we expect the source to bail on this Subscriber.
            subscriber.onSubscribe(cancellable);
            original.onSubscribe(cancellable);
        }

        @Override
        public void onSuccess(T value) {
            try {
                subscriber.onSuccess(value);
            } catch (Throwable cause) {
                original.onError(cause);
                return;
            }
            original.onSuccess(value);
        }

        @Override
        public void onError(Throwable t) {
            try {
                subscriber.onError(t);
            } catch (Throwable cause) {
                original.onError(addSuppressed(t, cause));
                return;
            }
            original.onError(t);
        }
    }
}
