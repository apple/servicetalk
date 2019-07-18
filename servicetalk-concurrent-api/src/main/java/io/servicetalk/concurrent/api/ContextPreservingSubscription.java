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

import io.servicetalk.concurrent.PublisherSource.Subscription;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.contextThreadLocal;
import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscription implements Subscription {
    private final AsyncContextMap saved;
    private final Subscription subscription;

    private ContextPreservingSubscription(Subscription subscription, AsyncContextMap current) {
        this.subscription = requireNonNull(subscription);
        this.saved = requireNonNull(current);
    }

    static Subscription wrap(Subscription subscription, AsyncContextMap current) {
        return subscription instanceof ContextPreservingSubscription &&
                ((ContextPreservingSubscription) subscription).saved == current ? subscription :
                new ContextPreservingSubscription(subscription, current);
    }

    @Override
    public void request(long l) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscription.request(l);
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            requestSlowPath(l);
        }
    }

    private void requestSlowPath(long l) {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscription.request(l);
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public void cancel() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) currentThread;
            AsyncContextMap prev = asyncContextMapHolder.asyncContextMap();
            try {
                asyncContextMapHolder.asyncContextMap(saved);
                subscription.cancel();
            } finally {
                asyncContextMapHolder.asyncContextMap(prev);
            }
        } else {
            cancelSlowPath();
        }
    }

    private void cancelSlowPath() {
        AsyncContextMap prev = contextThreadLocal.get();
        try {
            contextThreadLocal.set(saved);
            subscription.cancel();
        } finally {
            contextThreadLocal.set(prev);
        }
    }

    @Override
    public String toString() {
        return ContextPreservingSubscription.class.getSimpleName() + "(" + subscription + ')';
    }
}
