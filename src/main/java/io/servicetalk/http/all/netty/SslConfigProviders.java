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
package io.servicetalk.http.all.netty;

import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.SslConfig;

import static io.servicetalk.http.all.netty.HttpScheme.HTTP;
import static io.servicetalk.http.all.netty.HttpScheme.HTTPS;
import static io.servicetalk.http.all.netty.HttpScheme.NONE;
import static io.servicetalk.transport.api.SslConfigBuilder.forClient;

/**
 * Set of default {@link SslConfigProvider}s.
 */
public final class SslConfigProviders {

    private static final SslConfigProvider PLAIN = new SslConfigProvider() {
        @Override
        public int defaultPort(final HttpScheme scheme, final String effectiveHost) {
            if (scheme == NONE) {
                return HTTP.getDefaultPort();
            }
            return scheme.getDefaultPort();
        }

        @Override
        public SslConfig forHostAndPort(final HostAndPort hostAndPort) {
            return null;
        }
    };

    private static final SslConfigProvider SECURE = new SslConfigProvider() {
        @Override
        public int defaultPort(final HttpScheme scheme, final String effectiveHost) {
            if (scheme == NONE) {
                return HTTPS.getDefaultPort();
            }
            return scheme.getDefaultPort();
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
     *
     * @return The {@link SslConfigProvider} for secure HTTPS connection by default.
     */
    public static SslConfigProvider secureByDefault() {
        return SECURE;
    }
}
