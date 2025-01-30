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

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

final class ContextPreservingConsumer<T> implements Consumer<T> {
    private final ContextMap saved;
    private final Consumer<T> delegate;

    ContextPreservingConsumer(Consumer<T> delegate, ContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void accept(T t) {
        AsyncContextProvider provider = AsyncContext.provider();
        try (Scope ignored = provider.attachContext(saved)) {
            delegate.accept(t);
        }
    }
}
