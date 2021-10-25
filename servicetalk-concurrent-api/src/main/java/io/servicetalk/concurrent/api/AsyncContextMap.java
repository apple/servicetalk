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

import io.servicetalk.context.api.ContextMap;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * The key-value map stored in the {@link AsyncContext}.
 *
 * @deprecated Use {@link ContextMap}.
 */
@Deprecated
public interface AsyncContextMap {

    /**
     * A key identifies a specific object in a {@link AsyncContextMap}.
     * @param <T> The type of value associated with a {@link AsyncContextMap.Key}.
     * @deprecated Use {@link ContextMap.Key} with {@link ContextMap}.
     */
    @Deprecated
    final class Key<T> {
        private final String stringRepresentation;

        private Key() {
            this.stringRepresentation = super.toString();
        }

        private Key(String stringRepresentation) {
            // Append the hashCode so it is clear that two instances created with the same stringRepresentation value
            // are different.
            this.stringRepresentation = requireNonNull(stringRepresentation) + '-' + hashCode();
        }

        /**
         * Create a new {@link Key} which has a {@link String} used only in the {@link #toString()} method for debugging
         * visibility.
         * <p>
         * Comparison between {@link Key} objects should be assumed to be on an instance basis.
         * In general {@code newKey(str) != newKey(str)}.
         * @param toString The value to use in {@link #toString()}. This <strong>WILL NOT</strong> be used in
         * comparisons between {@link Key} objects.
         * @param <T> The value type associated with the {@link Key}.
         * @return a new {@link Key} which has a {@link String} used only in the {@link #toString()} method for
         * debugging visibility.
         * @deprecated Use {@link ContextMap.Key#newKey(String, Class)}.
         */
        @Deprecated
        public static <T> Key<T> newKey(String toString) {
            return new Key<>(toString);
        }

        /**
         * Create a new instance.
         * <p>
         * Comparison between {@link Key} objects should be assumed to be on an instance basis.
         * In general {@code newKey() != newKey()}.
         * @param <T> The value type associated with the {@link Key}.
         * @return a new instance.
         * @deprecated Use {@link ContextMap.Key#newKey(String, Class)}.
         */
        @Deprecated
        public static <T> Key<T> newKey() {
            return new Key<>();
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }
    }

    /**
     * Get the value associated with {@code key}, or {@code null} if no value is associated.
     *
     * @param key the key to lookup.
     * @param <T> The anticipated type of object associated with {@code key}.
     * @return the value associated with {@code key}, or {@code null} if no value is associated. {@code null} can also
     * indicate the value associated with {@code key} is {@code null} (if {@code null} values are supported by the
     * implementation).
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the implementation doesn't
     * support {@code null} keys or values.
     */
    @Nullable
    <T> T get(AsyncContextMap.Key<T> key);

    /**
     * Determine if this context contains a key/value entry corresponding to {@code key}.
     *
     * @param key the key to lookup.
     * @return {@code true} if this context contains a key/value entry corresponding to {@code key}.
     * {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the implementation doesn't
     * support {@code null} keys or values.
     */
    boolean containsKey(AsyncContextMap.Key<?> key);

    /**
     * Determine if there are no key/value pairs in this {@link AsyncContextMap}.
     *
     * @return {@code true} if there are no key/value pairs in this {@link AsyncContextMap}.
     */
    boolean isEmpty();

    /**
     * Determine the number of {@link Key}-value mappings.
     * @return the number of {@link Key}-value mappings.
     */
    int size();

    /**
     * Put a new key/value pair into this {@link AsyncContextMap}.
     *
     * @param key   the key used to index {@code value}. Cannot be {@code null}.
     * @param value the value to put.
     * @param <T>   The type of object associated with {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with {@code key} is {@code null} (if {@code null} values are supported
     * by the implementation).
     * @throws NullPointerException if {@code key} or {@code value} is {@code null} and the implementation doesn't
     * support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    @Nullable
    <T> T put(AsyncContextMap.Key<T> key, @Nullable T value);

    /**
     * Put all the key/value pairs into this {@link AsyncContextMap}.
     *
     * @param map The entries to insert into this {@link AsyncContextMap}.
     * @throws ConcurrentModificationException done on a best effort basis if {@code entries} is detected to be modified
     * while attempting to put all entries.
     * @throws NullPointerException if {@code key}s or {@code value}s from {@code map} are {@code null} and the
     * implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    void putAll(Map<AsyncContextMap.Key<?>, Object> map);

    /**
     * Remove a key/value pair from this {@link AsyncContextMap}, and get the previous value (if one exists).
     *
     * @param key The key which identifies the key/value to remove.
     * @param <T> The type of object associated with {@code key}.
     * @return the previous value associated with {@code key}, or {@code null} if there was none. A {@code null}
     * value may also indicate there was a previous value which was {@code null}.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    @Nullable
     <T> T remove(AsyncContextMap.Key<T> key);

    /**
     * Remove all key/value pairs from this {@link AsyncContextMap} associated with the keys from the {@link Iterable}.
     *
     * @param entries The entries to remove from this {@link AsyncContextMap}.
     * @return {@code true} if this map has changed as a result of this operation.
     * @throws ConcurrentModificationException Done on a best effort basis if {@code entries} is detected to be modified
     * while attempting to remove all entries.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    boolean removeAll(Iterable<AsyncContextMap.Key<?>> entries);

    /**
     * Clear the contents of this {@link AsyncContextMap}.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    void clear();

    /**
     * Iterate over the key/value pairs contained in this request context.
     *
     * @param consumer Each key/value pair will be passed as arguments to this {@link BiPredicate}. Returns {@code true}
     * if the consumer wants to keep iterating or {@code false} to stop iteration at the current key/value pair.
     * @return {@code null} if {@code consumer} iterated through all key/value pairs or the {@link AsyncContextMap.Key}
     * at which the iteration stopped.
     * @throws NullPointerException if {@code consumer} is null.
     */
    @Nullable
    AsyncContextMap.Key<?> forEach(BiPredicate<Key<?>, Object> consumer);

    /**
     * Create an isolated copy of the current map. The return value contents are the same as this
     * {@link AsyncContextMap} but modifications to this {@link AsyncContextMap} are not visible in the return value,
     * and visa-versa.
     *
     * @return an isolated copy of the current map. The contents are the same as this {@link AsyncContextMap} but
     * modifications to this {@link AsyncContextMap} are not visible in the return value, and visa-versa.
     */
    AsyncContextMap copy();
}
