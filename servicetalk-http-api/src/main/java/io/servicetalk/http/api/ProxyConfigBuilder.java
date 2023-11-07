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

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link ProxyConfig}.
 *
 * @param <A> the type of address
 */
public final class ProxyConfigBuilder<A> {

    private final A address;
    private Consumer<HttpHeaders> connectRequestHeadersInitializer = __ -> { };

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
     * Builds a new {@link ProxyConfig}.
     *
     * @return a new {@link ProxyConfig}.
     */
    public ProxyConfig<A> build() {
        return new DefaultProxyConfig<>(address, connectRequestHeadersInitializer);
    }

    private static final class DefaultProxyConfig<A> implements ProxyConfig<A> {

        private final A address;
        private final Consumer<HttpHeaders> connectRequestHeadersInitializer;

        private DefaultProxyConfig(final A address, final Consumer<HttpHeaders> connectRequestHeadersInitializer) {
            this.address = address;
            this.connectRequestHeadersInitializer = connectRequestHeadersInitializer;
        }

        @Override
        public A address() {
            return address;
        }

        @Override
        public Consumer<HttpHeaders> connectRequestHeadersInitializer() {
            return connectRequestHeadersInitializer;
        }
    }
}
