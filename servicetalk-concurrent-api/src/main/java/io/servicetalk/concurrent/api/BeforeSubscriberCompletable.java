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

import static java.util.Objects.requireNonNull;

final class BeforeSubscriberCompletable extends AbstractSynchronousCompletableOperator {
    private final Supplier<? extends Subscriber> subscriberSupplier;

    BeforeSubscriberCompletable(Completable original, Supplier<? extends Subscriber> subscriberSupplier) {
        super(original);
        this.subscriberSupplier = requireNonNull(subscriberSupplier);
    }

    @Override
    public Subscriber apply(final Subscriber subscriber) {
        return new BeforeSubscriberCompletableSubscriber(subscriber, subscriberSupplier.get());
    }

    private static final class BeforeSubscriberCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final Subscriber subscriber;

        BeforeSubscriberCompletableSubscriber(Subscriber original,
                                              Subscriber subscriber) {
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
