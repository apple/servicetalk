/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ContextMap}.
 * <p>
 * Note: it's not thread-safe!
 */
public final class DefaultContextMap implements ContextMap {

    private static final Key<?> CTOR_KEY = Key.newKey("CTOR_KEY", Object.class);

    private final HashMap<Key<?>, Object> theMap;
    private final ConcurrentMap<Key<?>, List<Throwable>> stacktraces = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     */
    public DefaultContextMap() {
        theMap = new HashMap<>(4); // start with a smaller table
        List<Throwable> list = stacktraces.computeIfAbsent(CTOR_KEY, __ -> new CopyOnWriteArrayList<>());
        list.add(new Throwable("new DefaultContextMap() on " + Thread.currentThread().getName() +
                " at " + System.nanoTime() +
                " for " + Integer.toHexString(System.identityHashCode(this))));
    }

    private DefaultContextMap(DefaultContextMap other) {
        theMap = new HashMap<>(other.theMap);
    }

    public List<Throwable> stacktrace(Key<?> key) {
        return stacktraces.get(key);
    }

    public List<Throwable> ctorStacktrace() {
        return stacktraces.get(CTOR_KEY);
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
        return theMap.containsKey(requireNonNull(key, "key"));
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return theMap.containsValue(value);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final Key<T> key) {
        return (T) theMap.get(requireNonNull(key, "key"));
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return (T) theMap.getOrDefault(requireNonNull(key, "key"), defaultValue);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T put(final Key<T> key, @Nullable final T value) {
        List<Throwable> list = stacktraces.computeIfAbsent(key, __ -> new CopyOnWriteArrayList<>());
        list.add(new Throwable("put(" + value + ") on " + Thread.currentThread().getName() +
                " at " + System.nanoTime() +
                " for " + Integer.toHexString(System.identityHashCode(this))));
        return (T) theMap.put(requireNonNull(key, "key"), value);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        final T oldVal = (T) theMap.putIfAbsent(requireNonNull(key, "key"), value);
        List<Throwable> list = stacktraces.computeIfAbsent(key, __ -> new CopyOnWriteArrayList<>());
        list.add(new Throwable("putIfAbsent(" + value + ")=" + oldVal +
                " on " + Thread.currentThread().getName() +
                " at " + System.nanoTime() +
                " for " + Integer.toHexString(System.identityHashCode(this))));
        return oldVal;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        return (T) theMap.computeIfAbsent(requireNonNull(key, "key"), k -> computeFunction.apply((Key<T>) k));
    }

    @Override
    public void putAll(final ContextMap map) {
        if (map instanceof DefaultContextMap) {
            final DefaultContextMap dcm = (DefaultContextMap) map;
            theMap.putAll(dcm.theMap);
        } else {
            ContextMap.super.putAll(map);
        }
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
        map.forEach(ContextMapUtils::ensureType);
        theMap.putAll(map);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T remove(final Key<T> key) {
        return (T) theMap.remove(requireNonNull(key, "key"));
    }

    @Override
    public void clear() {
        theMap.clear();
    }

    @Nullable
    @Override
    public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
        for (Map.Entry<Key<?>, Object> entry : theMap.entrySet()) {
            if (!consumer.test(entry.getKey(), entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public ContextMap copy() {
        return new DefaultContextMap(this);
    }

    @Override
    public int hashCode() {
        return theMap.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextMap)) {
            return false;
        }
        if (o instanceof DefaultContextMap) {
            return theMap.equals(((DefaultContextMap) o).theMap);
        }
        return ContextMapUtils.equals(this, (ContextMap) o);
    }

    @Override
    public String toString() {
        return ContextMapUtils.toString(this);
    }
}
