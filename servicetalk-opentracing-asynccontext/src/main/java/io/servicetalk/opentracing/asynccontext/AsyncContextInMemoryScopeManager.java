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
package io.servicetalk.opentracing.asynccontext;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemoryScopeManager;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static java.util.Objects.requireNonNull;

/**
 * A {@link InMemoryScopeManager} that uses {@link AsyncContext} as the backing storage.
 */
public final class AsyncContextInMemoryScopeManager implements InMemoryScopeManager {
    private static final AsyncContextMap.Key<AsyncContextInMemoryScope> SCOPE_KEY = newKey("opentracing");
    public static final AsyncContextInMemoryScopeManager SCOPE_MANAGER = new AsyncContextInMemoryScopeManager();

    private AsyncContextInMemoryScopeManager() {
        // singleton
    }

    @Override
    public InMemoryScope activate(final InMemorySpan span, final boolean finishSpanOnClose) {
        AsyncContextInMemoryScope scope = new AsyncContextInMemoryScope(span, finishSpanOnClose);
        AsyncContext.put(SCOPE_KEY, scope);
        return scope;
    }

    @Nullable
    @Override
    public InMemoryScope active() {
        AsyncContextInMemoryScope scope = AsyncContext.get(SCOPE_KEY);
        return scope == null || scope.closed ? null : scope;
    }

    /**
     * Get the currently {@link #active()} {@link InMemoryScope} or maybe also the current {@link InMemoryScope} for
     * which {@link InMemoryScope#close()} was previously called.
     * @return the currently {@link #active()} {@link InMemoryScope} or maybe also the current {@link InMemoryScope} for
     * which {@link InMemoryScope#close()} was previously called.
     */
    @Nullable
    public InMemoryScope currentScope() {
        return AsyncContext.get(SCOPE_KEY);
    }

    private static final class AsyncContextInMemoryScope implements InMemoryScope {
        private final InMemorySpan span;
        private final boolean finishSpanOnClose;
        private boolean closed;

        AsyncContextInMemoryScope(InMemorySpan span, boolean finishSpanOnClose) {
            this.span = requireNonNull(span);
            this.finishSpanOnClose = finishSpanOnClose;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (finishSpanOnClose) {
                span.finish();
            }

            // The lifetime of a Scope in ServiceTalk is typically owned by a single filter. However this information
            // is used across filter boundaries for logging, MDC, etc... If calling close() on the Scope also removed
            // the scope from AsyncContext this would either make it invisible from other filters or introduce a
            // before/after order dependency which is fragile. Instead we use the closed variable to ensure that
            // AsyncContextInMemoryScopeManager#active() doesn't return a scope after it has been closed.
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
