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
import io.servicetalk.concurrent.internal.SequentialCancellable;

import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;

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
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
        // Current expected behavior is to capture the context on the first subscribe, save it, and re-use it on each
        // resubscribe. This allows for async context to be shared across each request retry, and follows the same
        // shared state model as the request object on the client. If copy-on-each-resubscribe is desired this could
        // be provided by an independent operator, or manually cleared/overwritten.
        original.delegateSubscribe(new RetrySubscriber<>(new SequentialCancellable(), this, subscriber, 0, capturedContext,
                contextProvider), capturedContext, contextProvider);
    }

    abstract static class AbstractRetrySubscriber<T> implements Subscriber<T> {
        final SequentialCancellable sequentialCancellable;
        final Subscriber<? super T> target;
        int retryCount;

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
        private final CapturedContext capturedContext;
        private final AsyncContextProvider contextProvider;

        RetrySubscriber(SequentialCancellable sequentialCancellable, RetrySingle<T> retrySingle,
                        Subscriber<? super T> target, int retryCount, CapturedContext capturedContext,
                        AsyncContextProvider contextProvider) {
            super(sequentialCancellable, target, retryCount);
            this.retrySingle = retrySingle;
            this.capturedContext = capturedContext;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSuccess(@Nullable T result) {
            target.onSuccess(result);
        }

        @Override
        public void onError(Throwable t) {
            final boolean shouldRetry;
            try {
                shouldRetry = retrySingle.shouldRetry.test(++retryCount, t);
            } catch (Throwable cause) {
                target.onError(addSuppressed(cause, t));
                return;
            }
            if (shouldRetry) {
                // Either we copy the map up front before subscribe, or we just re-use the same map and let the async
                // source at the top of the chain reset if necessary. We currently choose the second option.
                retrySingle.original.delegateSubscribe(this, capturedContext, contextProvider);
            } else {
                target.onError(t);
            }
        }
    }
}
