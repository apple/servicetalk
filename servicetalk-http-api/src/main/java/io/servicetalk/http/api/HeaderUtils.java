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

import io.servicetalk.buffer.api.ByteProcessor;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.CharSequences.regionMatches;
import static io.servicetalk.http.api.NetUtil.isValidIpV4Address;
import static io.servicetalk.http.api.NetUtil.isValidIpV6Address;
import static java.lang.Math.min;
import static java.lang.System.lineSeparator;

final class HeaderUtils {
    /**
     * Constant used to seed the hash code generation. Could be anything but this was borrowed from murmur3.
     */
    static final int HASH_CODE_SEED = 0xc2b2ae35;
    static final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> DEFAULT_HEADER_FILTER = (k, v) -> "<filtered>";
    private static final ByteProcessor HEADER_NAME_VALIDATOR = value -> {
        validateHeaderNameToken(value);
        return true;
    };

    private HeaderUtils() {
        // no instances
    }

    static String toString(final HttpHeaders headers,
                           final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        final String simpleName = headers.getClass().getSimpleName();
        final int size = headers.size();
        if (size == 0) {
            return simpleName + "[]";
        } else {
            // original capacity assumes 20 chars per headers
            final StringBuilder sb = new StringBuilder(simpleName.length() + 2 + size * 20)
                    .append(simpleName)
                    .append('[');
            final Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
            if (itr.hasNext()) {
                for (;;) {
                    final Map.Entry<CharSequence, CharSequence> e = itr.next();
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

    static boolean equals(final HttpHeaders lhs, final HttpHeaders rhs) {
        if (lhs.size() != rhs.size()) {
            return false;
        }

        if (lhs == rhs) {
            return true;
        }

        // The regular iterator is not suitable for equality comparisons because the overall ordering is not
        // in any specific order relative to the content of this MultiMap.
        for (final CharSequence name : lhs.names()) {
            final Iterator<? extends CharSequence> valueItr = lhs.values(name);
            final Iterator<? extends CharSequence> h2ValueItr = rhs.values(name);
            while (valueItr.hasNext() && h2ValueItr.hasNext()) {
                if (!contentEquals(valueItr.next(), h2ValueItr.next())) {
                    return false;
                }
            }
            if (valueItr.hasNext() != h2ValueItr.hasNext()) {
                return false;
            }
        }
        return true;
    }

    static int hashCode(final HttpHeaders headers) {
        if (headers.empty()) {
            return 0;
        }
        int result = HASH_CODE_SEED;
        for (final CharSequence key : headers.names()) {
            result = 31 * result + caseInsensitiveHashCode(key);
            final Iterator<? extends CharSequence> valueItr = headers.values(key);
            while (valueItr.hasNext()) {
                result = 31 * result + caseInsensitiveHashCode(valueItr.next());
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
     *
     * @param key the cookie name or header name to validate.
     */
    static void validateCookieTokenAndHeaderName(final CharSequence key) {
        if (key.getClass() == AsciiBuffer.class) {
            ((AsciiBuffer) key).forEachByte(HEADER_NAME_VALIDATOR);
        } else {
            validateCookieTokenAndHeaderName0(key);
        }
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a>.
     *
     * @param requestDomain The domain from the request.
     * @param cookieDomain The domain from the cookie.
     * @return {@code true} if there is a match.
     */
    static boolean domainMatches(final CharSequence requestDomain, @Nullable final CharSequence cookieDomain) {
        if (cookieDomain == null || requestDomain.length() == 0) {
            return false;
        }
        final int startIndex = cookieDomain.length() - requestDomain.length();
        if (startIndex == 0) {
            // The RFC has an ambiguous statement [1] related to case sensitivity here but since domain names are
            // generally compared in a case insensitive fashion we do the same here.
            // [1] https://tools.ietf.org/html/rfc6265#section-5.1.3
            // the domain string and the string will have been canonicalized to lower case at this point
            return contentEqualsIgnoreCase(cookieDomain, requestDomain);
        }
        final boolean queryEndsInDot = requestDomain.charAt(requestDomain.length() - 1) == '.';
        return ((queryEndsInDot && startIndex >= -1 &&
                regionMatches(cookieDomain, true, startIndex + 1, requestDomain, 0, requestDomain.length() - 1)) ||
                (!queryEndsInDot && startIndex > 0 &&
                        regionMatches(cookieDomain, true, startIndex, requestDomain, 0, requestDomain.length()))) &&
                !isValidIpV4Address(cookieDomain) && !isValidIpV6Address(cookieDomain);
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a>.
     *
     * @param requestPath The path from the request.
     * @param cookiePath The path from the cookie.
     * @return {@code true} if there is a match.
     */
    static boolean pathMatches(final CharSequence requestPath, @Nullable final CharSequence cookiePath) {
        // cookiePath cannot be empty, but we check for 0 length to protect against IIOBE below.
        if (cookiePath == null || cookiePath.length() == 0 || requestPath.length() == 0) {
            return false;
        }

        if (requestPath.length() == cookiePath.length()) {
            return requestPath.equals(cookiePath);
        }
        final boolean actualStartsWithSlash = cookiePath.charAt(0) == '/';
        final int length = min(actualStartsWithSlash ? cookiePath.length() - 1 :
                cookiePath.length(), requestPath.length());
        return regionMatches(requestPath, false, requestPath.charAt(0) == '/' &&
                !actualStartsWithSlash ? 1 : 0, cookiePath, 0, length) &&
                (requestPath.length() > cookiePath.length() || cookiePath.charAt(length) == '/');
    }

    private static void validateCookieTokenAndHeaderName0(final CharSequence key) {
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
            final char value = key.charAt(i);
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
     *
     * @param value the character to validate.
     */
    private static void validateHeaderNameToken(final byte value) {
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
