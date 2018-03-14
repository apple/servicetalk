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
 * A {@link Single} implementation as returned by {@link Single#retryWhen(BiIntFunction)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class RetryWhenSingle<T> extends Single<T> {

    private final Single<T> original;
    private final BiIntFunction<Throwable, Completable> shouldRetry;

    RetryWhenSingle(Single<T> original, BiIntFunction<Throwable, Completable> shouldRetry) {
        this.original = original;
        this.shouldRetry = shouldRetry;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        final SequentialCancellable cancellable = new SequentialCancellable();
        original.subscribe(new RetrySubscriber<>(cancellable, 0, subscriber, this));
    }

    private static final class RetrySubscriber<T> extends RetrySingle.AbstractRetrySubscriber<T> {

        private final SequentialCancellable retrySignalCancellable;
        private final RetryWhenSingle<T> retrySingle;

        RetrySubscriber(SequentialCancellable cancellable, int redoCount, Subscriber<? super T> subscriber,
                        RetryWhenSingle<T> retrySingle) {
            super(cancellable, subscriber, redoCount);
            this.retrySingle = retrySingle;
            retrySignalCancellable = new SequentialCancellable();
        }

        @Override
        Cancellable decorate(Cancellable cancellable) {
            return () -> {
                retrySignalCancellable.cancel();
                cancellable.cancel();
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

            retryDecider.subscribe(new Completable.Subscriber() {
                @Override
                public void onSubscribe(Cancellable completableCancellable) {
                    retrySignalCancellable.setNextCancellable(completableCancellable);
                }

                @Override
                public void onComplete() {
                    retrySingle.original.subscribe(new RetrySubscriber<>(sequentialCancellable, retryCount + 1, target, retrySingle));
                }

                @Override
                public void onError(Throwable t) {
                    target.onError(t);
                }
            });
        }
    }
}
