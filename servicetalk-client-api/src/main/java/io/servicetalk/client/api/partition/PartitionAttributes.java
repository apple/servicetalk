/*
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
package io.servicetalk.client.api.partition;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

import static java.lang.Integer.compare;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

/**
 * Provide a way to describe a partition using a collection of of attributes. Typically only a single type of any
 * particular {@link Key} exists in each {@link PartitionAttributes}. For example:
 * <pre>
 * { [Key(shard) = "shard X"], [Key(data center) = "data center X"], [Key(is main) = "false/true"] }
 * </pre>
 * This construct allows for the attributes to partially specify a partition and preform "wild card" type matching.
 */
public interface PartitionAttributes {
    /**
     * A key identifies a specific object in a {@link PartitionAttributes}.
     * @param <T> The type of value associated with a {@link PartitionAttributes.Key}.
     */
    final class Key<T> implements Comparable<Key> {
        private static final AtomicInteger nextIdCounter = new AtomicInteger();
        private final int id;
        private final String toString;

        private Key() {
            id = nextIdCounter.getAndIncrement();
            toString = valueOf(id);
        }

        private Key(String toString) {
            id = nextIdCounter.getAndIncrement();
            // Append the id so it is clear that two instances created with the same toString value are different.
            this.toString = requireNonNull(toString) + '-' + id;
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
         */
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
         */
        public static <T> Key<T> newKey() {
            return new Key<>();
        }

        @Override
        public String toString() {
            return toString;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Key) && id == ((Key) o).id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public int compareTo(Key o) {
            return compare(id, o.id);
        }
    }

    /**
     * Get the value associated with {@code key}.
     * @param key The key to lookup.
     * @param <T> The expected value type associated with {@code key}.
     * @return the value associated with {@code key}, or {@code null}.
     */
    @Nullable
    <T> T get(Key<T> key);

    /**
     * Determine if there is a value associated with {@code key}.
     * @param key The key to check if there is any associated value for.
     * @param <T> The expected value type associated with {@code key}.
     * @return {@code true} if there is a value associated with {@code key}.
     */
    default <T> boolean contains(Key<T> key) {
        return get(key) != null;
    }

    /**
     * Iterate over the key/value pairs in this collection.
     * @param action Invoked for each key/value pair in this collection.
     */
    void forEach(BiConsumer<Key, Object> action);

    /**
     * Determine how many key/value pairs are contained in this collection.
     * @return the number of key/value pairs are contained in this collection.
     */
    int size();

    /**
     * Determine if there are no key/value pairs in this collection.
     * @return {@code true} if there are no key/value pairs in this collection.
     */
    boolean isEmpty();
}
