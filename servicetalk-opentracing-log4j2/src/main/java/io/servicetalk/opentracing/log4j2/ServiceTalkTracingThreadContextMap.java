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
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.JdkMapAdapterStringMap;
import org.apache.logging.log4j.util.StringMap;

import java.util.HashMap;
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
                InMemorySpan span = SCOPE_MANAGER.activeSpan();
                if (span != null) {
                    return span.traceIdHex();
                }
                break;
            }
            case SPAN_ID_KEY: {
                InMemorySpan span = SCOPE_MANAGER.activeSpan();
                if (span != null) {
                    return span.spanIdHex();
                }
                break;
            }
            case PARENT_SPAN_ID_KEY: {
                InMemorySpan span = SCOPE_MANAGER.activeSpan();
                if (span != null) {
                    return span.nonnullParentSpanIdHex();
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

    @Override
    public Map<String, String> getCopy() {
        Map<String, String> copy = super.getCopy();
        InMemorySpan span = SCOPE_MANAGER.activeSpan();
        if (span != null) {
            copy.put(TRACE_ID_KEY, span.traceIdHex());
            copy.put(SPAN_ID_KEY, span.spanIdHex());
            copy.put(PARENT_SPAN_ID_KEY, span.nonnullParentSpanIdHex());
        }
        return copy;
    }

    @Nullable
    @Override
    public Map<String, String> getImmutableMapOrNull() {
        Map<String, String> copy = getCopyOrNull();
        return copy == null ? null : unmodifiableMap(copy);
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && SCOPE_MANAGER.activeSpan() == null;
    }

    @Override
    public StringMap getReadOnlyContextData() {
        StringMap map = new JdkMapAdapterStringMap(getCopy());
        map.freeze();
        return map;
    }

    @Override
    @Nullable
    protected Map<String, String> getCopyOrNull() {
        InMemorySpan span = SCOPE_MANAGER.activeSpan();
        Map<String, String> copy = super.getCopyOrNull();
        if (copy == null && span == null) {
            return null;
        }
        if (copy == null) {
            copy = new HashMap<>(4);
        }
        if (span != null) {
            copy.put(TRACE_ID_KEY, span.traceIdHex());
            copy.put(SPAN_ID_KEY, span.spanIdHex());
            copy.put(PARENT_SPAN_ID_KEY, span.nonnullParentSpanIdHex());
        }
        return copy;
    }

    private static boolean containsTracingKey(String key) {
        return (TRACE_ID_KEY.equals(key) || SPAN_ID_KEY.equals(key) || PARENT_SPAN_ID_KEY.equals(key)) &&
                SCOPE_MANAGER.activeSpan() != null; // defer SCOPE_MANAGER.active() because it may access a thread local
    }
}
