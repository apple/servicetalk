/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpUri.HTTPS_DEFAULT_PORT;
import static io.servicetalk.http.api.HttpUri.HTTPS_SCHEME;
import static io.servicetalk.http.api.HttpUri.HTTP_DEFAULT_PORT;
import static io.servicetalk.http.api.HttpUri.HTTP_SCHEME;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;

/**
 * Set of default {@link SslConfigProvider}s.
 */
public final class SslConfigProviders {

    private static final SslConfigProvider PLAIN = new SslConfigProvider() {
        @Override
        public int defaultPort(@Nullable final String scheme, final String effectiveHost) {
            return resolvePort(scheme, false);
        }

        @Override
        public SslConfig forHostAndPort(final HostAndPort hostAndPort) {
            return null;
        }
    };

    private static final SslConfigProvider SECURE = new SslConfigProvider() {
        @Override
        public int defaultPort(@Nullable final String scheme, final String effectiveHost) {
            return resolvePort(scheme, true);
        }

        @Override
        public SslConfig forHostAndPort(final HostAndPort hostAndPort) {
            return forClient(hostAndPort).build();
        }
    };

    private SslConfigProviders() {
        // no instances
    }

    /**
     * {@link SslConfigProvider} for plain HTTP connection by default.
     * <p>
     * It returns the <a href="https://tools.ietf.org/html/rfc7230#section-2.7.1">default TCP port 80</a> if no
     * explicit port and scheme are given in a request, and {@code null} instead of {@link SslConfig}.
     * {@link SslConfigProvider#defaultPort(String, String)} may throw {@link IllegalArgumentException} if the provided
     * scheme is unknown.
     *
     * @return The {@link SslConfigProvider} for plain HTTP connection by default.
     */
    public static SslConfigProvider plainByDefault() {
        return PLAIN;
    }

    /**
     * {@link SslConfigProvider} for secure HTTPS connection by default.
     * <p>
     * It returns the <a href="https://tools.ietf.org/html/rfc7230#section-2.7.2">default TCP port 443</a> if no
     * explicit port and scheme, are given in a request, and a default client {@link SslConfig} for any specified
     * {@link HostAndPort}.
     * {@link SslConfigProvider#defaultPort(String, String)} may throw {@link IllegalArgumentException} if the provided
     * scheme is unknown.
     *
     * @return The {@link SslConfigProvider} for secure HTTPS connection by default.
     */
    public static SslConfigProvider secureByDefault() {
        return SECURE;
    }

    private static int resolvePort(@Nullable final String scheme, final boolean secure) {
        if (scheme == null || scheme.isEmpty()) {
            return secure ? HTTPS_DEFAULT_PORT : HTTP_DEFAULT_PORT;
        }
        if (HTTP_SCHEME.equalsIgnoreCase(scheme)) {
            return HTTP_DEFAULT_PORT;
        }
        if (HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
            return HTTPS_DEFAULT_PORT;
        }
        throw new IllegalArgumentException("Unknown scheme: " + scheme);
    }
}
