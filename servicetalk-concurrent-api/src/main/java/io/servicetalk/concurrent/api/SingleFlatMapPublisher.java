/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.SingleSource;

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#flatMapPublisher(Function)}.
 */
final class SingleFlatMapPublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {
    private final Single<T> original;
    private final Function<? super T, ? extends Publisher<? extends R>> nextFactory;

    SingleFlatMapPublisher(Single<T> original, Function<? super T, ? extends Publisher<? extends R>> nextFactory) {
        this.original = requireNonNull(original);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new SubscriberImpl<>(subscriber, nextFactory,
                        contextMap, contextProvider), contextMap, contextProvider);
    }

    private static final class SubscriberImpl<T, R> extends DelayedCancellableThenSubscription
            implements SingleSource.Subscriber<T>, Subscriber<R> {
        private final Subscriber<? super R> subscriber;
        private final Function<? super T, ? extends Publisher<? extends R>> nextFactory;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;

        SubscriberImpl(Subscriber<? super R> subscriber,
                       Function<? super T, ? extends Publisher<? extends R>> nextFactory,
                       final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.nextFactory = requireNonNull(nextFactory);
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            delayedCancellable(cancellable);
            subscriber.onSubscribe(this);
        }

        @Override
        public void onSubscribe(Subscription s) {
            delayedSubscription(s);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            final Publisher<? extends R> next;
            try {
                next = requireNonNull(nextFactory.apply(result));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            // The static AsyncContext should be the same as the original contextMap at this point because we are
            // being notified in the Subscriber path, but we make sure that it is restored after the asynchronous
            // boundary and explicitly use it to subscribe.
            next.subscribeInternal((Subscriber<? super R>)
                    contextProvider.wrapPublisherSubscriber(this, contextMap));
        }

        @Override
        public void onNext(R r) {
            subscriber.onNext(r);
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
