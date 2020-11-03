/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.http.api.UriUtils.FRAGMENT_HMASK;
import static io.servicetalk.http.api.UriUtils.FRAGMENT_LMASK;
import static io.servicetalk.http.api.UriUtils.HOST_NON_IP_HMASK;
import static io.servicetalk.http.api.UriUtils.HOST_NON_IP_LMASK;
import static io.servicetalk.http.api.UriUtils.PATH_HMASK;
import static io.servicetalk.http.api.UriUtils.PATH_LMASK;
import static io.servicetalk.http.api.UriUtils.PATH_SEGMENT_HMASK;
import static io.servicetalk.http.api.UriUtils.PATH_SEGMENT_LMASK;
import static io.servicetalk.http.api.UriUtils.QUERY_HMASK;
import static io.servicetalk.http.api.UriUtils.QUERY_LMASK;
import static io.servicetalk.http.api.UriUtils.QUERY_VALUE_HMASK;
import static io.servicetalk.http.api.UriUtils.QUERY_VALUE_LMASK;
import static io.servicetalk.http.api.UriUtils.USERINFO_HMASK;
import static io.servicetalk.http.api.UriUtils.USERINFO_LMASK;
import static io.servicetalk.http.api.UriUtils.isBitSet;

enum UriComponentType {
    USER_INFO {
        /**
         * Determines if a byte in the user info component needs to be escaped according to
         * <a href="https://tools.ietf.org/html/rfc3986#section-3.2.1">rfc3986</a>.
         * <pre>
         *     userinfo    = *( unreserved / pct-encoded / sub-delims / ":" )
         * </pre>
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, USERINFO_LMASK, USERINFO_HMASK);
        }
    },
    PATH {
        /**
         * Determines if a byte in a path needs to be escaped according to
         * <a href="https://tools.ietf.org/html/rfc3986#section-3.3">rfc3986</a>.
         * <pre>
         *     path          = path-abempty    ; begins with "/" or is empty
         *                     / path-absolute   ; begins with "/" but not "//"
         *                     / path-noscheme   ; begins with a non-colon segment
         *                     / path-rootless   ; begins with a segment
         *                     / path-empty      ; zero characters
         *
         *       path-abempty  = *( "/" segment )
         *       path-absolute = "/" [ segment-nz *( "/" segment ) ]
         *       path-noscheme = segment-nz-nc *( "/" segment )
         *       path-rootless = segment-nz *( "/" segment )
         *       path-empty    = 0&lt;pchar&gt;
         *       segment       = *pchar
         *       segment-nz    = 1*pchar
         *       segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" )
         *                       ; non-zero-length segment without any colon ":"
         * </pre>
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, PATH_LMASK, PATH_HMASK);
        }
    },
    PATH_SEGMENT {
        /**
         * An individual <a href="https://tools.ietf.org/html/rfc3986#section-3.3">path segment</a>.
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, PATH_SEGMENT_LMASK, PATH_SEGMENT_HMASK);
        }
    },
    QUERY {
        /**
         * Determines if a byte in a query string needs to be escaped according to
         * <a href="https://tools.ietf.org/html/rfc3986#section-3.4">rfc3986</a>.
         * <pre>
         *     query       = *( pchar / "/" / "?" )
         * </pre>
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, QUERY_LMASK, QUERY_HMASK);
        }
    },
    QUERY_VALUE {
        /**
         * Similar to {@link #QUERY} except targeted at encoding query values so
         * <a href="https://tools.ietf.org/html/rfc3986#section-2.2">sub-delims</a> are encoded (e.g.
         * {@code &}, {@code =}, etc.).
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, QUERY_VALUE_LMASK, QUERY_VALUE_HMASK);
        }
    },
    FRAGMENT {
        /**
         * Determines if a byte in a fragment string needs to be escaped according to
         * <a href="https://tools.ietf.org/html/rfc3986#section-3.5">rfc3986</a>.
         * <pre>
         *     fragment       = *( pchar / "/" / "?" )
         * </pre>
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, FRAGMENT_LMASK, FRAGMENT_HMASK);
        }
    },
    HOST_NON_IP {
        /**
         * Determines if a byte in a host string (excluding {@code IP-literal} and {@code IPv4address}) needs to be
         * escaped according to <a href="https://tools.ietf.org/html/rfc3986#section-3.2.2">rfc3986</a>.
         * <pre>
         *     host        = IP-literal / IPv4address / reg-name
         *     reg-name    = *( unreserved / pct-encoded / sub-delims )
         * </pre>
         * @param b the byte to validate.
         * @return {@code true} if the byte needs to be escaped.
         */
        @Override
        boolean isValid(final byte b) {
            return isBitSet(b, HOST_NON_IP_LMASK, HOST_NON_IP_HMASK);
        }
    };

    abstract boolean isValid(byte b);
}
