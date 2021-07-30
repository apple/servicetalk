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
package io.servicetalk.opentracing.asynccontext;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemoryScopeManager;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import io.opentracing.Scope;
import io.opentracing.Span;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static java.util.Objects.requireNonNull;

/**
 * A {@link InMemoryScopeManager} that uses {@link AsyncContext} as the backing storage.
 */
public final class AsyncContextInMemoryScopeManager implements InMemoryScopeManager {
    private static final AsyncContextMap.Key<AsyncContextInMemoryScope> SCOPE_KEY = newKey("opentracing");
    public static final InMemoryScopeManager SCOPE_MANAGER = new AsyncContextInMemoryScopeManager();

    private AsyncContextInMemoryScopeManager() {
        // singleton
    }

    @Override
    public Scope activate(final Span span) {
        AsyncContextMap contextMap = AsyncContext.current();
        AsyncContextInMemoryScope scope = new AsyncContextInMemoryScope(
                contextMap.get(SCOPE_KEY), (InMemorySpan) span);
        contextMap.put(SCOPE_KEY, scope);
        return scope;
    }

    @Override
    public InMemorySpan activeSpan() {
        AsyncContextInMemoryScope active = AsyncContext.get(SCOPE_KEY);
        if (active != null) {
            return active.span();
        }
        return null;
    }

    //visible for testing
    @Nullable
    AsyncContextInMemoryScope active() {
        return AsyncContext.get(SCOPE_KEY);
    }

    static final class AsyncContextInMemoryScope implements InMemoryScope {
        @Nullable
        private final AsyncContextInMemoryScope previousScope;
        private final InMemorySpan span;

        AsyncContextInMemoryScope(@Nullable AsyncContextInMemoryScope previousScope,
                                  InMemorySpan span) {
            this.previousScope = previousScope;
            this.span = requireNonNull(span);
        }

        @Override
        public void close() {
            if (previousScope == null) {
                AsyncContext.remove(SCOPE_KEY);
            } else {
                AsyncContext.put(SCOPE_KEY, previousScope);
            }
        }

        @Override
        public InMemorySpan span() {
            return span;
        }

        @Override
        public String toString() {
            return span.toString();
        }
    }
}
