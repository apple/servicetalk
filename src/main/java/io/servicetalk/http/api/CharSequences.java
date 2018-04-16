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

import javax.annotation.Nullable;

import static io.servicetalk.http.api.AsciiString.cached;

/**
 * Provides factory methods for creating {@link CharSequence} implementations.
 */
public final class CharSequences {
    private CharSequences() {
        // No instances
    }

    /**
     * Create a new {@link CharSequence} from the specified {@code input}, supporting only 8-bit ASCII characters, and
     * with a case-insensitive {@code hashCode}.
     * <p>
     * Supporting only 8-bit ASCII and providing a case-insensitive {@code hashCode} allows for optimizations when the
     * {@link CharSequence} returned is used as {@link HttpHeaders} names or values.
     *
     * @param input a string containing only 8-bit ASCII characters.
     * @return a {@link CharSequence}
     */
    public static CharSequence newAsciiString(final String input) {
        return cached(input);
    }

    /**
     * Perform a case-insensitive comparison of two {@link CharSequence}s.
     * <p>
     * NOTE: This only supports 8-bit ASCII.
     *
     * @param a first {@link CharSequence} to compare.
     * @param b second {@link CharSequence} to compare.
     * @return {@code true} if both {@link CharSequence}'s are equals when ignoring the case.
     */
    public static boolean contentEqualsIgnoreCase(@Nullable final CharSequence a, @Nullable final CharSequence b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a == b) {
            return true;
        }

        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); ++i) {
            if (!AsciiString.equalsIgnoreCase(a.charAt(i), b.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
