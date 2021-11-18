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
package io.servicetalk.log4j2.mdc.utils;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.context.api.ContextMap;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.CleanableThreadContextMap;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static java.util.Collections.unmodifiableMap;

/**
 * A {@link ThreadContext} that provides storage for MDC based upon {@link AsyncContext}.
 */
public class ServiceTalkThreadContextMap implements ReadOnlyThreadContextMap, CleanableThreadContextMap {
    @SuppressWarnings("unchecked")
    private static final ContextMap.Key<Map<String, String>> key = newKey("log4j2Mdc",
            (Class<Map<String, String>>) (Class<?>) Map.class);
    private static final String NULL_STRING = "";
    private static final String[] KNOWN_CONFLICTS = {
            "io.servicetalk.log4j2.mdc.DefaultServiceTalkThreadContextMap",
            "io.servicetalk.opentracing.log4j2.ServiceTalkTracingThreadContextMap",
    };
    private static final String ADAPTER_CONFLICT_MESSAGE = "Detected multiple ServiceTalk MDC adapters in classpath." +
            " The %s MDC adapters should not be" +
            " loaded at the same time. Please exclude one from your dependencies.%n";

    public ServiceTalkThreadContextMap() {
        detectPossibleConflicts();
    }

    private void detectPossibleConflicts() {
        List<String> found = new ArrayList<>(KNOWN_CONFLICTS.length);
        for (String cls : KNOWN_CONFLICTS) {
            try {
                Class.forName(cls);
                found.add(cls);
            } catch (ClassNotFoundException ex) {
                // Not found - continue
            }
        }

        if (found.size() > 1) {
            System.err.printf((ADAPTER_CONFLICT_MESSAGE), Arrays.toString(found.toArray()));
            // Log4j's provider interface provides no formal way to declare ordering or precedence if there are multiple
            // provides which may overlap. Currently log4j2's ThreadContextMapFactory gives precedent to the logger
            // specified via system property [1] so we check for this as a "best effort" to detect that this class has
            // precedence and will be used for MDC storage.
            // [1] https://logging.apache.org/log4j/2.0/log4j-api/apidocs/org/apache/logging/log4j/spi/ThreadContextMapFactory.html
            if (!System.getProperty("log4j2.threadContextMap", "")
                    .equals(this.getClass().getCanonicalName())) {
                throw new IllegalStateException("log4j2.threadContextMap is not set to " +
                        this.getClass().getCanonicalName() + ". There is no way to determine" +
                        " which ThreadContextMap has precedence for MDC storage and behavior is therefore undefined.");
            }
            // Do NOT use log4j2 to log here as it can cause some reentrancy problem
        }
    }

    @Override
    public final void put(String key, String value) {
        getStorage().put(key, wrapNull(value));
    }

    @Nullable
    @Override
    public String get(String key) {
        return unwrapNull(getStorage().get(key));
    }

    @Override
    public final void remove(String key) {
        getStorage().remove(key);
    }

    @Override
    public final void clear() {
        getStorage().clear();
    }

    @Override
    public boolean containsKey(String key) {
        return getStorage().containsKey(key);
    }

    @Override
    public Map<String, String> getCopy() {
        return getCopy(getStorage());
    }

    @Nullable
    @Override
    public Map<String, String> getImmutableMapOrNull() {
        Map<String, String> storage = getStorage();
        if (storage.isEmpty()) {
            return null;
        }
        // We use a ConcurrentMap for the storage. So we make a best effort check to avoid a copy first, but it is
        // possible that the map was modified after this check. Then we make a copy and check if the copy is actually
        // empty before returning the unmodifiable map.
        final Map<String, String> copy = getCopyOrNull(storage, true);
        return copy == null ? null : unmodifiableMap(copy);
    }

    @Override
    public boolean isEmpty() {
        return getStorage().isEmpty();
    }

    @Override
    public final void removeAll(Iterable<String> keys) {
        final Map<String, String> storage = getStorage();
        keys.forEach(storage::remove);
    }

    @Override
    public final void putAll(Map<String, String> map) {
        final Map<String, String> storage = getStorage();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            storage.put(entry.getKey(), wrapNull(entry.getValue()));
        }
    }

    @Override
    public StringMap getReadOnlyContextData() {
        final Map<String, String> storage = getStorage();
        return new StringMap() {
            private static final long serialVersionUID = -1707426073379541244L;

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
                return getCopy(storage);
            }

            @Override
            public boolean containsKey(String key) {
                return storage.containsKey(key);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <V> void forEach(BiConsumer<String, ? super V> action) {
                storage.forEach((key, value) -> action.accept(key, (V) unwrapNull(value)));
            }

            @SuppressWarnings("unchecked")
            @Override
            public <V, S> void forEach(TriConsumer<String, ? super V, S> action, S state) {
                storage.forEach((key, value) -> action.accept(key, (V) unwrapNull(value), state));
            }

            @Nullable
            @SuppressWarnings("unchecked")
            @Override
            public <V> V getValue(String key) {
                return (V) unwrapNull(storage.get(key));
            }

            @Override
            public boolean isEmpty() {
                return storage.isEmpty();
            }

            @Override
            public int size() {
                return storage.size();
            }
        };
    }

    @Nullable
    protected Map<String, String> getCopyOrNull() {
        return getCopyOrNull(getStorage(), true);
    }

    static Map<String, String> getStorage() {
        final ContextMap context = AsyncContext.context();
        Map<String, String> ret = context.get(key);
        if (ret == null) {
            // better be thread safe, since the context may be used in multiple operators which may use different
            // threads MDC is typically small (e.g. <8) so start with 4 (which ConcurrentHashMap will double to 8).
            ret = new ConcurrentHashMap<>(4);
            AsyncContext.put(key, ret);
        }
        return ret;
    }

    @Nullable
    private static Map<String, String> getCopyOrNull(Map<String, String> storage, boolean emptyReturnNull) {
        final int size = storage.size();
        if (size == 0) {
            return emptyReturnNull ? null : new HashMap<>(2);
        }
        Map<String, String> copy = new HashMap<>(size + (int) (size * 0.25f + 1), 0.75f);
        for (Map.Entry<String, String> entry : storage.entrySet()) {
            copy.put(entry.getKey(), unwrapNull(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, String> getCopy(Map<String, String> storage) {
        Map<String, String> copy = getCopyOrNull(storage, false);
        assert copy != null;
        return copy;
    }

    private static String wrapNull(@Nullable String value) {
        return value == null ? NULL_STRING : value;
    }

    @SuppressWarnings("StringEquality")
    @Nullable
    private static String unwrapNull(String value) {
        return value == NULL_STRING ? null : value;
    }
}
