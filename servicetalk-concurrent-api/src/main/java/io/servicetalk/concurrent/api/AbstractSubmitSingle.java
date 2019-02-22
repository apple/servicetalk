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
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

abstract class AbstractSubmitSingle<T> extends Single<T> {
    private final Executor runExecutor;

    AbstractSubmitSingle(final Executor runExecutor) {
        this.runExecutor = requireNonNull(runExecutor);
    }

    abstract Callable<? extends T> callable();

    @Override
    protected final void handleSubscribe(final Subscriber<? super T> subscriber) {
        DelayedCancellable cancellable = new DelayedCancellable();
        subscriber.onSubscribe(cancellable);
        final Cancellable eCancellable;
        try {
            eCancellable = runExecutor.execute(() -> {
                final T result;
                try {
                    result = callable().call();
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSuccess(result);
            });
        } catch (Throwable cause) {
            // We assume that runExecutor.execute() either throws or executes the Runnable. Hence we do not have to
            // protect against duplicate terminal events.
            subscriber.onError(cause);
            return;
        }
        cancellable.delayedCancellable(eCancellable);
    }
}
