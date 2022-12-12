/*
 * Copyright © 2019, 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConsumableEvent;

import static java.lang.Integer.toHexString;
import static java.util.Objects.requireNonNull;

/**
 * A key which identifies a configuration setting for a connection. Setting values may change over time.
 * <p>
 * Comparison between {@link HttpEventKey} objects should be assumed to be on an instance basis.
 * In general, {@code newKey(strA) != newKey(strA)}.
 *
 * @param <T> Type of the value of this setting.
 */
@SuppressWarnings("unused")
public final class HttpEventKey<T> {
    /**
     * Option to define max concurrent requests allowed on a connection.
     */
    public static final HttpEventKey<ConsumableEvent<Integer>> MAX_CONCURRENCY =
            newKey("max-concurrency", generify(ConsumableEvent.class));

    private final String name;
    private final Class<T> type;

    private HttpEventKey(final String name, final Class<T> type) {
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
     * Creates a new {@link HttpEventKey} with the specified name and type.
     *
     * @param name The name of the key. This <strong>WILL NOT</strong> be used in comparisons between
     * {@link HttpEventKey} objects.
     * @param type The type of the key. This <strong>WILL NOT</strong> be used in comparisons between
     * {@link HttpEventKey} objects.
     * @param <T> The value type associated with the {@link HttpEventKey}.
     * @return A new {@link HttpEventKey} which uses a passed name only in the {@link #toString()} method for
     * debugging visibility.
     */
    public static <T> HttpEventKey<T> newKey(String name, final Class<T> type) {
        return new HttpEventKey<>(name, type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{name='" + name + '\'' +
                ", type=" + type.getSimpleName() +
                "}@" + toHexString(hashCode());
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> generify(Class<?> clazz) {
        return (Class<T>) clazz;
    }
}
