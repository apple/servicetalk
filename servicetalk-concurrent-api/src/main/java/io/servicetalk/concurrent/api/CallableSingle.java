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

import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import java.util.concurrent.Callable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnSuccess;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class CallableSingle<T> extends AbstractSynchronousSingle<T> {
    private final Callable<T> callable;

    CallableSingle(final Callable<T> callable) {
        this.callable = requireNonNull(callable);
    }

    @Override
    void doSubscribe(final Subscriber<? super T> subscriber) {
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
        try {
            subscriber.onSubscribe(cancellable);
        } catch (Throwable cause) {
            handleExceptionFromOnSubscribe(subscriber, cause);
            return;
        }

        final T value;
        try {
            value = callable.call();
        } catch (Throwable cause) {
            cancellable.setDone(cause);
            safeOnError(subscriber, cause);
            return;
        }
        // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
        // operations which may be interrupted between the completion of the blockingHttpService call and here.
        cancellable.setDone();
        safeOnSuccess(subscriber, value);
    }
}
