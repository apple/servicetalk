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
package io.servicetalk.http.utils;

import io.servicetalk.transport.api.HostAndPort;

import javax.annotation.Nullable;

/**
 * A <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-4.1">URI-reference</a> without its fragment,
 * resolved per <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-5">RFC 3986, section 5</a>.
 * <p>
 * Components are split without validation; whatever consumes the result is expected to validate it.
 */
final class UriReference {
    @Nullable
    final String scheme;
    @Nullable
    final String authority;
    final String path;
    @Nullable
    final String query;

    UriReference(@Nullable final String scheme, @Nullable final String authority, final String path,
                 @Nullable final String query) {
        this.scheme = scheme;
        this.authority = authority;
        this.path = path;
        this.query = query;
    }

    // Splits as https://datatracker.ietf.org/doc/html/rfc3986#appendix-B does.
    static UriReference parse(final String uriReference) {
        final int length = uriReference.length();
        int i = 0;
        while (i < length && "/?#:".indexOf(uriReference.charAt(i)) < 0) {
            ++i;
        }
        String scheme = null;
        int start = 0;
        if (i < length && uriReference.charAt(i) == ':') {
            if (i == 0) {
                // https://datatracker.ietf.org/doc/html/rfc3986#section-4.2: the first segment of a relative-path
                // reference cannot contain a colon, otherwise it would be mistaken for a scheme name.
                throw new IllegalArgumentException("Invalid URI-reference: " + uriReference);
            }
            scheme = uriReference.substring(0, i);
            start = i + 1;
        }
        String authority = null;
        if (uriReference.startsWith("//", start)) {
            final int authorityEnd = indexOfAny(uriReference, "/?#", start + 2);
            authority = uriReference.substring(start + 2, authorityEnd);
            start = authorityEnd;
        }
        final int pathEnd = indexOfAny(uriReference, "?#", start);
        String query = null;
        if (pathEnd < length && uriReference.charAt(pathEnd) == '?') {
            final int queryEnd = uriReference.indexOf('#', pathEnd);
            query = uriReference.substring(pathEnd + 1, queryEnd < 0 ? length : queryEnd);
        }
        return new UriReference(scheme, authority, uriReference.substring(start, pathEnd), query);
    }

    private static int indexOfAny(final String s, final String chars, final int fromIndex) {
        for (int i = fromIndex; i < s.length(); ++i) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return s.length();
    }

    /**
     * Parses {@code reference} and {@linkplain #resolve(UriReference) resolves} it against this base URI.
     *
     * @param reference the reference to resolve.
     * @return the target URI.
     */
    UriReference resolve(final String reference) {
        return resolve(parse(reference));
    }

    /**
     * Resolves a reference against this base URI.
     *
     * @param r the reference to resolve.
     * @return the target URI.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.2">RFC 3986, section 5.2.2</a>
     */
    UriReference resolve(final UriReference r) {
        if (r.scheme != null) {
            return new UriReference(r.scheme, r.authority, removeDotSegments(r.path), r.query);
        }
        if (r.authority != null) {
            return new UriReference(scheme, r.authority, removeDotSegments(r.path), r.query);
        }
        if (r.path.isEmpty()) {
            return new UriReference(scheme, authority, path, r.query != null ? r.query : query);
        }
        return new UriReference(scheme, authority,
                removeDotSegments(r.path.charAt(0) == '/' ? r.path : merge(r.path)), r.query);
    }

    /**
     * Returns the host and port of the {@link #authority}, or {@code null} if it is undefined.
     *
     * @return the host and port of the {@link #authority}, or {@code null} if it is undefined.
     * @throws IllegalArgumentException if the host or port is malformed.
     */
    @Nullable
    HostAndPort hostAndPort() {
        if (authority == null) {
            return null;
        }
        final int hostStart = authority.lastIndexOf('@') + 1;
        final int ipLiteralEnd = authority.lastIndexOf(']');
        final int portDelimiter = authority.lastIndexOf(':');
        if (portDelimiter < hostStart || portDelimiter < ipLiteralEnd) {
            return HostAndPort.of(host(authority, hostStart, authority.length()), -1);
        }
        return HostAndPort.of(host(authority, hostStart, portDelimiter), port(authority, portDelimiter + 1));
    }

    /**
     * Returns the path and query as an origin-form request-target.
     *
     * @return the path and query as an origin-form request-target.
     */
    String originForm() {
        return (path.isEmpty() ? "/" : path) + (query == null ? "" : '?' + query);
    }

    // Only an IP literal may contain ':', and it must be enclosed in brackets.
    private static String host(final String authority, final int begin, final int end) {
        final String host = authority.substring(begin, end);
        if (host.startsWith("[") ? !host.endsWith("]") : host.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Invalid host in authority: " + authority);
        }
        return host;
    }

    // https://datatracker.ietf.org/doc/html/rfc3986#section-3.2.3: port = *DIGIT, limited to the TCP port range.
    private static int port(final String authority, final int begin) {
        if (begin == authority.length()) {
            return -1;
        }
        int port = 0;
        for (int i = begin; i < authority.length(); ++i) {
            final char c = authority.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Invalid port in authority: " + authority);
            }
            port = port * 10 + (c - '0');
            if (port > 65_535) {
                throw new IllegalArgumentException("Invalid port in authority: " + authority);
            }
        }
        return port;
    }

    // https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.3
    private String merge(final String referencePath) {
        if (authority != null && path.isEmpty()) {
            return '/' + referencePath;
        }
        return path.substring(0, path.lastIndexOf('/') + 1) + referencePath;
    }

    // https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.4, with an index into the input buffer instead of
    // removing its prefix: a substring per segment would make this quadratic in the length of the path.
    private static String removeDotSegments(final String path) {
        if (path.indexOf('.') < 0) {
            return path;
        }
        final int length = path.length();
        final StringBuilder output = new StringBuilder(length);
        int i = 0;
        while (i < length) {
            if (path.startsWith("../", i)) {
                i += 3;
            } else if (path.startsWith("./", i) || path.startsWith("/./", i)) {
                i += 2;
            } else if (path.startsWith("/.", i) && i + 2 == length) {
                output.append('/');
                i = length;
            } else if (path.startsWith("/../", i)) {
                i += 3;
                removeLastSegment(output);
            } else if (path.startsWith("/..", i) && i + 3 == length) {
                removeLastSegment(output);
                output.append('/');
                i = length;
            } else if ((path.startsWith(".", i) && i + 1 == length) || (path.startsWith("..", i) && i + 2 == length)) {
                i = length;
            } else {
                final int nextSlash = path.indexOf('/', i + 1);
                final int segmentEnd = nextSlash < 0 ? length : nextSlash;
                output.append(path, i, segmentEnd);
                i = segmentEnd;
            }
        }
        return output.toString();
    }

    private static void removeLastSegment(final StringBuilder output) {
        output.setLength(Math.max(output.lastIndexOf("/"), 0));
    }

    // https://datatracker.ietf.org/doc/html/rfc3986#section-5.3
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (scheme != null) {
            sb.append(scheme).append(':');
        }
        if (authority != null) {
            sb.append("//").append(authority);
        }
        sb.append(path);
        if (query != null) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }
}
