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

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMapHolder;

import java.util.concurrent.Callable;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.CONTEXT_THREAD_LOCAL;
import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCallable<V> implements Callable<V> {
    private final ContextMap saved;
    private final Callable<V> delegate;

    ContextPreservingCallable(Callable<V> delegate) {
        this(delegate, INSTANCE.context());
    }

    ContextPreservingCallable(Callable<V> delegate, ContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public V call() throws Exception {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                return delegate.call();
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            return slowPath();
        }
    }

    private V slowPath() throws Exception {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            return delegate.call();
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }
}
