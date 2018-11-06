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
import org.reactivestreams.Subscription;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class DoBeforeSubscriberPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Supplier<Subscriber<? super T>> subscriberSupplier;

    DoBeforeSubscriberPublisher(Publisher<T> original, Supplier<Subscriber<? super T>> subscriberSupplier, Executor executor) {
        super(original, executor);
        this.subscriberSupplier = requireNonNull(subscriberSupplier);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new DoBeforeSubscriberPublisherSubscriber<>(subscriber, subscriberSupplier.get());
    }

    private static final class DoBeforeSubscriberPublisherSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final Subscriber<? super T> subscriber;

        DoBeforeSubscriberPublisherSubscriber(Subscriber<? super T> original, Subscriber<? super T> subscriber) {
            this.original = original;
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(Subscription s) {
            try {
                subscriber.onSubscribe(s);
            } catch (Throwable cause) {
                try {
                    original.onSubscribe(s);
                } catch (Throwable err) {
                    cause.addSuppressed(err);
                    throw cause;
                }
                throw cause;
            }
            original.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            subscriber.onNext(t);
            original.onNext(t);
        }

        @Override
        public void onComplete() {
            try {
                subscriber.onComplete();
            } catch (Throwable cause) {
                original.onError(cause);
                return;
            }
            original.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            try {
                subscriber.onError(t);
            } catch (Throwable cause) {
                t.addSuppressed(cause);
                original.onError(t);
                return;
            }
            original.onError(t);
        }
    }
}
