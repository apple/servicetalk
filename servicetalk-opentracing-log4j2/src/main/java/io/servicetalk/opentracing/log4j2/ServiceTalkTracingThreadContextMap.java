/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.log4j2;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.log4j2.mdc.utils.ServiceTalkThreadContextMap;
import io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager;
import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static java.util.Collections.unmodifiableMap;

/**
 * A {@link ThreadContext} that provides storage for MDC based upon {@link AsyncContext} that also includes tracing
 * information in accessors via {@link AsyncContextInMemoryScopeManager}. Due to the read only nature of making the
 * tracing information available the {@link ThreadContext} map-like interface spirit is not strictly followed. This is
 * due to the fact that modifier methods (e.g. {@link #put(String, String)}, {@link #clear()}) will not have any impact
 * on this class returning tracing information from the accessor methods (e.g. {@link #get(String)}). The motivation for
 * this behavior is to avoid a tight coupling between changes to the tracing storage having to be replicated in this MDC
 * storage container. The mechanics to orchestrate this add non-negligible complexity/overhead and so this class
 * provides a trade-off to make the tracing information available in MDC.
 */
public final class ServiceTalkTracingThreadContextMap extends ServiceTalkThreadContextMap {
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String PARENT_SPAN_ID_KEY = "parentSpanId";

    @Nullable
    @Override
    public String get(String key) {
        // This Map implementation violates the Map contract in that it provides a "read only" view of the
        // AsyncContextInMemoryScopeManager to include tracing information in log statements. The "read only" portion
        // is because modifications to this MDC map (e.g. put, clear) will not modify the original
        // AsyncContextInMemoryScopeManager storage. The Map contract is therefore violated in other ways such as
        // isEmpty() may return true, but then get(..) may return elements from the trace.
        switch (key) {
            case TRACE_ID_KEY: {
                InMemoryScope scope = SCOPE_MANAGER.active();
                if (scope != null) {
                    return scope.span().traceIdHex();
                }
                break;
            }
            case SPAN_ID_KEY: {
                InMemoryScope scope = SCOPE_MANAGER.active();
                if (scope != null) {
                    return scope.span().spanIdHex();
                }
                break;
            }
            case PARENT_SPAN_ID_KEY: {
                InMemoryScope scope = SCOPE_MANAGER.active();
                if (scope != null) {
                    return scope.span().nonnullParentSpanIdHex();
                }
                break;
            }
            default:
                break;
        }
        return super.get(key);
    }

    @Override
    public boolean containsKey(String key) {
        return containsTracingKey(key) || super.containsKey(key);
    }

    private static boolean containsTracingKey(String key) {
        if (TRACE_ID_KEY.equals(key) || SPAN_ID_KEY.equals(key) || PARENT_SPAN_ID_KEY.equals(key)) {
            InMemoryScope scope = SCOPE_MANAGER.active();
            return scope != null;
        }
        return false;
    }

    @Override
    protected Map<String, String> getCopy(Map<String, String> storage) {
        return getCopy(storage, SCOPE_MANAGER.active());
    }

    private Map<String, String> getCopy(Map<String, String> storage, @Nullable InMemoryScope scope) {
        if (scope != null) {
            InMemorySpan span = scope.span();
            storage.put(TRACE_ID_KEY, span.traceIdHex());
            storage.put(SPAN_ID_KEY, span.spanIdHex());
            storage.put(PARENT_SPAN_ID_KEY, span.nonnullParentSpanIdHex());
        }
        return super.getCopy(storage);
    }

    @Nullable
    @Override
    protected Map<String, String> getImmutableMapOrNull(Map<String, String> storage) {
        InMemoryScope scope = SCOPE_MANAGER.active();
        if (storage.isEmpty() && scope == null) {
            return null;
        }
        // We use a ConcurrentMap for the storage. So we make a best effort check to avoid a copy first, but it is
        // possible that the map was modified after this check. Then we make a copy and check if the copy is actually
        // empty before returning the unmodifiable map.
        final Map<String, String> copy = getCopy(storage, scope);
        return copy.isEmpty() ? null : unmodifiableMap(copy);
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && SCOPE_MANAGER.active() == null;
    }

    @Override
    protected StringMap getReadOnlyContextData(Map<String, String> storage) {
        return new StringMap() {
            private static final long serialVersionUID = 6255838338202729246L;

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void freeze() {
            }

            @Override
            public boolean isFrozen() {
                return true;
            }

            @Override
            public void putAll(ReadOnlyStringMap source) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putValue(String key, Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void remove(String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, String> toMap() {
                return getCopy(storage, SCOPE_MANAGER.active());
            }

            @Override
            public boolean containsKey(String key) {
                return containsTracingKey(key) || storage.containsKey(key);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <V> void forEach(BiConsumer<String, ? super V> action) {
                InMemoryScope scope = SCOPE_MANAGER.active();
                if (scope != null) {
                    InMemorySpan span = scope.span();
                    action.accept(TRACE_ID_KEY, (V) span.traceIdHex());
                    action.accept(SPAN_ID_KEY, (V) span.spanIdHex());
                    action.accept(PARENT_SPAN_ID_KEY, (V) span.nonnullParentSpanIdHex());
                }
                storage.forEach((key, value) -> action.accept(key, (V) value));
            }

            @SuppressWarnings("unchecked")
            @Override
            public <V, S> void forEach(TriConsumer<String, ? super V, S> action, S state) {
                InMemoryScope scope = SCOPE_MANAGER.active();
                if (scope != null) {
                    InMemorySpan span = scope.span();
                    action.accept(TRACE_ID_KEY, (V) span.traceIdHex(), state);
                    action.accept(SPAN_ID_KEY, (V) span.spanIdHex(), state);
                    action.accept(PARENT_SPAN_ID_KEY, (V) span.nonnullParentSpanIdHex(), state);
                }
                storage.forEach((key, value) -> action.accept(key, (V) value, state));
            }

            @SuppressWarnings("unchecked")
            @Override
            public <V> V getValue(String key) {
                switch (key) {
                    case TRACE_ID_KEY: {
                        InMemoryScope scope = SCOPE_MANAGER.active();
                        if (scope != null) {
                            return (V) scope.span().traceIdHex();
                        }
                        break;
                    }
                    case SPAN_ID_KEY: {
                        InMemoryScope scope = SCOPE_MANAGER.active();
                        if (scope != null) {
                            return (V) scope.span().spanIdHex();
                        }
                        break;
                    }
                    case PARENT_SPAN_ID_KEY: {
                        InMemoryScope scope = SCOPE_MANAGER.active();
                        if (scope != null) {
                            return (V) scope.span().nonnullParentSpanIdHex();
                        }
                        break;
                    }
                    default:
                        break;
                }
                return (V) storage.get(key);
            }

            @Override
            public boolean isEmpty() {
                return storage.isEmpty() && SCOPE_MANAGER.active() == null;
            }

            @Override
            public int size() {
                InMemoryScope scope = SCOPE_MANAGER.active();
                return scope != null ? storage.size() + 3 : storage.size();
            }
        };
    }
}
