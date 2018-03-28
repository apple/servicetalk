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
import io.servicetalk.concurrent.internal.SequentialCancellable;

import org.reactivestreams.Subscription;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

/**
 * {@link Publisher} created from a {@link Single}.
 *
 * @param <T> Type of item emitted by the {@link Publisher}.
 */
final class SingleToPublisher<T> extends Publisher<T> {
    private final Single<T> parent;

    /**
     * New instance.
     *
     * @param parent Source {@link Single}.
     */
    SingleToPublisher(Single<T> parent) {
        this.parent = parent;
    }

    @Override
    public void handleSubscribe(final org.reactivestreams.Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new State<>(parent, subscriber));
    }

    private static final class State<T> implements Subscription, Single.Subscriber<T> {
        private final SequentialCancellable sequentialCancellable;
        private final org.reactivestreams.Subscriber<? super T> subscriber;
        private final Single<T> parent;
        private boolean subscribedToParent;

        private State(Single<T> parent, org.reactivestreams.Subscriber<? super T> subscriber) {
            this.parent = parent;
            this.subscriber = subscriber;
            sequentialCancellable = new SequentialCancellable();
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            sequentialCancellable.setNextCancellable(cancellable);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            try {
                subscriber.onNext(result);
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
