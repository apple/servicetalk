/**
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * A {@link Single} implementation as returned by {@link Single#retry(BiIntPredicate)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class RetrySingle<T> extends Single<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrySingle.class);

    private final Single<T> original;
    private final BiIntPredicate<Throwable> shouldRetry;

    RetrySingle(Single<T> original, BiIntPredicate<Throwable> shouldRetry) {
        this.original = original;
        this.shouldRetry = shouldRetry;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        SequentialCancellable cancellable = new SequentialCancellable();
        original.subscribe(new RetrySubscriber<>(cancellable, this, subscriber, 0));
    }

    abstract static class AbstractRetrySubscriber<T> implements Subscriber<T> {

        final SequentialCancellable sequentialCancellable;
        final Subscriber<? super T> target;
        final int retryCount;

        AbstractRetrySubscriber(SequentialCancellable sequentialCancellable, Subscriber<? super T> target, int retryCount) {
            this.sequentialCancellable = sequentialCancellable;
            this.target = target;
            this.retryCount = retryCount;
        }

        @Override
        public final void onSubscribe(Cancellable cancellable) {
            cancellable = decorate(cancellable);
            sequentialCancellable.setNextCancellable(cancellable);
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

        RetrySubscriber(SequentialCancellable sequentialCancellable, RetrySingle<T> retrySingle, Subscriber<? super T> target,
                        int retryCount) {
            super(sequentialCancellable, target, retryCount);
            this.retrySingle = retrySingle;
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
                retrySingle.original.subscribe(new RetrySubscriber<>(sequentialCancellable, retrySingle, target, retryCount + 1));
            } else {
                target.onError(t);
            }
        }
    }
}
