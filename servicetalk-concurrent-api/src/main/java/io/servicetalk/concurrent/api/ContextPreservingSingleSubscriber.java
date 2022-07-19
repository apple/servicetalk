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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMapHolder;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.CONTEXT_THREAD_LOCAL;
import static java.util.Objects.requireNonNull;

class ContextPreservingSingleSubscriber<T> implements Subscriber<T> {
    final ContextMap saved;
    final SingleSource.Subscriber<T> subscriber;

    final Throwable creator = new Throwable("Subscriber creator");

    ContextPreservingSingleSubscriber(Subscriber<T> subscriber, ContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    void invokeOnSubscribe(Cancellable cancellable) {
        subscriber.onSubscribe(cancellable);
    }

    @Override
    public final void onSubscribe(Cancellable cancellable) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                invokeOnSubscribe(cancellable);
            } catch (Throwable all) {
                all.addSuppressed(creator);
                throw all;
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            onSubscribeSlowPath(cancellable);
        }
    }

    private void onSubscribeSlowPath(Cancellable cancellable) {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            invokeOnSubscribe(cancellable);
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }

    @Override
    public final void onSuccess(@Nullable T result) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                subscriber.onSuccess(result);
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            onSuccessSlowPath(result);
        }
    }

    private void onSuccessSlowPath(@Nullable T result) {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            subscriber.onSuccess(result);
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }

    @Override
    public final void onError(Throwable t) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                subscriber.onError(t);
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            onErrorSlowPath(t);
        }
    }

    private void onErrorSlowPath(Throwable t) {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            subscriber.onError(t);
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
