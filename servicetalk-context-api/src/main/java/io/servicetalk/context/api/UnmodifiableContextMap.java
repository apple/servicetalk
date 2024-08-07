/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.context.api;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class UnmodifiableContextMap implements ContextMap {

    private final ContextMap map;

    UnmodifiableContextMap(final ContextMap map) {
        this.map = requireNonNull(map);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return map.containsValue(value);
    }

    @Override
    public <T> boolean contains(final Key<T> key, @Nullable final T value) {
        return map.contains(key, value);
    }

    @Nullable
    @Override
    public <T> T get(final Key<T> key) {
        return map.get(key);
    }

    @Nullable
    @Override
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Nullable
    @Override
    public <T> T put(final Key<T> key, @Nullable final T value) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(final ContextMap map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> T remove(final Key<T> key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(final Iterable<Key<?>> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
        return map.forEach(consumer);
    }

    @Override
    public ContextMap copy() {
        return new UnmodifiableContextMap(map.copy());
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o == this || map.equals(o);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
