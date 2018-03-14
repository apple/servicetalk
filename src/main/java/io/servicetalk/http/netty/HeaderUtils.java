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
package io.servicetalk.http.netty;

import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.servicetalk.http.api.HttpHeaders;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

import static java.lang.System.lineSeparator;

final class HeaderUtils {
    /**
     * Constant used to seed the hash code generation. Could be anything but this was borrowed from murmur3.
     */
    static final int HASH_CODE_SEED = 0xc2b2ae35;
    static final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> DEFAULT_HEADER_FILTER = (k, v) -> "<filtered>";
    static final ByteProcessor HEADER_NAME_VALIDATOR = value -> {
        validateHeaderNameToken(value);
        return true;
    };

    private HeaderUtils() {
        // no instances
    }

    static String toString(HttpHeaders headers,
                           BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        String simpleName = headers.getClass().getSimpleName();
        int size = headers.size();
        if (size == 0) {
            return simpleName + "[]";
        } else {
            // original capacity assumes 20 chars per headers
            StringBuilder sb = new StringBuilder(simpleName.length() + 2 + size * 20)
                    .append(simpleName)
                    .append('[');
            Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
            if (itr.hasNext()) {
                for (;;) {
                    Map.Entry<CharSequence, CharSequence> e = itr.next();
                    sb.append(e.getKey()).append(": ").append(filter.apply(e.getKey(), e.getValue()));
                    if (itr.hasNext()) {
                        sb.append(lineSeparator());
                    } else {
                        break;
                    }
                }
            }
            return sb.append(']').toString();
        }
    }

    static boolean equals(HttpHeaders lhs, HttpHeaders rhs) {
        if (lhs.size() != rhs.size()) {
            return false;
        }

        if (lhs == rhs) {
            return true;
        }

        // The regular iterator is not suitable for equality comparisons because the overall ordering is not
        // in any specific order relative to the content of this MultiMap.
        for (CharSequence name : lhs.getNames()) {
            Iterator<? extends CharSequence> valueItr = lhs.getAll(name);
            Iterator<? extends CharSequence> h2ValueItr = rhs.getAll(name);
            while (valueItr.hasNext() && h2ValueItr.hasNext()) {
                if (!AsciiString.contentEquals(valueItr.next(), h2ValueItr.next())) {
                    return false;
                }
            }
            if (valueItr.hasNext() != h2ValueItr.hasNext()) {
                return false;
            }
        }
        return true;
    }

    static int hashCode(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return 0;
        }
        int result = HASH_CODE_SEED;
        for (CharSequence key : headers.getNames()) {
            result = 31 * result + AsciiString.hashCode(key);
            Iterator<? extends CharSequence> valueItr = headers.getAll(key);
            while (valueItr.hasNext()) {
                result = 31 * result + AsciiString.hashCode(valueItr.next());
            }
        }
        return result;
    }

    /**
     * Validate {@code key} is valid <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>
     * (aka <a href="https://tools.ietf.org/html/rfc2616#section-2.2">token</a>) and a
     * valid <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">field-name</a> of a
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header-field</a>. Both of these
     * formats have the same restrictions.
     * @param key the cookie name or header name to validate.
     */
    static void validateCookieTokenAndHeaderName(CharSequence key) {
        // HEADER
        // header-field   = field-name ":" OWS field-value OWS
        //
        // field-name     = token
        // token          = 1*tchar
        //
        // tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
        //                    / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
        //                    / DIGIT / ALPHA
        //                    ; any VCHAR, except delimiters
        //  Delimiters are chosen
        //   from the set of US-ASCII visual characters not allowed in a token
        //   (DQUOTE and "(),/:;<=>?@[\]{}")
        //
        // COOKIE
        // cookie-pair       = cookie-name "=" cookie-value
        // cookie-name       = token
        // token          = 1*<any CHAR except CTLs or separators>
        // CTL = <any US-ASCII control character
        //       (octets 0 - 31) and DEL (127)>
        // separators     = "(" | ")" | "<" | ">" | "@"
        //                      | "," | ";" | ":" | "\" | <">
        //                      | "/" | "[" | "]" | "?" | "="
        //                      | "{" | "}" | SP | HT
        for (int i = 0; i < key.length(); ++i) {
            char value = key.charAt(i);
            // CTL = <any US-ASCII control character
            //       (octets 0 - 31) and DEL (127)>
            // separators     = "(" | ")" | "<" | ">" | "@"
            //                      | "," | ";" | ":" | "\" | <">
            //                      | "/" | "[" | "]" | "?" | "="
            //                      | "{" | "}" | SP | HT
            if (value <= 32 || value >= 127) {
                throw new IllegalArgumentException("invalid token detected at index: " + i);
            }
            switch (value) {
                case '(':
                case ')':
                case '<':
                case '>':
                case '@':
                case ',':
                case ';':
                case ':':
                case '\\':
                case '"':
                case '/':
                case '[':
                case ']':
                case '?':
                case '=':
                case '{':
                case '}':
                    throw new IllegalArgumentException("invalid token detected at index: " + i);
                default:
                    break;
            }
        }
    }

    /**
     * Validate char is valid <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">token</a> character.
     * @param value the character to validate.
     */
    private static void validateHeaderNameToken(byte value) {
        if (value >= 0 && value <= 32 || value < 0) {
            throw new IllegalArgumentException("invalid token detected: " + value);
        }
        switch (value) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '@':
            case ',':
            case ';':
            case ':':
            case '\\':
            case '"':
            case '/':
            case '[':
            case ']':
            case '?':
            case '=':
            case '{':
            case '}':
                throw new IllegalArgumentException("invalid token detected: " + value);
            default:
                break;
        }
    }
}
