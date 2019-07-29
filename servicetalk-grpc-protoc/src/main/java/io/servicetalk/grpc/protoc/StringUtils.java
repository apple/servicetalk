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
package io.servicetalk.grpc.protoc;

import javax.annotation.Nullable;

import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;

final class StringUtils {
    private StringUtils() {
        // no instances
    }

    /**
     * Sanitize a string to conform to java identifier standards.
     *
     * @param v The un-sanitized String.
     * @param firstToLower if {@code true} the first character (if ASCII) will be forced to lower case.
     * otherwise the first character (if ASCII) will be forced to upper case.
     * @return The sanitized String.
     */
    static String sanitizeIdentifier(final String v, final boolean firstToLower) {
        if (isNullOrEmpty(v)) {
            throw new IllegalArgumentException("java identifier must have length >= 1");
        }
        final StringBuilder sb = new StringBuilder(v.length());
        sb.append(firstToLower ? toLowerCase(v.charAt(0)) : toUpperCase(v.charAt(0)));
        boolean afterUnderscore = false;
        for (int i = 1; i < v.length(); ++i) {
            final char c = v.charAt(i);
            if (c == '_') {
                afterUnderscore = true;
            } else {
                sb.append(afterUnderscore ? toUpperCase(c) : c);
                afterUnderscore = false;
            }
        }
        return sb.toString();
    }

    static boolean isNotNullNorEmpty(@Nullable final String v) {
        return v != null && !v.isEmpty();
    }

    static boolean isNullOrEmpty(@Nullable final String v) {
        return v == null || v.isEmpty();
    }
}
