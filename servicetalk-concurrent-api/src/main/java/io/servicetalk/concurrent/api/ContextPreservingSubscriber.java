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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.contextThreadLocal;
import static java.util.Objects.requireNonNull;

class ContextPreservingSubscriber<T> implements Subscriber<T> {
    final AsyncContextMap saved;
    final Subscriber<T> subscriber;

    ContextPreservingSubscriber(Subscriber<T> subscriber, AsyncContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    void invokeOnSubscribe(Subscription s) {
        subscriber.onSubscribe(s);
    }

    @Override
    public final void onSubscribe(Subscription s) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                invokeOnSubscribe(s);
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onSubscribeSlowPath(s);
        }
    }

    private void onSubscribeSlowPath(Subscription s) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            invokeOnSubscribe(s);
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public final void onNext(T t) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscriber.onNext(t);
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onNextSlowPath(t);
        }
    }

    private void onNextSlowPath(T t) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscriber.onNext(t);
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
    public final void onComplete() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscriber.onComplete();
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onCompleteSlowPath();
        }
    }

    private void onCompleteSlowPath() {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscriber.onComplete();
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + subscriber + ')';
    }
}
