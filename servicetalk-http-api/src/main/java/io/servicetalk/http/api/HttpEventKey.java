/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
            newKey("max-concurrency");

    private final String stringRepresentation;

    private HttpEventKey(String stringRepresentation) {
        this.stringRepresentation = requireNonNull(stringRepresentation);
    }

    private HttpEventKey() {
        this.stringRepresentation = super.toString();
    }

    /**
     * Creates a new {@link HttpEventKey} with the specific {@code name}.
     *
     * @param name of the option. This is only used for debugging purpose and not for key equality.
     * @param <T> Type of the value of the option.
     * @return A new {@link HttpEventKey}.
     */
    public static <T> HttpEventKey<T> newKey(String name) {
        return new HttpEventKey<>(name);
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }
}
