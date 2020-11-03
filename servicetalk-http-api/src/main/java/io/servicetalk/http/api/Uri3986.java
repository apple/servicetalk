/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import java.nio.charset.Charset;
import java.util.Objects;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.UriComponentType.FRAGMENT;
import static io.servicetalk.http.api.UriComponentType.HOST_NON_IP;
import static io.servicetalk.http.api.UriComponentType.PATH;
import static io.servicetalk.http.api.UriComponentType.QUERY;
import static io.servicetalk.http.api.UriComponentType.USER_INFO;
import static io.servicetalk.http.api.UriUtils.decodeComponent;
import static io.servicetalk.http.api.UriUtils.encodeComponent;
import static io.servicetalk.http.api.UriUtils.parsePort;

/**
 * Represents the components of a <a href="https://tools.ietf.org/html/rfc3986">URI</a>.
 * <p>
 * {@link java.net.URI} targets the obsolete <a href="https://tools.ietf.org/html/rfc2396">rfc2732</a>. This class
 * also lazy parses some components which may not be as commonly used (e.g. query, fragment).
 */
final class Uri3986 implements Uri {
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String NULL_COMPONENT = new String(""); // instance equality required!
    private final String uri;
    @Nullable
    private final String scheme;
    @Nullable
    private final String userInfo;
    @Nullable
    private final String host;
    private final int port;
    private final String path;
    @Nullable
    private String query;
    @Nullable
    private String fragment;

    /**
     * Create a new instance give a {@link String} following
     * <a href="https://tools.ietf.org/html/rfc3986#section-3">URI Syntax</a>.
     * <pre>
     * foo://example.com:8042/over/there?name=ferret#nose
     * \_/   \______________/\_________/ \_________/ \__/
     * |           |            |            |        |
     * scheme     authority       path        query   fragment
     * </pre>
     * @param uri A URI string.
     */
    Uri3986(final String uri) {
        int i = 0;
        int begin = 0;
        String parsedScheme = null;
        String parsedUserInfo = null;
        String parsedHost = null;
        int parsedPort = -1;
        String parsedPath = null;
        boolean eligibleToParseScheme = true;

        outerloop:
        while (i < uri.length()) {
            final char c = uri.charAt(i);
            if (c == '/') {
                if (begin == i && parsedHost == null && uri.length() - 1 > i && uri.charAt(i + 1) == '/') {
                    // authority   = [ userinfo "@" ] host [ ":" port ]
                    i += 2;
                    begin = i;
                    final int authorityBegin = begin;
                    byte parsingIPv6 = 0; // 0 = not parsed, 1 = parsing, 2 = already parsed
                    boolean foundColonForPort = false;
                    while (i < uri.length()) {
                        final char c2 = uri.charAt(i);
                        if (c2 == '@') {
                            if (parsedUserInfo != null) {
                                throw new IllegalArgumentException("duplicate userinfo");
                            }
                            // Userinfo has `:` as valid. If we previously parsed the host throw it away.
                            parsedUserInfo = uri.substring(authorityBegin, i);
                            parsedHost = null;
                            begin = ++i;
                        } else if (c2 == '[') {
                            if (parsingIPv6 != 0 || parsedHost != null) {
                                throw new IllegalArgumentException("unexpected [");
                            }
                            parsingIPv6 = 1;
                            begin = i++; // post increment, preserve the '[' for original uri for pathEndIndex.
                        } else if (c2 == ']') {
                            if (parsingIPv6 == 0) {
                                throw new IllegalArgumentException("unexpected ]");
                            } else if (i - 1 <= begin) {
                                throw new IllegalArgumentException("empty ip literal");
                            }
                            // Copy the '[' and ']' characters. pathEndIndex depends upon retaining the uri contents.
                            parsedHost = uri.substring(begin, i + 1);
                            foundColonForPort = false;
                            parsingIPv6 = 2;
                            begin = ++i;
                        } else if (c2 == ':') {
                            if (parsingIPv6 == 0) {
                                if (parsedHost != null) {
                                    throw new IllegalArgumentException("duplicate/invalid host");
                                }
                                parsedHost = uri.substring(begin, i);
                            } else if (parsingIPv6 == 2 && begin != i) {
                                throw new IllegalArgumentException("Port must be immediately after IPv6address");
                            }
                            ++i;
                            if (parsingIPv6 != 1) {
                                begin = i;
                                foundColonForPort = true;
                            }
                        } else if (c2 == '?' || c2 == '#' || c2 == '/') {
                            if (parsedHost == null) {
                                if (parsingIPv6 == 1) {
                                    throw new IllegalArgumentException("missing closing ] for IP-literal");
                                }
                                parsedHost = uri.substring(begin, i);
                            } else if (foundColonForPort) {
                                parsedPort = parsePort(uri, begin, i);
                            }
                            if (c2 == '/') {
                                begin = i++; // post increment, preserve the '/' for original uri for pathEndIndex.
                                continue outerloop;
                            } else {
                                parsedPath = "";
                                begin = ++i;
                                break outerloop;
                            }
                        } else {
                            ++i;
                        }
                    }
                    if (i == uri.length()) {
                        if (parsedHost == null) {
                            if (parsingIPv6 == 1) {
                                throw new IllegalArgumentException("missing closing ] for IP-literal");
                            }
                            parsedHost = uri.substring(begin);
                        } else if (foundColonForPort) {
                            parsedPort = parsePort(uri, begin, i);
                        }
                        begin = i;
                    }
                } else {
                    eligibleToParseScheme = false;
                    ++i;
                }
            } else if (c == ':' && begin == 0 && parsedScheme == null && eligibleToParseScheme) {
                if (i == 0) {
                    throw new IllegalArgumentException("empty scheme");
                }
                parsedScheme = uri.substring(0, i);
                begin = ++i;
                // We don't enforce the following, browsers still generate these types of requests.
                // https://tools.ietf.org/html/rfc3986#section-3.3
                // > In addition, a URI reference (Section 4.1) may be a relative-path reference,
                //   in which case the first path segment cannot contain a colon (":") character.
            } else if (c == '?' || c == '#') {
                parsedPath = uri.substring(begin, i);
                break;
            } else {
                ++i;
            }
        }

        if (i == uri.length()) {
            parsedPath = uri.substring(begin);
        }
        assert parsedPath != null;

        scheme = parsedScheme;
        userInfo = parsedUserInfo;
        host = parsedHost;
        port = parsedPort;
        path = parsedPath;
        this.uri = uri;
    }

    @Override
    public String uri() {
        return uri;
    }

    @Nullable
    @Override
    public String scheme() {
        return scheme;
    }

    @Nullable
    @Override
    public String authority() {
        if (host == null) {
            return null;
        }
        final StringBuilder sb;
        if (userInfo == null) {
            sb = new StringBuilder(host.length() + 6); // 6 max port chars + `:`
        } else {
            sb = new StringBuilder(host.length() + userInfo.length() + 7); // '@' + 6 max port chars + `:`
            sb.append(userInfo).append('@');
        }

        sb.append(host);
        if (port >= 0) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    @Nullable
    @Override
    public String userInfo() {
        return userInfo;
    }

    @Nullable
    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String path(final Charset charset) {
        return decodeComponent(path, charset);
    }

    @Override
    public String query() {
        if (query != null) {
            return unwrapNullableComponent(query);
        }

        final int pathEndIndex = pathEndIndex();
        if (pathEndIndex >= uri.length() || uri.charAt(pathEndIndex) != '?') {
            query = NULL_COMPONENT;
            return null;
        }
        final int fragmentStart = uri.indexOf('#', pathEndIndex + 1);
        query = fragmentStart < 0 ? uri.substring(pathEndIndex + 1) :
                        uri.substring(pathEndIndex + 1, fragmentStart);
        return query;
    }

    @Nullable
    @Override
    public String query(final Charset charset) {
        final String query = query();
        return query == null ? null : decodeComponent(query, charset);
    }

    @Override
    public String fragment() {
        if (fragment != null) {
            return unwrapNullableComponent(fragment);
        }

        final int pathEndIndex = pathEndIndex();
        if (pathEndIndex >= uri.length()) {
            fragment = NULL_COMPONENT;
            return null;
        }

        final int fragmentStart = uri.indexOf('#', pathEndIndex);
        if (fragmentStart < 0) {
            fragment = NULL_COMPONENT;
            return null;
        }
        fragment = uri.substring(fragmentStart + 1);
        return fragment;
    }

    @SuppressWarnings("StringEquality")
    @Nullable
    private static String unwrapNullableComponent(String component) {
        return component == NULL_COMPONENT ? null : component;
    }

    private int pathEndIndex() {
        int i = 0;
        if (scheme != null) {
            i = scheme.length() + 1; // ':'
        }
        if (host != null) {
            // The authority component is preceded by a double slash ("//")
            // authority   = [ userinfo "@" ] host [ ":" port ]
            i += host.length() + 2; // '/' '/'
            if (userInfo != null) {
                i += userInfo.length() + 1; // '@'
            }
            if (port >= 0) {
                i += numberOfDigits(port) + 1; // ':'
            }
        }
        i += path.length();
        return i;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Uri3986)) {
            return false;
        }
        final Uri3986 rhs = (Uri3986) o;
        return port == rhs.port && Objects.equals(host, rhs.host);
    }

    @Override
    public int hashCode() {
        return 31 * (31 + port + Objects.hashCode(host));
    }

    @Override
    public String toString() {
        return uri;
    }

    static String encode(String requestTarget, Charset charset, boolean preservePctEncoding) {
        Uri3986 uri = new Uri3986(requestTarget);
        StringBuilder sb = new StringBuilder(uri.uri.length() + 16);
        if (uri.scheme != null) {
            sb.append(uri.scheme).append(':');
        }
        if (uri.host != null) {
            // The authority component is preceded by a double slash ("//")
            // authority   = [ userinfo "@" ] host [ ":" port ]
            sb.append("//");
            if (uri.userInfo != null) {
                sb.append(encodeComponent(USER_INFO, uri.userInfo, charset, preservePctEncoding)).append('@');
            }

            if (!uri.host.isEmpty()) {
                sb.append(uri.host.charAt(0) != '[' ?
                        encodeComponent(HOST_NON_IP, uri.host, charset, preservePctEncoding) : uri.host);
            }
            if (uri.port >= 0) {
                sb.append(':').append(uri.port);
            }
        }

        String path = uri.path();
        if (!path.isEmpty() && path.charAt(0) != '/') {
            sb.append('/');
        }
        sb.append(encodeComponent(PATH, path, charset, preservePctEncoding));

        String query = uri.query();
        if (query != null) {
            sb.append('?').append(encodeComponent(QUERY, query, charset, preservePctEncoding));
        }

        String fragment = uri.fragment();
        if (fragment != null) {
            sb.append('#').append(encodeComponent(FRAGMENT, fragment, charset, preservePctEncoding));
        }
        return sb.toString();
    }

    static String decode(String requestTarget, Charset charset) {
        Uri3986 uri = new Uri3986(requestTarget);
        StringBuilder sb = new StringBuilder(uri.uri.length());
        if (uri.scheme != null) {
            sb.append(uri.scheme).append(':');
        }
        if (uri.host != null) {
            // The authority component is preceded by a double slash ("//")
            // authority   = [ userinfo "@" ] host [ ":" port ]
            sb.append("//");
            if (uri.userInfo != null) {
                sb.append(decodeComponent(uri.userInfo, charset)).append('@');
            }
            if (!uri.host.isEmpty()) {
                sb.append(uri.host.charAt(0) != '[' ? decodeComponent(uri.host, charset) : uri.host);
            }
            if (uri.port >= 0) {
                sb.append(':').append(uri.port);
            }
        }

        String path = uri.path();
        if (!path.isEmpty() && path.charAt(0) != '/') {
            sb.append('/');
        }
        sb.append(decodeComponent(path, charset));

        String query = uri.query();
        if (query != null) {
            sb.append('?').append(decodeComponent(query, charset));
        }

        String fragment = uri.fragment();
        if (fragment != null) {
            sb.append('#').append(decodeComponent(fragment, charset));
        }
        return sb.toString();
    }

    private static int numberOfDigits(int port) {
        if (port < 10000) {
            if (port < 1000) {
                if (port < 100) {
                    return port < 10 ? 1 : 2;
                }
                return 3;
            }
            return 4;
        } else if (port <= 65535) {
            return 5;
        }
        throw new IllegalArgumentException("port out of bounds: " + port);
    }
}
