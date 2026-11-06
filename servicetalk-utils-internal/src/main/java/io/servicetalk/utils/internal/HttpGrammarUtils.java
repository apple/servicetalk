/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.utils.internal;

/**
 * Utilities for validating octets against HTTP ABNF grammar productions.
 */
public final class HttpGrammarUtils {

    private static final byte HTAB = 9;
    private static final byte DEL = 127;

    private HttpGrammarUtils() {
        // No instances.
    }

    /**
     * Checks if a byte is a valid <a href="https://tools.ietf.org/html/rfc7230#section-3.2">field-value</a> octet,
     * i.e. one of {@code HTAB / SP / VCHAR / obs-text}. The
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1.2">reason-phrase</a> grammar permits exactly this
     * same set, so this predicate is the single source of truth for both.
     *
     * @param value the octet to check.
     * @return {@code true} if {@code value} is allowed in a field-value or reason-phrase.
     */
    public static boolean isFieldValueByte(final byte value) {
        // field-value   = *( field-content / obs-fold )
        // field-content = field-vchar [ 1*( SP / HTAB ) field-vchar ]
        // field-vchar   = VCHAR / obs-text
        //
        // Note: we do not support obs-fold.
        // Illegal octets are control chars (0x00-0x1F) except HTAB (0x09), and DEL (0x7F):
        return ((value & 0xE0) != 0 || value == HTAB) && value != DEL;
    }
}
