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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncContextMapUtils.contextMapToString;

final class ConcurrentAsyncContextMap implements AsyncContextMap {
    private final Map<AsyncContextMap.Key<?>, Object> theMap;

    ConcurrentAsyncContextMap() {
        theMap = new ConcurrentHashMap<>(4);
    }

    private ConcurrentAsyncContextMap(ConcurrentAsyncContextMap rhs) {
        theMap = new ConcurrentHashMap<>(rhs.theMap);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T get(final Key<T> key) {
        return (T) theMap.get(key);
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return theMap.containsKey(key);
    }

    @Override
    public boolean isEmpty() {
        return theMap.isEmpty();
    }

    @Override
    public int size() {
        return theMap.size();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T put(final Key<T> key, @Nullable final T value) {
        assert value != null;
        return (T) theMap.put(key, value);
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
        theMap.putAll(map);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T remove(final Key<T> key) {
        return (T) theMap.remove(key);
    }

    @Override
    public boolean removeAll(final Iterable<Key<?>> entries) {
        boolean removed = false;
        for (Key<?> k : entries) {
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
    public AsyncContextMap copy() {
        return new ConcurrentAsyncContextMap(this);
    }

    @Override
    public String toString() {
        return contextMapToString(this);
    }
}
