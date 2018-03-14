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
 * As returned by {@link Completable#andThen(Single)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class CompletableAndThenSingle<T> extends Single<T> {
    private final Completable original;
    private final Single<T> next;

    CompletableAndThenSingle(Completable original, Single<T> next) {
        this.original = requireNonNull(original);
        this.next = requireNonNull(next);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        original.subscribe(new AndThenSubscriber<>(subscriber, next));
    }

    private static final class AndThenSubscriber<T> implements Subscriber<T>, Completable.Subscriber {
        private final Subscriber<? super T> target;
        private final Single<T> next;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        AndThenSubscriber(Subscriber<? super T> target, Single<T> next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onComplete() {
            next.subscribe(this);
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                target.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.setNextCancellable(cancellable);
            }
        }

        @Override
        public void onSuccess(@Nullable T result) {
            target.onSuccess(result);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }
    }
}
