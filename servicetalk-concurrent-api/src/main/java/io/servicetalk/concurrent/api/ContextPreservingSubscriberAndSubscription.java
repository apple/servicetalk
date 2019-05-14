/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.contextThreadLocal;
import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscriberAndSubscription<T> implements Subscriber<T> {
    final AsyncContextMap saved;
    final Subscriber<T> subscriber;

    ContextPreservingSubscriberAndSubscription(Subscriber<T> subscriber, AsyncContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    @Override
    public void onSubscribe(PublisherSource.Subscription s) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscriber.onSubscribe(ContextPreservingSubscription.wrap(s, saved));
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            onSubscribeSlowPath(s);
        }
    }

    private void onSubscribeSlowPath(PublisherSource.Subscription s) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscriber.onSubscribe(ContextPreservingSubscription.wrap(s, saved));
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public void onNext(T t) {
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
    public void onError(Throwable t) {
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
    public void onComplete() {
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
        return ContextPreservingSubscriberAndSubscription.class.getSimpleName() + "(" + subscriber + ')';
    }
}
