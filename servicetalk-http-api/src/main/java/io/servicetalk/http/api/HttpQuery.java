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
package io.servicetalk.http.api;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import javax.annotation.Nullable;

/**
 * A structured representation of the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> of a
 * request. May be used for reading and manipulating the query component. Modifications will only be reflected in the
 * request after {@link #encodeToRequestTarget()} is called.
 */
public interface HttpQuery extends Iterable<Map.Entry<String, String>> {

    /**
     * Returns the value of a query parameter with the specified key. If there is more than one value for the specified
     * key, the first value in insertion order is returned.

     * @param key the key of the query parameter to retrieve.
     * @return the first query parameter value if the key is found. {@code null} if there's no such key.
     */
    @Nullable
    String get(String key);

    /**
     * Returns all values for the query parameter with the specified key.
     *
     * @param key the key of the query parameter to retrieve.
     * @return a {@link Iterator} of query parameter values or an empty {@link Iterator} if no values are found.
     */
    Iterator<String> all(String key);

    /**
     * Returns a {@link Set} of all query parameter keys in this object. The returned {@link Set} cannot be modified.
     * @return a {@link Set} of all query parameter keys in this object. The returned {@link Set} cannot be modified.
     */
    Set<String> keys();

    /**
     * Adds a new query parameter with the specified {@code key} and {@code value}.
     *
     * @param key the query parameter key.
     * @param value the query parameter value.
     * @return {@code this}.
     */
    HttpQuery add(String key, String value);

    /**
     * Adds new query parameters with the specified {@code key} and {@code values}. This method is semantically
     * equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     query.add(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpQuery add(String key, Iterable<String> values);

    /**
     * Adds new query parameters with the specified {@code key} and {@code values}. This method is semantically
     * equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     query.add(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpQuery add(String key, String... values);

    /**
     * Sets a query parameter with the specified {@code key} and {@code value}. Any existing query parameters with the same key are
     * overwritten.
     *
     * @param key the query parameter key.
     * @param value the query parameter value.
     * @return {@code this}.
     */
    HttpQuery set(String key, String value);

    /**
     * Sets a new query parameter with the specified {@code key} and {@code values}. This method is equivalent to:
     *
     * <pre>
     * query.remove(key);
     * for (T value : values) {
     *     query.add(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter values.
     * @return {@code this}.
     */
    HttpQuery set(String key, Iterable<String> values);

    /**
     * Sets a new query parameter with the specified {@code key} and {@code values}. This method is equivalent to:
     *
     * <pre>
     * query.remove(key);
     * for (T value : values) {
     *     query.add(key, value);
     * }
     * </pre>
     *
     * @param key the query parameter key.
     * @param values the query parameter value.
     * @return {@code this}.
     */
    HttpQuery set(String key, String... values);

    /**
     * Returns {@code true} if a query parameter with the {@code key} exists, {@code false} otherwise.
     *
     * @param key the query parameter name.
     * @return {@code true} if {@code key} exists.
     */
    default boolean contains(final String key) {
        return get(key) != null;
    }

    /**
     * Returns {@code true} if a query parameter with the {@code key} and {@code value} exists, {@code false} otherwise.
     *
     * @param key the query parameter key.
     * @param value the query parameter value of the query parameter to find.
     * @return {@code true} if a {@code key}, {@code value} pair exists.
     */
    boolean contains(String key, String value);

    /**
     * Removes all query parameters with the specified {@code key}.
     *
     * @param key the query parameter key.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean remove(String key);

    /**
     * Removes all query parameters with the specified {@code key} and {@code value}.
     *
     * @param key the query parameter key.
     * @param value the query parameter value.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean remove(String key, String value);

    /**
     * Returns the number of query parameters in this object.
     * @return the number of query parameters in this object.
     */
    int size();

    /**
     * Returns {@code true} if {@link #size()} equals {@code 0}.
     * @return {@code true} if {@link #size()} equals {@code 0}.
     */
    boolean empty();

    /**
     * Sets the <a href="https://tools.ietf.org/html/rfc3986#section-3.4">query component</a> on the request that this
     * {@link HttpQuery} originated from. This will overwrite any prior changes to the query component of that request.
     */
    void encodeToRequestTarget();

    @Override
    Iterator<Map.Entry<String, String>> iterator();

    @Override
    default Spliterator<Map.Entry<String, String>> spliterator() {
        return Spliterators.spliterator(iterator(), size(), Spliterator.SIZED);
    }
}
