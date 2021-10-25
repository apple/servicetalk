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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMapHolder;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.CONTEXT_THREAD_LOCAL;
import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscription implements Subscription {
    private final ContextMap saved;
    private final Subscription subscription;

    private ContextPreservingSubscription(Subscription subscription, ContextMap current) {
        this.subscription = requireNonNull(subscription);
        this.saved = requireNonNull(current);
    }

    static Subscription wrap(Subscription subscription, ContextMap current) {
        return subscription instanceof ContextPreservingSubscription &&
                ((ContextPreservingSubscription) subscription).saved == current ? subscription :
                new ContextPreservingSubscription(subscription, current);
    }

    @Override
    public void request(long l) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                subscription.request(l);
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            requestSlowPath(l);
        }
    }

    private void requestSlowPath(long l) {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            subscription.request(l);
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }

    @Override
    public void cancel() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                subscription.cancel();
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            cancelSlowPath();
        }
    }

    private void cancelSlowPath() {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            subscription.cancel();
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscription + ')';
    }
}
