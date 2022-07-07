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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Single} implementation as returned by {@link Single#retryWhen(BiIntFunction)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class RetryWhenSingle<T> extends AbstractNoHandleSubscribeSingle<T> {

    private final Single<T> original;
    private final BiIntFunction<Throwable, ? extends Completable> shouldRetry;

    RetryWhenSingle(Single<T> original, BiIntFunction<Throwable, ? extends Completable> shouldRetry) {
        this.original = original;
        this.shouldRetry = shouldRetry;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        // For the current subscribe operation we want to use contextMap directly, but in the event a re-subscribe
        // operation occurs we want to restore the original state of the AsyncContext map, so we save a copy upfront.
        original.delegateSubscribe(new RetrySubscriber<>(new SequentialCancellable(), 0, subscriber,
                contextMap, contextProvider, this), contextMap, contextProvider);
    }

    private static final class RetrySubscriber<T> extends RetrySingle.AbstractRetrySubscriber<T> {
        private final SequentialCancellable retrySignalCancellable;
        private final RetryWhenSingle<T> retrySingle;
        private final ContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final CompletableSource.Subscriber completableSubscriber = new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(Cancellable completableCancellable) {
                retrySignalCancellable.nextCancellable(completableCancellable);
            }

            @Override
            public void onComplete() {
                // Either we copy the map up front before subscribe, or we just re-use the same map and let the async
                // source at the top of the chain reset if necessary. We currently choose the second option.
                retrySingle.original.delegateSubscribe(RetrySubscriber.this, contextMap, contextProvider);
            }

            @Override
            public void onError(Throwable t) {
                target.onError(t);
            }
        };

        RetrySubscriber(SequentialCancellable cancellable, int redoCount, Subscriber<? super T> subscriber,
                        ContextMap contextMap, AsyncContextProvider contextProvider,
                        RetryWhenSingle<T> retrySingle) {
            super(cancellable, subscriber, redoCount);
            this.retrySingle = retrySingle;
            retrySignalCancellable = new SequentialCancellable();
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        Cancellable decorate(Cancellable cancellable) {
            return () -> {
                try {
                    retrySignalCancellable.cancel();
                } finally {
                    cancellable.cancel();
                }
            };
        }

        @Override
        public void onSuccess(@Nullable T t) {
            target.onSuccess(t);
        }

        @Override
        public void onError(Throwable t) {
            final Completable retryDecider;
            try {
                retryDecider = requireNonNull(retrySingle.shouldRetry.apply(++retryCount, t),
                        () -> "Retry decider " + retrySingle.shouldRetry + " returned null");
            } catch (Throwable cause) {
                target.onError(addSuppressed(cause, t));
                return;
            }

            retryDecider.subscribeInternal(completableSubscriber);
        }
    }
}
