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

import org.reactivestreams.Subscription;

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#flatmapPublisher(Function)}.
 */
final class SingleFlatmapPublisher<T, R> extends Publisher<R> {
    private final Single<T> original;
    private final Function<T, Publisher<R>> nextFactory;

    /**
     * New instance.
     *
     * @param original Source.
     * @param nextFactory For creating the next {@link Publisher}.
     */
    SingleFlatmapPublisher(Single<T> original, Function<T, Publisher<R>> nextFactory) {
        this.original = requireNonNull(original);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    public void handleSubscribe(org.reactivestreams.Subscriber<? super R> s) {
        original.subscribe(new SubscriberImpl<>(s, nextFactory));
    }

    private static final class SubscriberImpl<T, R> implements Single.Subscriber<T>, org.reactivestreams.Subscriber<R> {
        private final org.reactivestreams.Subscriber<? super R> subscriber;
        private final Function<T, Publisher<R>> nextFactory;
        @Nullable
        private volatile SequentialSubscription sequentialSubscription;

        SubscriberImpl(org.reactivestreams.Subscriber<? super R> subscriber, Function<T, Publisher<R>> nextFactory) {
            this.subscriber = subscriber;
            this.nextFactory = requireNonNull(nextFactory);
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialSubscription sequentialSubscription = this.sequentialSubscription;
            if (sequentialSubscription != null) {
                sequentialSubscription.cancel();
                return;
            }
            this.sequentialSubscription = sequentialSubscription = new SequentialSubscription(new Subscription() {
                @Override
                public void request(long n) {
                    // This is a NOOP because the work for the original Single is already in progress when onSubscribe
                    // is called. Demand for Single is implicit so there is nothing to request.
                }

                @Override
                public void cancel() {
                    cancellable.cancel();
                }
            });
            subscriber.onSubscribe(sequentialSubscription);
        }

        @Override
        public void onSubscribe(Subscription s) {
            SequentialSubscription sequentialSubscription = this.sequentialSubscription;
            assert sequentialSubscription != null;
            sequentialSubscription.switchTo(s);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            final Publisher<R> next;
            try {
                next = requireNonNull(nextFactory.apply(result));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            next.subscribe(this);
        }

        @Override
        public void onNext(R r) {
            SequentialSubscription sequentialSubscription = this.sequentialSubscription;
            assert sequentialSubscription != null;
            sequentialSubscription.itemReceived();
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
