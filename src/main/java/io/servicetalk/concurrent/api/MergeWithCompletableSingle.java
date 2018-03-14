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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#merge(Completable)}.
 */
final class MergeWithCompletableSingle<T> extends Single<T> {
    private final Single<T> source;
    private final Completable mergeWith;

    /**
     * New instance.
     *
     * @param source Source.
     * @param mergeWith {@link Completable} to merge.
     */
    MergeWithCompletableSingle(Single<T> source, Completable mergeWith) {
        this.source = requireNonNull(source);
        this.mergeWith = requireNonNull(mergeWith);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        mergeWith.subscribe(new SubscriberImpl<>(subscriber, source));
    }

    private static final class SubscriberImpl<T> implements Subscriber<T>, Completable.Subscriber {
        private final Single<T> target;
        private final Subscriber<? super T> subscriber;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        SubscriberImpl(Subscriber<? super T> subscriber, Single<T> target) {
            this.subscriber = subscriber;
            this.target = target;
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
        public void onSuccess(@Nullable T result) {
            subscriber.onSuccess(result);
        }

        @Override
        public void onComplete() {
            target.subscribe(this);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }
    }
}
