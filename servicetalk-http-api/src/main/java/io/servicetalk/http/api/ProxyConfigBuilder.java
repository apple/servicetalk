/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ClientSslConfig;

import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link ProxyConfig}.
 *
 * @param <A> the type of address
 */
public final class ProxyConfigBuilder<A> {

    private static final Consumer<HttpHeaders> NOOP_HEADERS_CONSUMER = new Consumer<HttpHeaders>() {
        @Override
        public void accept(final HttpHeaders headers) {
        }

        @Override
        public String toString() {
            return "NOOP_HEADERS_CONSUMER";
        }
    };

    private final A address;
    private Consumer<HttpHeaders> connectRequestHeadersInitializer = NOOP_HEADERS_CONSUMER;
    @Nullable
    private ClientSslConfig sslConfig;

    /**
     * Creates a new instance.
     *
     * @param address Proxy address
     * @see ProxyConfig#address()
     */
    public ProxyConfigBuilder(final A address) {
        this.address = requireNonNull(address);
    }

    /**
     * Sets an initializer for {@link HttpHeaders} related to
     * <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6">HTTP/1.1 CONNECT</a> request.
     *
     * @param connectRequestHeadersInitializer {@link Consumer} that can be used to set custom {@link HttpHeaders} for
     * {@code HTTP/1.1 CONNECT} request (auth, tracing, etc.)
     * @return {@code this}
     * @see ProxyConfig#connectRequestHeadersInitializer()
     */
    public ProxyConfigBuilder<A> connectRequestHeadersInitializer(
            final Consumer<HttpHeaders> connectRequestHeadersInitializer) {
        this.connectRequestHeadersInitializer = requireNonNull(connectRequestHeadersInitializer);
        return this;
    }

    /**
     * Sets the {@link ClientSslConfig} for the TLS handshake to the proxy itself.
     * <p>
     * Distinct from the origin SSL config configured via
     * {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig)}, which applies to the inner TLS handshake
     * performed after the {@code HTTP/1.1 CONNECT} tunnel is established. {@code peerHost}, {@code peerPort},
     * and {@code sniHostname} default from the proxy {@link ProxyConfig#address() address} when unset; ALPN is
     * restricted to {@code http/1.1}. See {@link ProxyConfig#sslConfig()} for details.
     *
     * @param sslConfig the {@link ClientSslConfig} for the proxy TLS stage, or {@code null} for plaintext to the proxy.
     * @return {@code this}
     * @see ProxyConfig#sslConfig()
     */
    public ProxyConfigBuilder<A> sslConfig(@Nullable final ClientSslConfig sslConfig) {
        this.sslConfig = sslConfig;
        return this;
    }

    /**
     * Builds a new {@link ProxyConfig}.
     *
     * @return a new {@link ProxyConfig}.
     */
    public ProxyConfig<A> build() {
        return new DefaultProxyConfig<>(address, connectRequestHeadersInitializer, sslConfig);
    }

    private static final class DefaultProxyConfig<A> implements ProxyConfig<A> {

        private final A address;
        private final Consumer<HttpHeaders> connectRequestHeadersInitializer;
        @Nullable
        private final ClientSslConfig sslConfig;

        private DefaultProxyConfig(final A address, final Consumer<HttpHeaders> connectRequestHeadersInitializer,
                                   @Nullable final ClientSslConfig sslConfig) {
            this.address = address;
            this.connectRequestHeadersInitializer = connectRequestHeadersInitializer;
            this.sslConfig = sslConfig;
        }

        @Override
        public A address() {
            return address;
        }

        @Override
        public Consumer<HttpHeaders> connectRequestHeadersInitializer() {
            return connectRequestHeadersInitializer;
        }

        @Nullable
        @Override
        public ClientSslConfig sslConfig() {
            return sslConfig;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DefaultProxyConfig)) {
                return false;
            }

            final DefaultProxyConfig<?> that = (DefaultProxyConfig<?>) o;
            return address.equals(that.address) &&
                    connectRequestHeadersInitializer.equals(that.connectRequestHeadersInitializer) &&
                    Objects.equals(sslConfig, that.sslConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, connectRequestHeadersInitializer, sslConfig);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{address=" + address +
                    ", connectRequestHeadersInitializer=" + connectRequestHeadersInitializer +
                    ", sslConfig=" + sslConfig +
                    '}';
        }
    }
}
