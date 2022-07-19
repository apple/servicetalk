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

import java.util.function.Function;

import static io.servicetalk.concurrent.api.AsyncContextMapThreadLocal.CONTEXT_THREAD_LOCAL;
import static java.util.Objects.requireNonNull;

final class ContextPreservingFunction<T, U> implements Function<T, U> {
    private final ContextMap saved;
    private final Function<T, U> delegate;

    private final Throwable creator = new Throwable("Function creator");

    ContextPreservingFunction(Function<T, U> delegate, ContextMap contextMap) {
        this.saved = requireNonNull(contextMap);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public U apply(T t) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            try {
                asyncContextMapHolder.context(saved);
                return delegate.apply(t);
            } catch (Throwable all) {
                all.addSuppressed(creator);
                throw all;
            } finally {
                asyncContextMapHolder.context(prev);
            }
        } else {
            return slowPath(t);
        }
    }

    private U slowPath(T t) {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        try {
            CONTEXT_THREAD_LOCAL.set(saved);
            return delegate.apply(t);
        } finally {
            CONTEXT_THREAD_LOCAL.set(prev);
        }
    }
}
