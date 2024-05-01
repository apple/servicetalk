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

import javax.annotation.Nullable;

import static io.servicetalk.context.api.ContextMap.Key;

/**
 * Utility class to create or operate with {@link ContextMap}s.
 */
public final class ContextMaps {

    private ContextMaps() {
        // No instances.
    }

    /**
     * Returns an empty {@link ContextMap} (immutable).
     * <p>
     * Any attempt to modify the returned {@link ContextMap} DOES NOT result in an
     * {@link UnsupportedOperationException}, those are simply ignored.
     *
     * @return an empty {@link ContextMap}.
     */
    public static ContextMap emptyMap() {
        return EmptyContextMap.INSTANCE;
    }

    /**
     * Creates a new {@link ContextMap} backed by a {@link java.util.Map}.
     * <p>
     * Note: it's not thread-safe!
     *
     * @return a new {@link ContextMap}.
     */
    public static ContextMap newDefaultMap() {
        return new DefaultContextMap();
    }

    /**
     * Creates a new {@link ContextMap} backed by a {@link java.util.concurrent.ConcurrentMap}.
     * <p>
     * Note: this implementation is thread-safe.
     *
     * @return a new {@link ContextMap}.
     */
    public static ContextMap newConcurrentMap() {
        return new ConcurrentContextMap();
    }

    /**
     * Creates a new {@link ContextMap} that provides a Copy-on-Write map behavior.
     * <p>
     * Note: this implementation is thread-safe.
     *
     * @return a new {@link ContextMap}.
     */
    public static ContextMap newCopyOnWriteMap() {
        return new CopyOnWriteContextMap();
    }

    /**
     * Creates a new {@link ContextMap} that is immutable and holds only a single {@link Key}-value pair.
     * <p>
     * Any attempt to modify the returned {@link ContextMap} result in an {@link UnsupportedOperationException}.
     *
     * @param <T> The type of value associated with a {@link Key}.
     * @param key the {@link ContextMap.Key} to be stored.
     * @param value the value to be stored.
     * @return a new {@link ContextMap}.
     */
    public static <T> ContextMap singletonMap(final ContextMap.Key<T> key, final @Nullable T value) {
        return new SingletonContextMap(key, value);
    }

    /**
     * Returns an unmodifiable view of the specified {@link ContextMap}.
     * <p>
     * Query operations on the returned map "read through" to the specified map. Any attempt to modify the returned
     * map result in an {@link UnsupportedOperationException}.
     *
     * @param map the {@link ContextMap} for which an unmodifiable view is to be returned.
     * @return an unmodifiable view of the specified {@link ContextMap}.
     */
    public static ContextMap unmodifiableMap(final ContextMap map) {
        if (map instanceof UnmodifiableContextMap) {
            return map;
        }
        return new UnmodifiableContextMap(map);
    }
}
