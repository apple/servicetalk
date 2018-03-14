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

import java.util.function.Function;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#flatmapCompletable(Function)}.
 */
final class SingleFlatmapCompletable<T> extends Completable {
    private final Single<T> first;
    private final Function<T, Completable> nextFactory;

    /**
     * New instance.
     *
     * @param first Source.
     * @param nextFactory For creating the next {@link Completable}.
     */
    SingleFlatmapCompletable(Single<T> first, Function<T, Completable> nextFactory) {
        this.first = requireNonNull(first);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        first.subscribe(new SubscriberImpl<>(subscriber, nextFactory));
    }

    private static final class SubscriberImpl<T> implements Single.Subscriber<T>, Subscriber {
        private final Subscriber subscriber;
        private final Function<T, Completable> nextFactory;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        SubscriberImpl(Subscriber subscriber, Function<T, Completable> nextFactory) {
            this.subscriber = subscriber;
            this.nextFactory = nextFactory;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.setNextCancellable(cancellable);
            }
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onSuccess(@Nullable T result) {
            final Completable next;
            try {
                next = requireNonNull(nextFactory.apply(result));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            next.subscribe(this);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }
    }
}
