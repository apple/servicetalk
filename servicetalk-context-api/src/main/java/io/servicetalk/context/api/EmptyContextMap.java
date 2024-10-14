/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

final class EmptyContextMap implements ContextMap {
    static final ContextMap INSTANCE = new EmptyContextMap();

    private EmptyContextMap() {
        // Singleton
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return false;
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return false;
    }

    @Override
    public <T> boolean contains(final Key<T> key, @Nullable final T value) {
        return false;
    }

    @Nullable
    @Override
    public <T> T get(final Key<T> key) {
        return null;
    }

    @Override
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return defaultValue;
    }

    @Nullable
    @Override
    public <T> T put(final Key<T> key, @Nullable final T value) {
        return null;
    }

    @Nullable
    @Override
    public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        return null;
    }

    @Nullable
    @Override
    public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        return null;
    }

    @Override
    public void putAll(final ContextMap map) {
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
    }

    @Nullable
    @Override
    public <T> T remove(final Key<T> key) {
        return null;
    }

    @Override
    public boolean removeAll(final Iterable<Key<?>> keys) {
        return false;
    }

    @Override
    public void clear() {
    }

    @Nullable
    @Override
    public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
        return null;
    }

    @Override
    public ContextMap copy() {
        return this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextMap)) {
            return false;
        }
        return ((ContextMap) o).isEmpty();
    }

    @Override
    public String toString() {
        return ContextMapUtils.toString(this);
    }
}
