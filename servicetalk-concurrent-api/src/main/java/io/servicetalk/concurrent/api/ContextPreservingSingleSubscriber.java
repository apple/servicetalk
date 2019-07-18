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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.contextThreadLocal;
import static java.util.Objects.requireNonNull;

class ContextPreservingSingleSubscriber<T> implements Subscriber<T> {
    final AsyncContextMap saved;
    final SingleSource.Subscriber<T> subscriber;

    ContextPreservingSingleSubscriber(Subscriber<T> subscriber, AsyncContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    void invokeOnSubscribe(Cancellable cancellable) {
        subscriber.onSubscribe(cancellable);
    }

    @Override
    public final void onSubscribe(Cancellable cancellable) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                invokeOnSubscribe(cancellable);
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onSubscribeSlowPath(cancellable);
        }
    }

    private void onSubscribeSlowPath(Cancellable cancellable) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            invokeOnSubscribe(cancellable);
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public final void onSuccess(@Nullable T result) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscriber.onSuccess(result);
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onSuccessSlowPath(result);
        }
    }

    private void onSuccessSlowPath(@Nullable T result) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscriber.onSuccess(result);
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public final void onError(Throwable t) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscriber.onError(t);
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onErrorSlowPath(t);
        }
    }

    private void onErrorSlowPath(Throwable t) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscriber.onError(t);
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + subscriber + ')';
    }
}
