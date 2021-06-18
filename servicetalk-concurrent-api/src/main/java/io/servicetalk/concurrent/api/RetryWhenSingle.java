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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import javax.annotation.Nullable;

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
    Executor executor() {
        return original.executor();
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        // For the current subscribe operation we want to use contextMap directly, but in the event a re-subscribe
        // operation occurs we want to restore the original state of the AsyncContext map, so we save a copy upfront.
        original.delegateSubscribe(new RetrySubscriber<>(new SequentialCancellable(), 0, subscriber,
                contextMap, contextProvider, this, signalOffloader), signalOffloader, contextMap, contextProvider);
    }

    private static final class RetrySubscriber<T> extends RetrySingle.AbstractRetrySubscriber<T> {

        private final SequentialCancellable retrySignalCancellable;
        private final RetryWhenSingle<T> retrySingle;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;

        RetrySubscriber(SequentialCancellable cancellable, int redoCount, Subscriber<? super T> subscriber,
                        AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                        RetryWhenSingle<T> retrySingle, final SignalOffloader signalOffloader) {
            super(cancellable, subscriber, redoCount);
            this.retrySingle = retrySingle;
            retrySignalCancellable = new SequentialCancellable();
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            this.signalOffloader = signalOffloader;
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
                retryDecider = requireNonNull(retrySingle.shouldRetry.apply(retryCount + 1, t));
            } catch (Throwable cause) {
                cause.addSuppressed(t);
                target.onError(cause);
                return;
            }

            retryDecider.subscribeInternal(new CompletableSource.Subscriber() {
                @Override
                public void onSubscribe(Cancellable completableCancellable) {
                    retrySignalCancellable.nextCancellable(completableCancellable);
                }

                @Override
                public void onComplete() {
                    // For the current subscribe operation we want to use contextMap directly, but in the event a
                    // re-subscribe operation occurs we want to restore the original state of the AsyncContext map, so
                    // we save a copy upfront.
                    retrySingle.original.delegateSubscribe(new RetrySubscriber<>(sequentialCancellable,
                            retryCount + 1, target, contextMap.copy(), contextProvider, retrySingle, signalOffloader),
                            signalOffloader, contextMap, contextProvider);
                }

                @Override
                public void onError(Throwable t) {
                    target.onError(t);
                }
            });
        }
    }
}
