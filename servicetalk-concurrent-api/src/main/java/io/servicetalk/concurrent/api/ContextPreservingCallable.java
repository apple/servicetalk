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

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

final class ContextPreservingCallable<V> implements Callable<V> {
    private final ContextMap saved;
    private final Callable<V> delegate;

    ContextPreservingCallable(Callable<V> delegate) {
        this(delegate, AsyncContext.provider().captureContext());
    }

    ContextPreservingCallable(Callable<V> delegate, ContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public V call() throws Exception {
        try (Scope ignored = AsyncContext.provider().attachContext(saved)) {
            return delegate.call();
        }
    }
}
