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
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class SingletonContextMap implements ContextMap {

    private final Key<?> key;
    @Nullable
    private final Object value;

    <T> SingletonContextMap(final Key<T> key, final @Nullable T value) {
        this.key = requireNonNull(key);
        this.value = value;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return this.key.equals(key);
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return Objects.equals(this.value, value);
    }

    @Override
    public <T> boolean contains(final Key<T> key, @Nullable final T value) {
        return containsKey(key) && containsValue(value);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final Key<T> key) {
        return this.key.equals(key) ? (T) value : null;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return this.key.equals(key) ? (T) value : defaultValue;
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
        consumer.test(key, value);
        return null;
    }

    @Override
    public ContextMap copy() {
        return this;
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SingletonContextMap that = (SingletonContextMap) o;
        return key.equals(that.key) && Objects.equals(value, that.value);
    }

    @Override
    public String toString() {
        return ContextMapUtils.toString(this);
    }
}
