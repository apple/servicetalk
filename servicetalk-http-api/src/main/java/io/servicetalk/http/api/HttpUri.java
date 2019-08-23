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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.StringUtil.decodeHexByte;
import static java.nio.charset.CodingErrorAction.REPLACE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Accepts a <a href="https://tools.ietf.org/html/rfc7230#section-2.7">HTTP URI</a> and breaks down the components.
 * <p>
 * In some scenarios, HTTP URI parsing using {@link java.net.URI} has been in the hot-path. For HTTP, not all of the
 * complexity of {@link java.net.URI} is needed. Additionally, deriving "the correct" hostname for HTTP can be easy to
 * get wrong. {@link HttpUri} attempts to address these issues.
 */
final class HttpUri {
    private static final String SPACE = " ";

    static final String HTTP_SCHEME = "http";
    static final String HTTPS_SCHEME = "https";
    static final int HTTP_DEFAULT_PORT = 80;
    static final int HTTPS_DEFAULT_PORT = 443;

    private final String uri;
    @Nullable
    private final String hostHeader;
    private final String relativeReference;
    private final boolean ssl;
    @Nullable
    private final String scheme;
    @Nullable
    private String userInfo;
    @Nullable
    private final String host;
    private final int port;
    private final boolean explicitPort;
    @Nullable
    private String rawPath;
    @Nullable
    private String path;
    @Nullable
    private String rawQuery;

    /**
     * See <a href="https://tools.ietf.org/html/rfc3986#section-3">URI Syntax</a>.
     * <pre>
     * foo://example.com:8042/over/there?name=ferret#nose
     * \_/   \______________/\_________/ \_________/ \__/
     * |           |            |            |        |
     * scheme     authority       path        query   fragment
     *                       \__________________________/
     *                                |
     *                                  relativeReference
     * </pre>
     *
     * @param uri The URI from a HTTP request line.
     */
    HttpUri(final String uri) throws IllegalArgumentException {
        this(uri, () -> null);
    }

    /**
     * See <a href="https://tools.ietf.org/html/rfc3986#section-3">URI Syntax</a>.
     * <pre>
     * foo://example.com:8042/over/there?name=ferret#nose
     * \_/   \______________/\_________/ \_________/ \__/
     * |           |            |            |        |
     * scheme     authority       path        query   fragment
     *                       \__________________________/
     *                                |
     *                                  relativeReference
     * </pre>
     *
     * @param uri The URI from a HTTP request line.
     * @param defaultHostHeader Will be called if the host header couldn't be parsed and the host:port will be used.
     */
    HttpUri(final String uri,
            final Supplier<String> defaultHostHeader) throws IllegalArgumentException {

        int begin = 0;
        int lastColon = -1;
        int ipliteral = -1;
        @Nullable
        String parsedHost = null;
        @Nullable
        String parsedHostHeader = null;
        int parsedPort = -1;
        // -1 = undefined, 0 = http, 1 = https
        int parsedScheme = -1;
        int relativeReferenceStart = -1;
        boolean authorityFound = false;

        int i = 0;

        while (i < uri.length()) {
            final char c = uri.charAt(i);
            if (c == '/') {
                if (i - 1 > 0 && i + 1 < uri.length() && uri.charAt(i - 1) == ':' && uri.charAt(i + 1) == '/') {
                    if (parsedScheme != -1) {
                        throw new IllegalArgumentException("duplicate scheme");
                    }
                    if (i == 5 && uri.regionMatches(true, 0, HTTP_SCHEME, 0, 4)) {
                        parsedScheme = 0;
                    } else if (i == 6 && uri.regionMatches(true, 0, HTTPS_SCHEME, 0, 5)) {
                        parsedScheme = 1;
                    } else {
                        throw new IllegalArgumentException("unsupported scheme");
                    }
                    begin = i += 2;
                    lastColon = -1;
                } else if (begin != 0) {
                    relativeReferenceStart = i;
                    break;
                } else if (uri.length() > 1 && uri.charAt(0) == '/' && uri.charAt(1) == '/') {
                    begin = 2;
                    i = 3;
                } else {
                    break;
                }
            } else if (c == '?' || c == '#') {
                relativeReferenceStart = begin == 0 ? 0 : i;
                break;
            } else if (c == '@') {
                if (begin == 0 || parsedScheme < 0 && uri.charAt(begin - 1) == '/') {
                    invalidAuthority();
                }
                userInfo = uri.substring(begin, i);
                begin = i += 1;
                lastColon = -1;
            } else if (c == '[') {
                if (ipliteral != -1) {
                    throw new IllegalArgumentException("unexpected [");
                }
                ipliteral = 0;
                ++i;
            } else if (c == ']') {
                if (ipliteral != 0) {
                    throw new IllegalArgumentException("unexpected ]");
                }
                ipliteral = i;
                ++i;
            } else if (c == ':') {
                lastColon = i;
                ++i;
            } else {
                ++i;
            }
        }

        if (lastColon > ipliteral) {
            parsedPort = parsePort(uri, lastColon + 1, i);
            parsedHost = uri.substring(begin, lastColon);
            parsedHostHeader = uri.substring(begin, i);
            authorityFound = true;
        } else if (begin != i &&
                ((begin > 1 && uri.charAt(begin - 1) == '/' && uri.charAt(begin - 2) == '/') ||
                        (begin > 0 && uri.charAt(begin - 1) == '@'))) {
            if (i > uri.length() || parsedScheme < 0 && uri.charAt(begin) == '@' && uri.charAt(begin - 1) == '/') {
                invalidAuthority();
            }
            parsedHost = uri.substring(begin, i);
            parsedHostHeader = parsedHost;
            authorityFound = true;
        } else {
            if (relativeReferenceStart < 0) {
                relativeReferenceStart = 0;
            }
            parsedHostHeader = defaultHostHeader.get();
            if (parsedHostHeader != null) {
                final int x = parsedHostHeader.lastIndexOf(':');
                if (x > 0) {
                    final int y = parsedHostHeader.lastIndexOf(':', x - 1);
                    if (y >= 0) {
                        // IPv6 address is present in the header
                        // https://tools.ietf.org/html/rfc3986#section-3.2.2
                        // A host identified by an Internet Protocol literal address, version 6
                        // [RFC3513] or later, is distinguished by enclosing the IP literal
                        // within square brackets ("[" and "]").  This is the only place where
                        // square bracket characters are allowed in the URI syntax.
                        final int cb;
                        if (parsedHostHeader.charAt(0) != '[' || (cb = parsedHostHeader.lastIndexOf(']')) < 0) {
                            throw new IllegalArgumentException("IPv6 address should be in square brackets");
                        }
                        if (cb < x) {
                            parsedHost = parsedHostHeader.substring(0, x);
                            parsedPort = parsePort(parsedHostHeader, x + 1, parsedHostHeader.length());
                        } else if (cb != parsedHostHeader.length() - 1) {
                            throw new IllegalArgumentException(
                                    "']' should be at the end of IPv6 address or before port number");
                        } else {
                            parsedHost = parsedHostHeader;
                        }
                    } else {
                        // IPv4 or literal host with port number
                        parsedHost = parsedHostHeader.substring(0, x);
                        parsedPort = parsePort(parsedHostHeader, x + 1, parsedHostHeader.length());
                    }
                } else if (x < 0) {
                    parsedHost = parsedHostHeader;
                } else {
                    throw new IllegalArgumentException("Illegal position of colon in the host header");
                }
            }
        }

        if (relativeReferenceStart == 0 || (begin == 0 && i == uri.length())) {
            if (authorityFound || "*".equals(uri)) {
                relativeReference = "";
            } else {
                verifyFirstPathSegment(uri, 0);
                relativeReference = uri;
            }
        } else if (relativeReferenceStart > 0) {
            verifyFirstPathSegment(uri, relativeReferenceStart);
            relativeReference = uri.substring(relativeReferenceStart);
        } else {
            relativeReference = "";
        }
        scheme = parsedScheme == 0 ? HTTP_SCHEME : parsedScheme == 1 ? HTTPS_SCHEME : null;
        host = parsedHost;
        hostHeader = parsedHostHeader;
        ssl = parsedScheme == 1;
        port = parsedPort > 0 ? parsedPort : (ssl ? HTTPS_DEFAULT_PORT : HTTP_DEFAULT_PORT);
        explicitPort = parsedPort > 0;
        this.uri = uri;
    }

    String uri() {
        return uri;
    }

    @Nullable
    String scheme() {
        return scheme;
    }

    @Nullable
    String userInfo() {
        return userInfo;
    }

    @Nullable
    String host() {
        return host;
    }

    int port() {
        return port;
    }

    int explicitPort() {
        return explicitPort ? port : -1;
    }

    String rawPath() {
        if (rawPath != null) {
            return rawPath;
        }

        if (!relativeReference.isEmpty()) {
            final int queryStart = relativeReference.indexOf('?');
            if (queryStart >= 0) {
                rawPath = relativeReference.substring(0, queryStart);
                return rawPath;
            }

            final int fragmentStart = relativeReference.indexOf('#');
            if (fragmentStart >= 0) {
                rawPath = relativeReference.substring(0, fragmentStart);
                return rawPath;
            }
        }

        rawPath = relativeReference;
        return rawPath;
    }

    String path() {
        if (path == null) {
            final String raw = rawPath();
            path = decodeComponent(raw, 0, raw.length(), true, UTF_8);
        }
        return path;
    }

    String rawQuery() {
        if (rawQuery != null) {
            return rawQuery;
        }

        final int rawPathLength = rawPath().length();
        if (rawPathLength == relativeReference.length()) {
            rawQuery = "";
            return rawQuery;
        }

        final int fragmentStart = relativeReference.indexOf('#', rawPathLength);
        rawQuery = fragmentStart == rawPathLength ? "" :
                fragmentStart < 0 ? relativeReference.substring(rawPathLength + 1) :
                        relativeReference.substring(rawPathLength + 1, fragmentStart);
        return rawQuery;
    }

    String relativeReference() {
        return relativeReference;
    }

    @Nullable
    String hostHeader() {
        return hostHeader;
    }

    boolean isSsl() {
        return ssl;
    }

    boolean hostAndPortEqual(final HttpUri rhs) {
        return port == rhs.port && (host == rhs.host || (host != null && host.equals(rhs.host)));
    }

    static String buildRequestTarget(final String scheme, @Nullable final String host, final int port,
                                     @Nullable final String path, @Nullable final String query,
                                     @Nullable final String relativeReference) {
        if (relativeReference == null) {
            assert path != null;
            assert query != null;
        }
        final int approximateLength = (host == null ? 0 : scheme.length() + 3 + host.length() + (port >= 0 ? 0 : 4))
                + (relativeReference != null ? relativeReference.length() :
                path.length() + 1 + query.length());

        final StringBuilder uri = new StringBuilder(approximateLength);
        if (host != null) {
            uri.append(scheme).append("://").append(host);
            if (port >= 0) {
                uri.append(':').append(port);
            }
        }
        if (relativeReference != null) {
            uri.append(relativeReference);
        } else {
            uri.append(path);
            if (!query.isEmpty()) {
                uri.append('?').append(query);
            }
        }
        return uri.toString();
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof HttpUri && hostAndPortEqual((HttpUri) o);
    }

    @Override
    public int hashCode() {
        return 31 * (31 + port + Objects.hashCode(host));
    }

    @Override
    public String toString() {
        return uri;
    }

    static String decodeComponent(final String s, final int from, final int toExcluded, final boolean isPath,
                                  final Charset charset) {
        final int len = toExcluded - from;
        if (len <= 0) {
            return "";
        }
        int firstEscaped = -1;
        for (int i = from; i < toExcluded; i++) {
            final char c = s.charAt(i);
            if (c == '%' || c == '+' && !isPath) {
                firstEscaped = i;
                break;
            }
        }
        if (firstEscaped == -1) {
            return s.substring(from, toExcluded);
        }

        final CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(REPLACE).onUnmappableCharacter(REPLACE);

        // Each encoded byte takes 3 characters (e.g. "%20")
        final int decodedCapacity = (toExcluded - firstEscaped) / 3;
        final ByteBuffer byteBuf = ByteBuffer.allocate(decodedCapacity);
        final CharBuffer charBuf = CharBuffer.allocate(decodedCapacity);

        final StringBuilder strBuf = new StringBuilder(len);
        strBuf.append(s, from, firstEscaped);

        for (int i = firstEscaped; i < toExcluded; i++) {
            final char c = s.charAt(i);
            if (c != '%') {
                strBuf.append(c != '+' || isPath ? c : SPACE);
                continue;
            }

            byteBuf.clear();
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + s);
                }
                byteBuf.put(decodeHexByte(s, i + 1));
                i += 3;
            } while (i < toExcluded && s.charAt(i) == '%');
            i--;

            byteBuf.flip();
            charBuf.clear();
            CoderResult result = decoder.reset().decode(byteBuf, charBuf, true);
            try {
                if (!result.isUnderflow()) {
                    result.throwException();
                }
                result = decoder.flush(charBuf);
                if (!result.isUnderflow()) {
                    result.throwException();
                }
            } catch (final CharacterCodingException ex) {
                throw new IllegalArgumentException(ex);
            }
            strBuf.append(charBuf.flip());
        }
        return strBuf.toString();
    }

    private static int parsePort(final String uri, final int begin, final int end) {
        final int len = end - begin;
        if (len == 4) {
            return (1000 * toDecimal(uri.charAt(begin))) +
                    (100 * toDecimal(uri.charAt(begin + 1))) +
                    (10 * toDecimal(uri.charAt(begin + 2))) +
                    toDecimal(uri.charAt(begin + 3));
        } else if (len == 3) {
            return (100 * toDecimal(uri.charAt(begin))) +
                    (10 * toDecimal(uri.charAt(begin + 1))) +
                    toDecimal(uri.charAt(begin + 2));
        } else if (len == 2) {
            return (10 * toDecimal(uri.charAt(begin))) +
                    toDecimal(uri.charAt(begin + 1));
        } else if (len == 5) {
            final int port = (10000 * toDecimal(uri.charAt(begin))) +
                    (1000 * toDecimal(uri.charAt(begin + 1))) +
                    (100 * toDecimal(uri.charAt(begin + 2))) +
                    (10 * toDecimal(uri.charAt(begin + 3))) +
                    toDecimal(uri.charAt(begin + 4));
            if (port > 65535) {
                throw new IllegalArgumentException("port out of bounds");
            }
            return port;
        } else if (len == 1) {
            return toDecimal(uri.charAt(begin));
        } else {
            throw new IllegalArgumentException("invalid port");
        }
    }

    private static int toDecimal(final char c) {
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("invalid port");
        }
        return c - '0';
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc3986#section-3.3">Path</a>
     * <pre>the first path segment cannot contain a colon (":") character</pre>
     *
     * @param uri The original uri.
     * @param begin the start of the path.
     */
    private static void verifyFirstPathSegment(final String uri, final int begin) {
        for (int i = begin + 1; i < uri.length(); ++i) {
            final char c = uri.charAt(i);
            if (c == ':') {
                throw new IllegalArgumentException("invalid first path segment: " + uri);
            } else if (c == '/' || c == '?' || c == '#') {
                return;
            }
        }
    }

    private static void invalidAuthority() {
        // https://tools.ietf.org/html/rfc3986#section-3.2.2
        // If the URI scheme defines a default for host, then that default
        // applies when the host subcomponent is undefined or when the
        // registered name is empty (zero length). For example, the "file" URI
        // scheme is defined so that no authority, an empty host, and
        // "localhost" all mean the end-user's machine, whereas the "http"
        // scheme considers a missing authority or empty host invalid.
        throw new IllegalArgumentException("authority component must have username and host specified for HTTP");
    }
}
