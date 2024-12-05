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
package io.servicetalk.concurrent.internal;

import io.servicetalk.context.api.ContextMap;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class ReadOnlyContextMap implements ContextMap {

    private final ContextMap delegate;

    public ReadOnlyContextMap(final ContextMap delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return delegate.containsValue(value);
    }

    @Nullable
    @Override
    public <T> T get(final Key<T> key) {
        return delegate.get(key);
    }

    @Nullable
    @Override
    public <T> T put(final Key<T> key, @Nullable final T value) {
        throw isReadOnly();
    }

    @Nullable
    @Override
    public <T> T remove(final Key<T> key) {
        throw isReadOnly();
    }

    @Override
    public void clear() {
        throw isReadOnly();
    }

    @Nullable
    @Override
    public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
        return delegate.forEach(consumer);
    }

    @Override
    public ContextMap copy() {
        return delegate.copy();
    }

    @Override
    public boolean removeAll(final Iterable<Key<?>> keys) {
        throw isReadOnly();
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
        throw isReadOnly();
    }

    @Override
    public void putAll(final ContextMap map) {
        throw isReadOnly();
    }

    @Nullable
    @Override
    public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        throw isReadOnly();
    }

    @Nullable
    @Override
    public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        throw isReadOnly();
    }

    @Nullable
    @Override
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public <T> boolean contains(final Key<T> key, @Nullable final T value) {
        return delegate.contains(key, value);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    private static RuntimeException isReadOnly() {
        return new UnsupportedOperationException("This ContextMap is read-only");
    }
}
