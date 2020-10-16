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
package io.servicetalk.http.utils;

import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;

/**
 * Helper methods for computing effective request URIs according
 * to <a href="https://tools.ietf.org/html/rfc7230#section-5.5">RFC 7230, section 5.5</a>
 * and base URIs (which are effective request URIs with {@code /} as path, and no query nor fragment.
 */
public final class HttpRequestUriUtils {
    private HttpRequestUriUtils() {
        // no instances
    }

    /**
     * Get the effective request URI for the provided {@link ConnectionContext} and {@link HttpRequestMetaData}.
     * <p>
     * For example, for this <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> request:
     * <pre>
     * GET /pub/WWW/TheProject.html HTTP/1.1
     * Host: www.example.org:8080
     * </pre>
     * the effective request URI will be:
     * <pre>
     * http://www.example.org:8080/pub/WWW/TheProject.html
     * </pre>
     *
     * @param ctx the {@link ConnectionContext} to use.
     * @param metaData the {@link HttpRequestMetaData} to use.
     * @param includeUserInfo {@code true} if the deprecated user info sub-component must be included
     * (see <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">RFC 7230, section 2.7.1</a>).
     * @return an effective request URI as {@link String}.
     */
    public static String getEffectiveRequestUri(final ConnectionContext ctx, final HttpRequestMetaData metaData,
                                                final boolean includeUserInfo) {
        return getEffectiveRequestUri(ctx, metaData, null, null, includeUserInfo);
    }

    /**
     * Get the effective request URI for the provided {@link ConnectionContext} and {@link HttpRequestMetaData}.
     * <p>
     * For example, for this <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> request:
     * <pre>
     * GET /pub/WWW/TheProject.html HTTP/1.1
     * Host: www.example.org:8080
     * </pre>
     * and a {@code fixedAuthority} of {@code "example.com"} the effective request URI will be:
     * <pre>
     * http://example.com/pub/WWW/TheProject.html
     * </pre>
     *
     * @param ctx the {@link ConnectionContext} to use.
     * @param metaData the {@link HttpRequestMetaData} to use.
     * @param fixedScheme the configured fixed scheme as {@link String}, or {@code null} if none is set.
     * @param fixedAuthority the configured fixed authority as {@link String}, or {@code null} if none is set.
     * @param includeUserInfo {@code true} if the deprecated user info sub-component must be included
     * (see <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">RFC 7230, section 2.7.1</a>).
     * Note that this flag has no effect on any user info that could be provided in {@code fixedAuthority}.
     * @return an effective request URI as {@link String}.
     * @throws IllegalArgumentException if {@code fixedScheme} is not {@code null} and is not
     * {@code http} nor {@code https}.
     */
    public static String getEffectiveRequestUri(final ConnectionContext ctx, final HttpRequestMetaData metaData,
                                                @Nullable final String fixedScheme,
                                                @Nullable final String fixedAuthority,
                                                final boolean includeUserInfo) {
        return buildEffectiveRequestUri(ctx, metaData, fixedScheme, fixedAuthority, metaData.rawPath(),
                metaData.rawQuery(), includeUserInfo, true);
    }

    /**
     * Get the effective request URI for the provided {@link HttpRequestMetaData}.
     * <p>
     * For example, for this <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> request:
     * <pre>
     * GET /pub/WWW/TheProject.html HTTP/1.1
     * Host: www.example.org:8080
     * </pre>
     * and a {@code fixedAuthority} of {@code "example.com"} the effective request URI will be:
     * <pre>
     * http://example.com/pub/WWW/TheProject.html
     * </pre>
     *
     * @param metaData the {@link HttpRequestMetaData} to use.
     * @param fixedScheme the configured fixed scheme as {@link String}, or {@code null} if none is set.
     * @param fixedAuthority the configured fixed authority as {@link String}, or {@code null} if none is set.
     * @param includeUserInfo {@code true} if the deprecated user info sub-component must be included
     * (see <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">RFC 7230, section 2.7.1</a>).
     * Note that this flag has no effect on any user info that could be provided in {@code fixedAuthority}.
     * @return an effective request URI as {@link String}.
     * @throws IllegalArgumentException if {@code fixedScheme} is not {@code null} and is not
     * {@code http} nor {@code https}.
     */
    public static String getEffectiveRequestUri(final HttpRequestMetaData metaData,
                                                final String fixedScheme,
                                                final String fixedAuthority,
                                                final boolean includeUserInfo) {
        return buildEffectiveRequestUri(null, metaData, fixedScheme, fixedAuthority, metaData.rawPath(),
                metaData.rawQuery(), includeUserInfo, true);
    }

    /**
     * Get the base URI for the provided {@link ConnectionContext} and {@link HttpRequestMetaData}.
     * <p>
     * For example, for this <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> request:
     * <pre>
     * GET /pub/WWW/TheProject.html HTTP/1.1
     * Host: www.example.org:8080
     * </pre>
     * the base request URI will be:
     * <pre>
     * http://www.example.org:8080/
     * </pre>
     *
     * @param ctx the {@link ConnectionContext} to use.
     * @param metaData the {@link HttpRequestMetaData} to use.
     * @param includeUserInfo {@code true} if the deprecated user info sub-component must be included
     * (see <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">RFC 7230, section 2.7.1</a>).
     * @return a base request URI as {@link String}.
     */
    public static String getBaseRequestUri(final ConnectionContext ctx, final HttpRequestMetaData metaData,
                                           final boolean includeUserInfo) {
        return getBaseRequestUri(ctx, metaData, null, null, includeUserInfo);
    }

    /**
     * Get the base URI for the provided {@link ConnectionContext} and {@link HttpRequestMetaData}.
     * <p>
     * For example, for this <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> request:
     * <pre>
     * GET /pub/WWW/TheProject.html HTTP/1.1
     * Host: www.example.org:8080
     * </pre>
     * and a {@code fixedAuthority} of {@code "example.com"} the base request URI will be:
     * <pre>
     * http://example.com/
     * </pre>
     *
     * @param ctx the {@link ConnectionContext} to use.
     * @param metaData the {@link HttpRequestMetaData} to use.
     * @param fixedScheme the configured fixed scheme as {@link String}, or {@code null} if none is set.
     * @param fixedAuthority the configured fixed authority as {@link String}, or {@code null} if none is set.
     * @param includeUserInfo {@code true} if the deprecated user info sub-component must be included
     * (see <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">RFC 7230, section 2.7.1</a>).
     * Note that this flag has no effect on any user info that could be provided in {@code fixedAuthority}.
     * @return a base request URI as {@link String}.
     * @throws IllegalArgumentException if {@code fixedScheme} is not {@code null} and is not
     * {@code http} nor {@code https}.
     */
    public static String getBaseRequestUri(final ConnectionContext ctx, final HttpRequestMetaData metaData,
                                           @Nullable final String fixedScheme,
                                           @Nullable final String fixedAuthority,
                                           final boolean includeUserInfo) {
        return buildEffectiveRequestUri(ctx, metaData, fixedScheme, fixedAuthority, "/", null, includeUserInfo, false);
    }

    private static String buildEffectiveRequestUri(@Nullable final ConnectionContext ctx,
                                                   final HttpRequestMetaData metaData,
                                                   @Nullable final String fixedScheme,
                                                   @Nullable final String fixedAuthority,
                                                   @Nullable String path,
                                                   @Nullable String query,
                                                   final boolean includeUserInfo,
                                                   final boolean checkAuthorityOrAsteriskForm) {

        if (fixedScheme != null && !"http".equalsIgnoreCase(fixedScheme) && !"https".equalsIgnoreCase(fixedScheme)) {
            throw new IllegalArgumentException("Unsupported scheme: " + fixedScheme);
        }

        if (ctx == null && (fixedScheme == null || fixedAuthority == null)) {
            throw new IllegalArgumentException("Context required without scheme and authority");
        }

        // https://tools.ietf.org/html/rfc7230#section-5.5
        // If the request-target is in authority-form or asterisk-form, the effective request URI's combined
        // path and query component is empty.
        if (checkAuthorityOrAsteriskForm && (CONNECT.equals(metaData.method()) || OPTIONS.equals(metaData.method()))) {
            path = query = null;
        }

        final String metadataScheme = metaData.scheme();
        if (metadataScheme != null) {
            // absolute form
            return buildRequestUri(
                    metadataScheme,
                    includeUserInfo ? metaData.userInfo() : null,
                    metaData.host(),
                    metaData.port(),
                    path,
                    query);
        }

        final String scheme = fixedScheme != null ? fixedScheme.toLowerCase() :
                (ctx.sslSession() != null ? "https" : "http");

        final HostAndPort effectiveHostAndPort;
        if (fixedAuthority != null) {
            return buildRequestUri(
                    scheme,
                    fixedAuthority,
                    path,
                    query);
        } else if ((effectiveHostAndPort = metaData.effectiveHostAndPort()) != null) {
            return buildRequestUri(
                    scheme,
                    includeUserInfo ? metaData.userInfo() : null,
                    effectiveHostAndPort.hostName(),
                    effectiveHostAndPort.port(),
                    path,
                    query);
        } else {
            if (!(ctx.localAddress() instanceof InetSocketAddress)) {
                throw new IllegalArgumentException("ConnectionContext#getLocalAddress is not an InetSocketAddress: "
                        + ctx.localAddress());
            }
            final InetSocketAddress localAddress = (InetSocketAddress) ctx.localAddress();
            final boolean defaultPort = ("http".equals(scheme) && localAddress.getPort() == 80)
                    || ("https".equals(scheme) && localAddress.getPort() == 443);

            return buildRequestUri(
                    scheme,
                    null,
                    localAddress.getHostName(),
                    defaultPort ? -1 : localAddress.getPort(),
                    path,
                    query);
        }
    }

    private static String buildRequestUri(final String scheme,
                                          final String authority,
                                          @Nullable final String path,
                                          @Nullable final String query) {

        final StringBuilder sb =
                newRequestUriStringBuilder(scheme, authority.length(), path, query)
                        .append(authority);

        return appendPathAndQuery(path, query, sb);
    }

    private static String buildRequestUri(final String scheme,
                                          @Nullable final String userInfo,
                                          @Nullable final String host,
                                          final int port,
                                          @Nullable final String path,
                                          @Nullable final String query) {

        final StringBuilder sb =
                newRequestUriStringBuilder(scheme,
                        (userInfo != null ? userInfo.length() + 1 : 0)
                                + (host != null ? host.length() : 0)
                                + (port >= 0 ? 6 : 0), // maximum port is 65535, +1 for :
                        path, query);

        if (userInfo != null) {
            sb.append(userInfo).append('@');
        }
        if (host != null) {
            sb.append(host);
        }
        if (port >= 0) {
            sb.append(':').append(port);
        }
        return appendPathAndQuery(path, query, sb);
    }

    private static StringBuilder newRequestUriStringBuilder(final String scheme,
                                                            final int authorityLength,
                                                            @Nullable final String path,
                                                            @Nullable final String query) {
        return new StringBuilder(
                scheme.length() + 3
                        + authorityLength
                        + (path != null ? path.length() : 0)
                        + (query != null ? query.length() + 1 : 0))
                .append(scheme)
                .append("://");
    }

    private static String appendPathAndQuery(@Nullable final String path,
                                             @Nullable final String query,
                                             final StringBuilder sb) {
        if (path != null) {
            sb.append(path);
        }
        if (query != null && !query.isEmpty()) {
            sb.append('?').append(query);
        }

        return sb.toString();
    }
}
