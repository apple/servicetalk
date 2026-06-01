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

import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Configuration for a proxy.
 *
 * @param <A> the type of address
 * @see ProxyConfigBuilder
 */
public interface ProxyConfig<A> {

    /**
     * Address of the proxy.
     * <p>
     * Usually, this is an unresolved proxy address and its type must match the type of address before resolution used
     * by {@link SingleAddressHttpClientBuilder}. However, if the client builder was created for a resolved address,
     * this address must also be already resolved. Otherwise, a runtime exception will occur.
     *
     * @return address of the proxy
     */
    A address();

    /**
     * An initializer for {@link HttpHeaders} related to
     * <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6">HTTP/1.1 CONNECT</a> request.
     * <p>
     * When this {@link ProxyConfig} is used for secure proxy tunnels (when
     * {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig) ClientSslConfig} is configured) the tunnel is
     * always initialized using {@code HTTP/1.1 CONNECT} request. This {@link Consumer} can be used to set custom
     * {@link HttpHeaders} for that request, like {@link HttpHeaderNames#PROXY_AUTHORIZATION proxy-authorization},
     * tracing, or any other header.
     *
     * @return An initializer for {@link HttpHeaders} related to
     * <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6">HTTP/1.1 CONNECT</a> request
     */
    Consumer<HttpHeaders> connectRequestHeadersInitializer();

    /**
     * {@link ClientSslConfig} used for the TLS handshake to the proxy itself.
     * <p>
     * This is independent of the {@link SingleAddressHttpClientBuilder#sslConfig(ClientSslConfig) origin SSL config}
     * which applies to the inner TLS handshake performed after the {@code HTTP/1.1 CONNECT} tunnel is established.
     * Setting this enables a "double-TLS" pattern where the connection to the proxy is secured with one
     * certificate/trust configuration and the inner connection to the origin uses a different one — required for
     * proxies that perform mTLS at their own front door).
     * <p>
     * When {@code null} (default) the connection to the proxy is not wrapped in an outer TLS layer; the existing
     * single-TLS behavior (origin TLS only, performed inside the CONNECT tunnel) is unchanged.
     * <p>
     * {@link ClientSslConfig#peerHost() peerHost}, {@link ClientSslConfig#peerPort() peerPort}, and
     * {@link ClientSslConfig#sniHostname() sniHostname} are inferred from {@link #address()} when unset on the
     * returned config.
     *
     * @return the {@link ClientSslConfig} for the proxy TLS stage, or {@code null} for plaintext to the proxy.
     */
    @Nullable
    default ClientSslConfig sslConfig() {
        return null;
    }

    /**
     * Returns a {@link ProxyConfig} for the specified {@code address}.
     * <p>
     * All other {@link ProxyConfig} options will use their default values applied by {@link ProxyConfigBuilder}.
     *
     * @param address Address of the proxy
     * @param <A> the type of address
     * @return a {@link ProxyConfig} for the specified {@code address}
     * @see ProxyConfigBuilder
     */
    static <A> ProxyConfig<A> forAddress(A address) {
        return new ProxyConfigBuilder<>(address).build();
    }
}
