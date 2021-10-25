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

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.lang.Integer.toHexString;
import static java.util.Objects.requireNonNull;

/**
 * The key-value map for different types of the value, defined by the {@link Key}.
 */
public interface ContextMap {
    /**
     * A key identifies a specific object in a {@link ContextMap}.
     * <p>
     * Comparison between {@link Key} objects should be assumed to be on an instance basis.
     * In general, {@code newKey(strA, typeA) != newKey(strA, typeA)}.
     *
     * @param <T> The type of value associated with a {@link Key}.
     */
    final class Key<T> {

        private final String name;
        private final Class<T> type;

        private Key(final String name, final Class<T> type) {
            this.name = requireNonNull(name);
            this.type = requireNonNull(type);
        }

        /**
         * Returns the name of the key.
         *
         * @return the name of the key.
         */
        public String name() {
            return name;
        }

        /**
         * Returns the type of the key.
         *
         * @return the type of the key.
         */
        public Class<T> type() {
            return type;
        }

        /**
         * Creates a new {@link Key} with the specified name and type.
         *
         * @param name The name of the key. This <strong>WILL NOT</strong> be used in comparisons between {@link Key}
         * objects.
         * @param type The type of the key. This <strong>WILL NOT</strong> be used in comparisons between {@link Key}
         * objects.
         * @param <T> The value type associated with the {@link Key}.
         * @return a new {@link Key} which has a {@link String} used only in the {@link #toString()} method for
         * debugging visibility.
         */
        public static <T> Key<T> newKey(final String name, final Class<T> type) {
            return new Key<>(name, type);
        }

        @Override
        public String toString() {
            return "ContextMap.Key{" +
                    "name='" + name + '\'' +
                    ", type=" + type.getSimpleName() +
                    "}@" + toHexString(hashCode());
        }
    }

    /**
     * Determine the number of {@link Key}-value pairs in this {@link ContextMap}.
     *
     * @return the number of {@link Key}-value pairs in this {@link ContextMap}.
     */
    int size();

    /**
     * Determine if there are no key-value pairs in this {@link ContextMap}.
     *
     * @return {@code true} if there are no key-value pairs in this {@link ContextMap}.
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Determine if this {@link ContextMap} contains a key-value pair corresponding to the {@code key}.
     *
     * @param key The {@link Key} to lookup.
     * @return {@code true} if this {@link ContextMap} contains a key-value pair corresponding to the {@code key},
     * {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the implementation doesn't
     * support {@code null} keys.
     */
    boolean containsKey(Key<?> key);

    /**
     * Determine if this {@link ContextMap} contains a key-value pair with the specified {@code value}.
     *
     * @param value The {@code value} to lookup.
     * @return {@code true} if this {@link ContextMap} contains one or more a key-value entries with the specified
     * {@code value}, {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code value} is {@code null} and the implementation doesn't
     * support {@code null} values.
     */
    boolean containsValue(@Nullable Object value);

    /**
     * Determine if this {@link ContextMap} contains a key-value pair matching the passed {@code key} and {@code value}.
     *
     * @param key The {@link Key} to lookup.
     * @param value The value to match.
     * @param <T> The anticipated type of object associated with the {@code key}.
     * @return {@code true} if this {@link ContextMap} contains a key-value pair matching the passed {@code key} and
     * {@code value}, {@code false} otherwise.
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * implementation doesn't support {@code null} keys or values.
     */
    default <T> boolean contains(final Key<T> key, @Nullable final T value) {
        final T current = get(key);
        return current != null ? current.equals(value) : (value == null && containsKey(key));
    }

    /**
     * Get the value associated with the {@code key}, or {@code null} if no value is associated.
     *
     * @param key The {@link Key} to lookup.
     * @param <T> The anticipated type of object associated with the {@code key}.
     * @return The value associated with the {@code key}, or {@code null} if no value is associated. {@code null} can
     * also indicate the value associated with the {@code key} is {@code null} (if {@code null} values are supported by
     * the implementation).
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the implementation doesn't
     * support {@code null} keys.
     */
    @Nullable
    <T> T get(Key<T> key);

    /**
     * Get the value associated with the {@code key}, or {@code defaultValue} if no value is associated.
     *
     * @param key The {@link Key} to lookup.
     * @param defaultValue The value to return if no value is associated with the {@code key}.
     * @param <T> The anticipated type of object associated with the {@code key}.
     * @return The value associated with the {@code key} (can return {@code null} if {@code null} values are supported
     * by the implementation), or {@code defaultValue} if no value is associated.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the implementation doesn't
     * support {@code null} keys.
     */
    @Nullable
    default <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        final T current = get(key);
        return ((current != null) || containsKey(key)) ? current : defaultValue;
    }

    /**
     * Put a new key-value pair into this {@link ContextMap}.
     *
     * @param key The {@link Key} used to index the {@code value}.
     * @param value The value to put.
     * @param <T> The type of object associated with the {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with the {@code key} was {@code null} (if {@code null} values are
     * supported by the implementation).
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    @Nullable
    <T> T put(Key<T> key, @Nullable T value);

    /**
     * Put a new key-value pair into this {@link ContextMap} if this map does not already contain this {@code key} or is
     * mapped to {@code null}.
     *
     * @param key The {@link Key} used to index the {@code value}.
     * @param value The value to put.
     * @param <T> The type of object associated with the {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with the {@code key} was {@code null} (if {@code null} values are
     * supported by the implementation).
     * @throws NullPointerException (optional behavior) if {@code key} or {@code value} is {@code null} and the
     * implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    @Nullable
    default <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        T prev = get(key);
        if (prev == null) {
            prev = put(key, value);
        }

        return prev;
    }

    /**
     * Computes a new key-value pair for this {@link ContextMap} if this map does not already contain this {@code key}
     * or is mapped to {@code null}.
     *
     * @param key The {@link Key} used to index a new value.
     * @param computeFunction The function to compute a new value. Implementation may invoke this function multiple
     * times if concurrent threads attempt modifying this context map, result is expected to be idempotent.
     * @param <T> The type of object associated with the {@code key}.
     * @return The current (existing or computed) value associated with the {@code key}, or {@code null} if the computed
     * value is {@code null}.
     * @throws NullPointerException (optional behavior) if {@code key} or computed {@code value} is {@code null} and the
     * implementation doesn't support {@code null} keys or values.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    @Nullable
    default <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        requireNonNull(computeFunction);
        T value;
        if ((value = get(key)) == null) {
            T newValue;
            if ((newValue = computeFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }

        return value;
    }

    /**
     * Put all the key-value pairs into this {@link ContextMap}.
     *
     * @param map The entries to insert into this {@link ContextMap}.
     * @throws IllegalArgumentException if any value type does not match with its corresponding {@link Key#type()}.
     * @throws NullPointerException (optional behavior) if any of the {@code map} entries has a {@code null} {@code key}
     * or {@code value} and the implementation doesn't support {@code null} keys or values.
     * @throws ConcurrentModificationException done on a best effort basis if {@code entries} is detected to be modified
     * while attempting to put all entries.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    default void putAll(final ContextMap map) {
        map.forEach((k, v) -> {
            //noinspection unchecked
            put((Key<? super Object>) k, v);
            return true;
        });
    }

    /**
     * Put all the key-value pairs into this {@link ContextMap}.
     *
     * @param map The entries to insert into this {@link ContextMap}.
     * @throws IllegalArgumentException if any value type does not match with its corresponding {@link Key#type()}.
     * @throws NullPointerException (optional behavior) if any of the {@code map} entries has a {@code null} {@code key}
     * or {@code value} and the implementation doesn't support {@code null} keys or values.
     * @throws ConcurrentModificationException done on a best effort basis if {@code entries} is detected to be modified
     * while attempting to put all entries.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    default void putAll(final Map<Key<?>, Object> map) {
        map.forEach((key, value) -> {
            requireNonNull(key);
            if (value != null && !key.type().isInstance(value)) {
                throw new IllegalArgumentException("Type of the value " + value + '(' + value.getClass() + ')' +
                        " does mot match with " + key);
            }
        });
        // Run another forEach to make sure we add all or nothing
        //noinspection unchecked
        map.forEach((key, value) -> put((Key<? super Object>) key, value));
    }

    /**
     * Remove a key-value pair from this {@link ContextMap}, and get the previous value (if one exists).
     *
     * @param key The {@link Key} which identifies a key-value pair for removal.
     * @param <T> The type of object associated with the {@code key}.
     * @return The previous value associated with the {@code key}, or {@code null} if there was none. {@code null} can
     * also indicate the value associated value with the {@code key} was {@code null} (if {@code null} values are
     * supported by the implementation).
     * If the {@link ContextMap} implementation is immutable this may be a new object.
     * @throws NullPointerException (optional behavior) if {@code key} is {@code null} and the implementation doesn't
     * support {@code null} keys.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    @Nullable
    <T> T remove(Key<T> key);

    /**
     * Remove all key-value pairs from this {@link ContextMap} associated with the keys from the passed
     * {@link Iterable}.
     *
     * @param keys The {@link Key}s that identify key-value pairs for removal.
     * @return {@code true} if this map has changed as a result of this operation.
     * @throws NullPointerException (optional behavior) if any of the {@code keys} is {@code null} and the
     * implementation doesn't support {@code null} keys.
     * @throws ConcurrentModificationException Done on a best effort basis if {@code entries} is detected to be modified
     * while attempting to remove all entries.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    default boolean removeAll(Iterable<Key<?>> keys) {
        boolean removed = false;
        for (Key<?> k : keys) {
            final boolean contains = containsKey(k);    // have to query because `null` values are allowed
            removed |= contains;
            if (contains) {
                remove(k);
            }
        }
        return removed;
    }

    /**
     * Clear the contents of this {@link ContextMap}.
     *
     * @throws UnsupportedOperationException if this method is not supported.
     */
    void clear();

    /**
     * Iterate over the key-value pairs contained in this {@link ContextMap}.
     *
     * @param consumer Each key-value pair will be passed as arguments to this {@link BiPredicate}. Returns {@code true}
     * if the consumer wants to keep iterating or {@code false} to stop iteration at the current key-value pair.
     * @return {@code null} if {@code consumer} iterated through all key-value pairs or the {@link Key} at which the
     * iteration stopped.
     * @throws NullPointerException if {@code consumer} is {@code null}.
     */
    @Nullable
    Key<?> forEach(BiPredicate<Key<?>, Object> consumer);

    /**
     * Create an isolated copy of the current map. The return value contents are the same as this
     * {@link ContextMap} but modifications to this {@link ContextMap} are not visible in the return value,
     * and visa-versa.
     *
     * @return an isolated copy of the current map. The contents are the same as this {@link ContextMap} but
     * modifications to this {@link ContextMap} are not visible in the return value, and visa-versa.
     */
    ContextMap copy();

    /**
     * Determines if the specified object is equal to this {@link ContextMap}.
     *
     * @param o object to be compared for equality with this {@link ContextMap}.
     * @return {@code true} if the passed object is a {@link ContextMap} and has the same key-value mappings.
     * @see #hashCode()
     */
    @Override
    boolean equals(Object o);

    /**
     * Returns the hash code value for this {@link ContextMap}.
     *
     * @return the hash code value for this {@link ContextMap}, taking into account the hash codes of each key-value
     * mappings this {@link ContextMap} contains.
     * @see #equals(Object)
     */
    @Override
    int hashCode();
}
