/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.SignalOffloader;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Completable#concat(Single)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class CompletableConcatWithSingle<T> extends AbstractCompletableAndSingleConcatenated<T> {
    private final Completable original;
    private final Single<? extends T> next;

    CompletableConcatWithSingle(final Completable original, final Single<? extends T> next) {
        this.original = requireNonNull(original);
        this.next = requireNonNull(next);
    }

    @Override
    Executor executor() {
        return original.executor();
    }

    @Override
    void delegateSubscribeToOriginal(final Subscriber<? super T> offloadSubscriber, final SignalOffloader offloader,
                                     final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConcatWithSubscriber<>(offloadSubscriber, next), offloader, contextMap,
                contextProvider);
    }

    private static final class ConcatWithSubscriber<T> extends AbstractConcatWithSubscriber<T> {
        private final Single<T> next;

        ConcatWithSubscriber(final Subscriber<? super T> target, final Single<T> next) {
            super(target);
            this.next = next;
        }

        @Override
        public void onComplete() {
            subscribeToNext(next);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            sendSuccessToTarget(result);
        }
    }
}
