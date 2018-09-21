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
package io.servicetalk.opentracing.log4j.internal;

import io.servicetalk.opentracing.core.internal.InMemorySpan;
import io.servicetalk.opentracing.core.internal.ListenableInMemoryScopeManager.InMemorySpanChangeListener;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static java.util.Collections.unmodifiableSet;

/**
 * A {@link InMemorySpanChangeListener} that uses {@link ThreadContext} operations. This can provide a benefit over {@link MDC}
 * because the modifications can be done in bulk via {@link ThreadContext#putAll(Map)} and
 * {@link ThreadContext#removeAll(Iterable)}.
 */
public final class ThreadContextMapListenerInMemory implements InMemorySpanChangeListener {
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String PARENT_SPAN_ID_KEY = "parentSpanId";
    private static final Iterable<String> KEYS_ITERABLE = new Iterable<String>() {
        @Override
        public Iterator<String> iterator() {
            return new Iterator<String>() {
                private int i;
                @Override
                public boolean hasNext() {
                    return i < 3;
                }

                @Override
                public String next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    switch (++i) {
                        case 1: return TRACE_ID_KEY;
                        case 2: return SPAN_ID_KEY;
                        case 3: return PARENT_SPAN_ID_KEY;
                        default: throw new NoSuchElementException();
                    }
                }
            };
        }

        @Override
        public void forEach(Consumer<? super String> action) {
            action.accept(TRACE_ID_KEY);
            action.accept(SPAN_ID_KEY);
            action.accept(PARENT_SPAN_ID_KEY);
        }
    };
    private static final Set<String> KEY_SET = unmodifiableSet(new HashSet<>(Arrays.asList(TRACE_ID_KEY, SPAN_ID_KEY,
            PARENT_SPAN_ID_KEY)));
    public static final ThreadContextMapListenerInMemory INSTANCE = new ThreadContextMapListenerInMemory();

    private ThreadContextMapListenerInMemory() {
    }

    @Override
    public void spanChanged(@Nullable InMemorySpan oldSpan, @Nullable InMemorySpan newSpan) {
        // If there was no oldSpan, or oldSpan and newSpan are of the same "type class" then we can directly
        // put/replace.
        if (oldSpan == null && newSpan != null) {
            putSpanInThreadContext(newSpan);
        } else {
            // oldSpan and newSpan are of different type classes, and we must remove then put.
            ThreadContext.removeAll(KEYS_ITERABLE);
            if (newSpan != null) {
                putSpanInThreadContext(newSpan);
            }
        }
    }

    private static void putSpanInThreadContext(InMemorySpan span) {
        // The hex values are calculated on demand, so cache them here to avoid re-calculation.
        final String traceId = span.traceIdHex();
        final String spanId = span.spanIdHex();
        final String parentSpanId = span.nonnullParentSpanIdHex();
        // This map is optimized for forEach iteration! Member variables and caching are kept to a minimum to keep
        // memory footprint low for methods that are not expected to be used.
        ThreadContext.putAll(new Map<String, String>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                return unmodifiableSet(new HashSet<>(Arrays.asList(
                        new SimpleEntry<>(TRACE_ID_KEY, traceId),
                        new SimpleEntry<>(SPAN_ID_KEY, spanId),
                        new SimpleEntry<>(PARENT_SPAN_ID_KEY, parentSpanId))));
            }

            @Override
            public void forEach(BiConsumer<? super String, ? super String> consumer) {
                consumer.accept(TRACE_ID_KEY, traceId);
                consumer.accept(SPAN_ID_KEY, spanId);
                consumer.accept(PARENT_SPAN_ID_KEY, parentSpanId);
            }

            @Override
            public int size() {
                return 3;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean containsKey(Object key) {
                return TRACE_ID_KEY.equals(key) || SPAN_ID_KEY.equals(key) || PARENT_SPAN_ID_KEY.equals(key);
            }

            @Override
            public boolean containsValue(Object value) {
                return traceId.equals(value) || spanId.equals(value) || parentSpanId.equals(value);
            }

            @Nullable
            @Override
            public String get(Object key) {
                return TRACE_ID_KEY.equals(key) ? traceId :
                        SPAN_ID_KEY.equals(key) ? spanId :
                                PARENT_SPAN_ID_KEY.equals(key) ? parentSpanId :
                                        null;
            }

            @Override
            public String put(String key, String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String remove(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putAll(Map<? extends String, ? extends String> m) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<String> keySet() {
                return KEY_SET;
            }

            @Override
            public Collection<String> values() {
                return unmodifiableSet(new HashSet<>(Arrays.asList(traceId, spanId, parentSpanId)));
            }
        });
    }
}
