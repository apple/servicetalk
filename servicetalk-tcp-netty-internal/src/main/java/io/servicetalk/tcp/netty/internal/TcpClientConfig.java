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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ClientSslConfig;

import javax.annotation.Nullable;

/**
 * Configuration for TCP based clients.
 */
public final class TcpClientConfig extends AbstractTcpConfig {

    @Nullable
    private ClientSslConfig sslConfig;
    @Nullable
    private ClientSslConfig proxySslConfig;

    /**
     * New instance.
     */
    public TcpClientConfig() {
    }

    /**
     * Copy constructor.
     *
     * @param from the source {@link TcpClientConfig} to copy from
     */
    public TcpClientConfig(final TcpClientConfig from) {
        super(from);
        sslConfig = from.sslConfig;
        proxySslConfig = from.proxySslConfig;
    }

    /**
     * Get the {@link ClientSslConfig}.
     *
     * @return the {@link ClientSslConfig}, or {@code null} if SSL/TLS is not configured.
     */
    @Nullable
    public ClientSslConfig sslConfig() {
        return sslConfig;
    }

    /**
     * Get the {@link ClientSslConfig} used for the TLS handshake to a proxy that fronts the connection.
     * <p>
     * Distinct from {@link #sslConfig()}, which applies to the inner (origin) TLS handshake performed after the
     * proxy CONNECT tunnel is established. When non-{@code null}, an eager TLS handshake is performed against the
     * proxy before any CONNECT exchange.
     *
     * @return the proxy {@link ClientSslConfig}, or {@code null} for plaintext to the proxy.
     */
    @Nullable
    public ClientSslConfig proxySslConfig() {
        return proxySslConfig;
    }

    /**
     * Create a read only view of this object.
     * @return a read only view of this object.
     */
    public ReadOnlyTcpClientConfig asReadOnly() {
        return new ReadOnlyTcpClientConfig(this);
    }

    /**
     * Add SSL/TLS related config.
     *
     * @param sslConfig the {@link ClientSslConfig}.
     */
    public void sslConfig(@Nullable final ClientSslConfig sslConfig) {
        this.sslConfig = sslConfig;
    }

    /**
     * Add SSL/TLS config used for the proxy hop (eager handshake performed before CONNECT).
     *
     * @param proxySslConfig the {@link ClientSslConfig} used for the proxy TLS stage.
     */
    public void proxySslConfig(@Nullable final ClientSslConfig proxySslConfig) {
        this.proxySslConfig = proxySslConfig;
    }
}
