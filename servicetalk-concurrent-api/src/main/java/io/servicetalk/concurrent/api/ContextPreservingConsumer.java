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

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingConsumer<T> implements Consumer<T> {
    private final AsyncContextMap saved;
    private final Consumer<T> delegate;

    ContextPreservingConsumer(Consumer<T> delegate, AsyncContextMap current) {
        this.saved = requireNonNull(current);
        this.delegate = delegate instanceof ContextPreservingConsumer ?
                ((ContextPreservingConsumer<T>) delegate).delegate : requireNonNull(delegate);
    }

    @Override
    public void accept(T t) {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            delegate.accept(t);
        } finally {
            INSTANCE.contextMap(prev);
        }
    }
}
