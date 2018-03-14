/**
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.context;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * The key-value map stored in the {@link AsyncContext}.
 */
public interface AsyncContextMap {
    /**
     * A key identifies a specific object in a {@link AsyncContextMap}.
     * @param <T> The type of value associated with a {@link AsyncContextMap.Key}.
     */
    final class Key<T> {
        private final String stringRepresentation;

        private Key() {
            this.stringRepresentation = super.toString();
        }

        private Key(String stringRepresentation) {
            // Append the hashCode so it is clear that two instances created with the same stringRepresentation value are different.
            this.stringRepresentation = requireNonNull(stringRepresentation) + '-' + hashCode();
        }

        /**
         * Create a new {@link Key} which has a {@link String} used only in the {@link #toString()} method for debugging visibility.
         * <p>
         * Comparison between {@link Key} objects should be assumed to be on an instance basis.
         * In general {@code newKeyWithDebugToString(str) != newKeyWithDebugToString(str)}.
         * @param toString The value to use in {@link #toString()}. This <strong>WILL NOT</strong> be used in comparisons between {@link Key} objects.
         * @param <T> The value type associated with the {@link Key}.
         * @return a new {@link Key} which has a {@link String} used only in the {@link #toString()} method for debugging visibility.
         */
        public static <T> Key<T> newKeyWithDebugToString(String toString) {
            return new Key<>(toString);
        }

        /**
         * Create a new instance.
         * <p>
         * Comparison between {@link Key} objects should be assumed to be on an instance basis.
         * In general {@code newKey() != newKey()}.
         * @param <T> The value type associated with the {@link Key}.
         * @return a new instance.
         */
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
     * @return the value associated with {@code key}, or {@code null} if no value is associated.
     */
    @Nullable
    <T> T get(AsyncContextMap.Key<T> key);

    /**
     * Determine if this context contains a key/value entry corresponding to {@code key}.
     *
     * @param key the key to lookup.
     * @return {@code true} if this context contains a key/value entry corresponding to {@code key}.
     * {@code false} otherwise.
     */
    boolean contains(AsyncContextMap.Key<?> key);

    /**
     * Determine if there are no key/value pairs in this {@link AsyncContextMap}.
     *
     * @return {@code true} if there are no key/value pairs in this {@link AsyncContextMap}.
     */
    boolean isEmpty();

    /**
     * Return a new context with {@code key=value} added.
     *
     * @param key   the key used to index {@code value}. Cannot be {@code null}.
     * @param value the value to put.
     * @param <T>   The type of object associated with {@code key}.
     * @return A {@link AsyncContextMap} object which contains the new key/value entry.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     * @throws NullPointerException          if {@code key} is {@code null}, or {@code value} is {@code null} and the implementation
     *                                       doesn't support {@code null} values.
     * @throws UnsupportedOperationException if this method is not supported.
     */
    <T> AsyncContextMap put(AsyncContextMap.Key<T> key, @Nullable T value);

    /**
     * Return a new context with key/value pairs from another context added.
     * <p>Multiple iterations over {@code context} may be done, and so this object must be unmodified until this method completes.
     *
     * @param context All of the key/value pairs from {@code context} will be insert into this {@link AsyncContextMap}.
     * @return A {@link AsyncContextMap} object which contains all the key/value pairs in {@code context}.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     * @throws ConcurrentModificationException Done on a best effort basis if {@code context} is detected to be modified while
     *                                         attempting to put all entries.
     */
    AsyncContextMap putAll(AsyncContextMap context);

    /**
     * Return a new context with key/value pairs added.
     * <p>Multiple iterations over {@code entries} may be done, and so the object must be unmodified until this method completes.
     *
     * @param map The entries to insert into this {@link AsyncContextMap}.
     * @return A {@link AsyncContextMap} object which contains all the key/value pairs in {@code entries}.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     * @throws ConcurrentModificationException Done on a best effort basis if {@code entries} is detected to be modified while
     *                                         attempting to put all entries.
     */
    AsyncContextMap putAll(Map<AsyncContextMap.Key<?>, Object> map);

    /**
     * Return a new context with a key removed.
     *
     * @param key The key which identifies the key/value to remove.
     * @return this If this object did not contain an entry corresponding to {@code key}.
     * Otherwise a {@link AsyncContextMap} object which does not contain an entry corresponding to {@code key}.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     */
    AsyncContextMap remove(AsyncContextMap.Key<?> key);

    /**
     * Return a new context with key/value pairs from another context removed.
     * <p>The {@code context} object must be unmodified until this method completes.
     *
     * @param context All of the keys from {@code context} will be removed from this {@link AsyncContextMap}.
     * @return A {@link AsyncContextMap} object which contains all the key/value pairs in {@code entries}.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     * @throws ConcurrentModificationException Done on a best effort basis if {@code entries} is detected to be modified while
     *                                         attempting to remove all entries.
     */
    AsyncContextMap removeAll(AsyncContextMap context);

    /**
     * Return a new context with key/value pairs removed.
     * <p>The {@code entries} object must be unmodified until this method completes.
     *
     * @param entries The entries to remove from this {@link AsyncContextMap}.
     * @return A {@link AsyncContextMap} object which contains all the key/value pairs in {@code entries}.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     * @throws ConcurrentModificationException Done on a best effort basis if {@code entries} is detected to be modified while
     *                                         attempting to remove all entries.
     */
    AsyncContextMap removeAll(Iterable<AsyncContextMap.Key<?>> entries);

    /**
     * Return an empty context.
     *
     * @return A {@link AsyncContextMap} with no key/value entries.
     * If the {@link AsyncContextMap} implementation is immutable this may be a new object.
     */
    AsyncContextMap clear();

    /**
     * Iterate over the key/value pairs contained in this request context.
     *
     * @param consumer Each key/value pair will be passed as arguments to this {@link BiFunction}.
     *                 Returns {@code true} if the consumer wants to keep iterating or {@code false} to stop iteration at the current key/value pair.
     * @return {@code null} if {@code consumer} iterated through all key/value pairs or the {@link AsyncContextMap.Key} at which the iteration stopped.
     */
    @Nullable
    AsyncContextMap.Key<?> forEach(BiFunction<AsyncContextMap.Key<?>, Object, Boolean> consumer);
}
