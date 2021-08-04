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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Single#concat(Completable)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class SingleConcatWithCompletable<T> extends AbstractCompletableAndSingleConcatenated<T> {
    private final Single<? extends T> original;
    private final Completable next;

    SingleConcatWithCompletable(final Single<? extends T> original, final Completable next) {
        this.original = requireNonNull(original);
        this.next = requireNonNull(next);
    }

    @Override
    void delegateSubscribeToOriginal(final Subscriber<? super T> offloadSubscriber,
                                     final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConcatWithSubscriber<>(offloadSubscriber, next), contextMap,
                contextProvider);
    }

    private static final class ConcatWithSubscriber<T> extends AbstractConcatWithSubscriber<T> {
        private final Completable next;
        @Nullable
        private T result;

        ConcatWithSubscriber(final Subscriber<? super T> target, final Completable next) {
            super(target);
            this.next = next;
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            this.result = result;
            subscribeToNext(next);
        }

        @Override
        public void onComplete() {
            sendSuccessToTarget(result);
        }
    }
}
