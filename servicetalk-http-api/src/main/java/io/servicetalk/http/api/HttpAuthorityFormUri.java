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

import java.nio.charset.Charset;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.UriComponentType.HOST_NON_IP;
import static io.servicetalk.http.api.UriUtils.decodeComponent;
import static io.servicetalk.http.api.UriUtils.encodeComponent;
import static io.servicetalk.http.api.UriUtils.parsePort;

/**
 * <a href="https://tools.ietf.org/html/rfc7230#section-5.3.3">authority-form</a> URI.
 */
final class HttpAuthorityFormUri implements Uri {
    private final String uri;
    private final String host;
    private final int port;

    HttpAuthorityFormUri(final String uri) {
        int i = 0;
        int begin = 0;
        String parsedHost = null;
        int parsedPort = -1;
        byte parsingIPv6 = 0; // 0 = not parsed, 1 = parsing, 2 = already parsed
        boolean foundColonForPort = false;
        while (i < uri.length()) {
            final char c = uri.charAt(i);
            if (c == '[') {
                if (parsingIPv6 != 0 || parsedHost != null) {
                    throw new IllegalArgumentException("unexpected [");
                }
                parsingIPv6 = 1;
                begin = i++; // post increment, preserve the '[' for original uri for pathEndIndex.
            } else if (c == ']') {
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
            } else if (c == ':') {
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
            } else if (c == '@' || c == '?' || c == '#' || c == '/') {
                throw new IllegalArgumentException("authority-form URI doesn't allow userinfo, path, query, fragment");
            } else {
                ++i;
            }
        }

        if (parsedHost == null) {
            if (parsingIPv6 == 1) {
                throw new IllegalArgumentException("missing closing ] for IP-literal");
            }
            parsedHost = uri;
        } else if (foundColonForPort) {
            parsedPort = parsePort(uri, begin, uri.length());
        } else if (parsedHost.length() != uri.length()) {
            throw new IllegalArgumentException("authority-form URI only supports the host component");
        }

        host = parsedHost;
        port = parsedPort;
        this.uri = uri;
    }

    @Override
    public String uri() {
        return uri;
    }

    @Nullable
    @Override
    public String scheme() {
        return null;
    }

    @Override
    public String authority() {
        StringBuilder sb = new StringBuilder(host.length() + 6); // 6 max port chars + `:`
        sb.append(host);
        if (port >= 0) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    @Nullable
    @Override
    public String userInfo() {
        return null;
    }

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
        return "";
    }

    @Override
    public String path(final Charset charset) {
        return "";
    }

    @Nullable
    @Override
    public String query() {
        return null;
    }

    @Nullable
    @Override
    public String query(final Charset charset) {
        return null;
    }

    @Nullable
    @Override
    public String fragment() {
        return null;
    }

    static String encode(String requestTarget, Charset charset) {
        HttpAuthorityFormUri uri = new HttpAuthorityFormUri(requestTarget);
        StringBuilder sb = new StringBuilder(uri.uri.length() + 16);
        if (!uri.host.isEmpty()) {
            sb.append(uri.host.charAt(0) != '[' ?
                    encodeComponent(HOST_NON_IP, uri.host, charset, true) : uri.host);
        }
        if (uri.port >= 0) {
            sb.append(':').append(uri.port);
        }
        return sb.toString();
    }

    static String decode(String requestTarget, Charset charset) {
        HttpAuthorityFormUri uri = new HttpAuthorityFormUri(requestTarget);
        StringBuilder sb = new StringBuilder(uri.uri.length());
        if (!uri.host.isEmpty()) {
            sb.append(uri.host.charAt(0) != '[' ? decodeComponent(uri.host, charset) : uri.host);
        }
        if (uri.port >= 0) {
            sb.append(':').append(uri.port);
        }
        return sb.toString();
    }
}
