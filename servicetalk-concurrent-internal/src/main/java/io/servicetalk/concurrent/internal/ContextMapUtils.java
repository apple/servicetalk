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
package io.servicetalk.concurrent.internal;

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMap.Key;

import javax.annotation.Nullable;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Objects.requireNonNull;

/**
 * Shared utilities for {@link ContextMap}.
 */
public final class ContextMapUtils {
    private ContextMapUtils() {
        // no instances
    }

    /**
     * {@link Object#toString()} implementation for {@link ContextMap}.
     *
     * @param map {@link ContextMap} to convert
     * @return {@link String} representation of the context map
     */
    public static String toString(final ContextMap map) {
        final String simpleName = map.getClass().getSimpleName();
        final int size = map.size();
        if (size == 0) {
            return simpleName + '@' + toHexString(identityHashCode(map)) + ":{}";
        }
        // 12 is 1 characters for '@' + 8 hash code integer in hex form + 1 character for ':' + 2 characters for "{}".
        // Assume size of 90 for each key/value pair: 42 overhead characters for formatting + 16 characters for key
        // name + 16 characters for key type + 16 characters for value.
        StringBuilder sb = new StringBuilder(simpleName.length() + 12 + size * 90);
        sb.append(simpleName)
                .append('@')
                // There are many copies of these maps around, the content maybe equal but a differentiating factor is
                // the object reference. this may help folks understand why state is not visible across AsyncContext
                // boundaries.
                .append(toHexString(identityHashCode(map)))
                .append(":{");

        map.forEach((key, value) -> {
            sb.append(key).append('=').append(value == map ? "(this Map)" : value).append(',').append(' ');
            return true;
        });
        sb.setLength(sb.length() - 2);
        return sb.append('}').toString();
    }

    /**
     * {@link java.util.Objects#equals(Object, Object)} alternative for {@link ContextMap}.
     *
     * @param first the first {@link ContextMap}
     * @param second the second {@link ContextMap} to compare equality with the first one
     * @return {@code true} if both {@link ContextMap}(s) are equal (contains the same elements), {@code false}
     * otherwise.
     */
    public static boolean equals(final ContextMap first, final ContextMap second) {
        if (first.size() != second.size()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        final Key<?> stopped = first.forEach((key, value) -> second.contains((Key<? super Object>) key, value));
        return stopped == null;
    }

    /**
     * Make sure that the {@code value} type matches with the {@link Key#type()}.
     *
     * @param key the {@link Key} to verify
     * @param value the value to verify
     * @throws NullPointerException if {@code key == null}
     * @throws IllegalArgumentException if type of the {@code value} does not match with {@link Key#type()}
     */
    public static void ensureType(final Key<?> key, @Nullable final Object value) {
        requireNonNull(key);
        if (value != null && !key.type().isInstance(value)) {
            throw new IllegalArgumentException("Type of the value " + value + '(' + value.getClass() + ')' +
                    " does mot match with " + key);
        }
    }
}
