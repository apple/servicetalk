/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.core;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.opentracing.core.internal.AbstractListenableInMemoryScopeManager;
import io.servicetalk.opentracing.core.internal.InMemoryScope;
import io.servicetalk.opentracing.core.internal.InMemorySpan;
import io.servicetalk.opentracing.core.internal.ListenableInMemoryScopeManager;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;

/**
 * A {@link ListenableInMemoryScopeManager} that uses {@link AsyncContext} as the backing storage.
 */
public final class AsyncContextInMemoryScopeManager extends AbstractListenableInMemoryScopeManager {
    private static final Key<InMemoryScope> SCOPE_KEY = newKey("opentracing");
    public static final ListenableInMemoryScopeManager SCOPE_MANAGER = new AsyncContextInMemoryScopeManager();

    /**
     * Constructs an instance which stores the spans in {@link AsyncContext}.
     */
    private AsyncContextInMemoryScopeManager() {
        AsyncContext.addListener((oldContext, newContext) -> {
            InMemoryScope oldScope = oldContext.get(SCOPE_KEY);
            InMemoryScope newScope = newContext.get(SCOPE_KEY);
            if (oldScope != newScope) {
                notifySpanChanged(span(oldScope), span(newScope));
            }
        });
    }

    @Override
    public InMemoryScope activate(final InMemorySpan span, final boolean finishSpanOnClose) {
        InMemoryScope scope = new InMemoryScope() {
            @Override
            public void close() {
                if (finishSpanOnClose) {
                    span.finish();
                }

                // TODO(scott): the default thread local implementation does something similar,
                // but since AsyncContext has other infrastructure to save/restore it isn't clear this
                // is required by the API.
                // AsyncContext.put(key, oldScope);
            }

            @Override
            public InMemorySpan span() {
                return span;
            }
        };
        AsyncContext.put(SCOPE_KEY, scope);
        return scope;
    }

    @Nullable
    @Override
    public InMemoryScope active() {
        return AsyncContext.get(SCOPE_KEY);
    }

    @Nullable
    private static InMemorySpan span(@Nullable InMemoryScope scope) {
        return scope == null ? null : scope.span();
    }
}
