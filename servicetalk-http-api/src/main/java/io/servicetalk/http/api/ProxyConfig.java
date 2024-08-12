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
