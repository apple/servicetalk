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
import io.servicetalk.concurrent.internal.SequentialCancellable;

import javax.annotation.Nullable;

abstract class AbstractCompletableAndSingleConcatenated<T> extends AbstractNoHandleSubscribeSingle<T> {

    AbstractCompletableAndSingleConcatenated() {
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber,
                                   final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        final Subscriber<? super T> wrappedSubscriber = contextProvider.wrapSingleSubscriber(subscriber, contextMap);
        delegateSubscribeToOriginal(wrappedSubscriber, contextMap, contextProvider);
    }

    abstract void delegateSubscribeToOriginal(Subscriber<? super T> offloadSubscriber,
                                              AsyncContextMap contextMap, AsyncContextProvider contextProvider);

    abstract static class AbstractConcatWithSubscriber<T> implements Subscriber<T>, CompletableSource.Subscriber {

        private final Subscriber<? super T> target;
        @Nullable
        private SequentialCancellable sequentialCancellable;

        AbstractConcatWithSubscriber(final Subscriber<? super T> target) {
            this.target = target;
        }

        @Override
        public final void onSubscribe(final Cancellable cancellable) {
            if (sequentialCancellable == null) {
                sequentialCancellable = new SequentialCancellable(cancellable);
                target.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        final void subscribeToNext(final Completable next) {
            // This is an asynchronous boundary, and so we should recapture the AsyncContext instead of propagating it.
            next.subscribeInternal(this);
        }

        final void subscribeToNext(final Single<T> next) {
            // This is an asynchronous boundary, and so we should recapture the AsyncContext instead of propagating it.
            next.subscribeInternal(this);
        }

        final void sendSuccessToTarget(@Nullable final T result) {
            target.onSuccess(result);
        }

        @Override
        public final void onError(final Throwable t) {
            target.onError(t);
        }
    }
}
