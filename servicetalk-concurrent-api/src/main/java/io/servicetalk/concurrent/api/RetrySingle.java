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
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import javax.annotation.Nullable;

/**
 * A {@link Single} implementation as returned by {@link Single#retry(BiIntPredicate)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class RetrySingle<T> extends AbstractNoHandleSubscribeSingle<T> {

    private final Single<T> original;
    private final BiIntPredicate<Throwable> shouldRetry;

    RetrySingle(Single<T> original, BiIntPredicate<Throwable> shouldRetry) {
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
        original.delegateSubscribe(new RetrySubscriber<>(new SequentialCancellable(), this, subscriber,
                0, contextMap.copy(), contextProvider, signalOffloader),
                signalOffloader, contextMap, contextProvider);
    }

    abstract static class AbstractRetrySubscriber<T> implements Subscriber<T> {

        final SequentialCancellable sequentialCancellable;
        final Subscriber<? super T> target;
        final int retryCount;

        AbstractRetrySubscriber(SequentialCancellable sequentialCancellable, Subscriber<? super T> target,
                                int retryCount) {
            this.sequentialCancellable = sequentialCancellable;
            this.target = target;
            this.retryCount = retryCount;
        }

        @Override
        public final void onSubscribe(Cancellable cancellable) {
            cancellable = decorate(cancellable);
            sequentialCancellable.nextCancellable(cancellable);
            if (retryCount == 0) {
                target.onSubscribe(sequentialCancellable);
            }
        }

        Cancellable decorate(Cancellable cancellable) {
            return cancellable;
        }
    }

    private static final class RetrySubscriber<T> extends AbstractRetrySubscriber<T> {

        private final RetrySingle<T> retrySingle;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final SignalOffloader signalOffloader;

        RetrySubscriber(SequentialCancellable sequentialCancellable, RetrySingle<T> retrySingle,
                        Subscriber<? super T> target, int retryCount, AsyncContextMap contextMap,
                        AsyncContextProvider contextProvider, final SignalOffloader signalOffloader) {
            super(sequentialCancellable, target, retryCount);
            this.retrySingle = retrySingle;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            this.signalOffloader = signalOffloader;
        }

        @Override
        public void onSuccess(@Nullable T result) {
            target.onSuccess(result);
        }

        @Override
        public void onError(Throwable t) {
            final boolean shouldRetry;
            try {
                shouldRetry = retrySingle.shouldRetry.test(retryCount + 1, t);
            } catch (Throwable cause) {
                cause.addSuppressed(t);
                target.onError(cause);
                return;
            }
            if (shouldRetry) {
                // For the current subscribe operation we want to use contextMap directly, but in the event a
                // re-subscribe operation occurs we want to restore the original state of the AsyncContext map, so
                // we save a copy upfront.
                retrySingle.original.delegateSubscribe(
                        new RetrySubscriber<>(sequentialCancellable, retrySingle, target, retryCount + 1,
                                contextMap.copy(), contextProvider, signalOffloader), signalOffloader,
                        contextMap, contextProvider);
            } else {
                target.onError(t);
            }
        }
    }
}
