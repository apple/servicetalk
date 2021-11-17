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

import io.servicetalk.concurrent.internal.ContextMapUtils;
import io.servicetalk.context.api.ContextMap;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

// FIXME: 0.42 - rename to ConcurrentContextMap
final class ConcurrentAsyncContextMap implements ContextMap {

    private final ConcurrentMap<Key<?>, Object> theMap;

    ConcurrentAsyncContextMap() {
        theMap = new ConcurrentHashMap<>(4); // start with a smaller table
    }

    private ConcurrentAsyncContextMap(ConcurrentAsyncContextMap rhs) {
        theMap = new ConcurrentHashMap<>(rhs.theMap);
    }

    @Override
    public int size() {
        return theMap.size();
    }

    @Override
    public boolean isEmpty() {
        return theMap.isEmpty();
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return theMap.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        assert value != null;
        return theMap.containsValue(value);
    }

    @Override
    public <T> boolean contains(final Key<T> key, @Nullable final T value) {
        final T current = get(key);
        return current != null && current.equals(value);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final Key<T> key) {
        return (T) theMap.get(key);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return (T) theMap.getOrDefault(key, defaultValue);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T put(final Key<T> key, @Nullable final T value) {
        assert value != null;
        return (T) theMap.put(key, value);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        assert value != null;
        return (T) theMap.putIfAbsent(key, value);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        return (T) theMap.computeIfAbsent(key, k -> computeFunction.apply((Key<T>) k));
    }

    @Override
    public void putAll(final ContextMap map) {
        if (map instanceof ConcurrentAsyncContextMap) {
            final ConcurrentAsyncContextMap ccm = (ConcurrentAsyncContextMap) map;
            theMap.putAll(ccm.theMap);
        } else {
            ContextMap.super.putAll(map);
        }
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
        map.forEach(ContextMapUtils::ensureType);
        theMap.putAll(map);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T remove(final Key<T> key) {
        return (T) theMap.remove(key);
    }

    @Override
    public boolean removeAll(final Iterable<Key<?>> keys) {
        boolean removed = false;
        for (Key<?> k : keys) {
            // Null values aren't allowed so if a non-null value is seen then the map has been modified.
            removed |= theMap.remove(k) != null;
        }
        return removed;
    }

    @Override
    public void clear() {
        theMap.clear();
    }

    @Nullable
    @Override
    public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
        for (Entry<Key<?>, Object> entry : theMap.entrySet()) {
            if (!consumer.test(entry.getKey(), entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public ContextMap copy() {
        return new ConcurrentAsyncContextMap(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextMap)) {
            return false;
        }
        if (o instanceof ConcurrentAsyncContextMap) {
            return theMap.equals(((ConcurrentAsyncContextMap) o).theMap);
        }
        return ContextMapUtils.equals(this, (ContextMap) o);
    }

    @Override
    public int hashCode() {
        return theMap.hashCode();
    }

    @Override
    public String toString() {
        return ContextMapUtils.toString(this);
    }
}
