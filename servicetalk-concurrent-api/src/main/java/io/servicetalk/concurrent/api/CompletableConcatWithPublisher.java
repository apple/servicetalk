/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;

import javax.annotation.Nullable;

final class CompletableConcatWithPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Completable original;
    private final Publisher<? extends T> next;

    CompletableConcatWithPublisher(final Completable original, Publisher<? extends T> next) {
        this.original = original;
        this.next = next;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConcatSubscriber<>(subscriber, next), contextMap, contextProvider);
    }

    private static final class ConcatSubscriber<T> extends DelayedCancellableThenSubscription
            implements CompletableSource.Subscriber, PublisherSource.Subscriber<T> {

        private final Subscriber<? super T> target;
        private final Publisher<? extends T> next;
        private boolean subscribedToPublisher;

        ConcatSubscriber(final Subscriber<? super T> subscriber, final Publisher<? extends T> next) {
            this.target = subscriber;
            this.next = next;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            delayedCancellable(cancellable);
            target.onSubscribe(this);
        }

        @Override
        public void onComplete() {
            if (subscribedToPublisher) {
                target.onComplete();
            } else {
                subscribedToPublisher = true;
                next.subscribeInternal(this);
            }
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            delayedSubscription(subscription);
        }

        @Override
        public void onNext(@Nullable final T t) {
            target.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            target.onError(t);
        }
    }
}
