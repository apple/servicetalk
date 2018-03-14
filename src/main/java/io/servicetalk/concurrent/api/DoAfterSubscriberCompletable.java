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

final class DoAfterSubscriberCompletable extends Completable {
    private final Completable original;
    private final Supplier<Subscriber> subscriberSupplier;

    DoAfterSubscriberCompletable(Completable original, Supplier<Subscriber> subscriberSupplier) {
        this.original = requireNonNull(original);
        this.subscriberSupplier = requireNonNull(subscriberSupplier);
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        original.subscribe(new DoBeforeSubscriberCompletableSubscriber(subscriber, subscriberSupplier.get()));
    }

    private static final class DoBeforeSubscriberCompletableSubscriber implements Subscriber {
        private final Subscriber original;
        private final Subscriber subscriber;

        DoBeforeSubscriberCompletableSubscriber(Subscriber original, Subscriber subscriber) {
            this.original = original;
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            try {
                original.onSubscribe(cancellable);
            } catch (Throwable cause) {
                try {
                    subscriber.onSubscribe(cancellable);
                } catch (Throwable err) {
                    err.addSuppressed(cause);
                    throw err;
                }
                throw cause;
            }
            subscriber.onSubscribe(cancellable);
        }

        @Override
        public void onComplete() {
            original.onComplete();
            subscriber.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            original.onError(t);
            subscriber.onError(t);
        }
    }
}
