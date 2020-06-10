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
package io.servicetalk.log4j2.mdc.utils;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.CleanableThreadContextMap;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static java.util.Collections.unmodifiableMap;

/**
 * A {@link ThreadContext} that provides storage for MDC based upon {@link AsyncContext}.
 */
public class ServiceTalkThreadContextMap implements ReadOnlyThreadContextMap, CleanableThreadContextMap {
    private static final Key<Map<String, String>> key = Key.newKey("log4j2Mdc");
    @SuppressWarnings("RedundantStringConstructorCall")
    private static final String NULL_STRING = new String("");

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
        AsyncContextMap context = AsyncContext.current();
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
