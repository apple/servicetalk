/**
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
import io.servicetalk.concurrent.internal.SequentialCancellable;
import org.reactivestreams.Subscription;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;

final class CompletableToPublisher<T> extends Publisher<T> {
    private final Supplier<T> valueSupplier;
    private final Completable parent;

    CompletableToPublisher(Completable parent, Supplier<T> valueSupplier) {
        this.valueSupplier = requireNonNull(valueSupplier);
        this.parent = parent;
    }

    @Override
    protected void handleSubscribe(org.reactivestreams.Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new ConversionSubscriber<>(valueSupplier, parent, subscriber));
    }

    private static final class ConversionSubscriber<T> implements Completable.Subscriber, Subscription {
        private final SequentialCancellable sequentialCancellable;
        private final org.reactivestreams.Subscriber<? super T> subscriber;
        private final Completable parent;
        private final Supplier<T> valueSupplier;
        private boolean subscribedToParent;

        private ConversionSubscriber(Supplier<T> valueSupplier, Completable parent, org.reactivestreams.Subscriber<? super T> subscriber) {
            this.valueSupplier = valueSupplier;
            this.parent = parent;
            this.subscriber = subscriber;
            sequentialCancellable = new SequentialCancellable();
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            sequentialCancellable.setNextCancellable(cancellable);
        }

        @Override
        public void onComplete() {
            try {
                subscriber.onNext(valueSupplier.get());
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void request(long n) {
            if (!subscribedToParent) {
                subscribedToParent = true;
                if (isRequestNValid(n)) {
                    parent.subscribe(this);
                } else {
                    subscriber.onError(newExceptionForInvalidRequestN(n));
                }
            }
        }

        @Override
        public void cancel() {
            sequentialCancellable.cancel();
        }
    }
}
