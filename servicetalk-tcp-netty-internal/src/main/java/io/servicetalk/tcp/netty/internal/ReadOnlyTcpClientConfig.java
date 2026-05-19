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
import io.servicetalk.transport.api.DelegatingClientSslConfig;

import io.netty.handler.ssl.SslContext;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SslContextFactory.forClient;
import static java.util.Objects.requireNonNull;

/**
 Read only view of {@link TcpClientConfig}.
 */
public final class ReadOnlyTcpClientConfig extends AbstractReadOnlyTcpConfig {
    @Nullable
    private final SslContext sslContext;
    @Nullable
    private final ClientSslConfig sslConfig;
    @Nullable
    private final SslContext proxySslContext;
    @Nullable
    private final ClientSslConfig proxySslConfig;

    ReadOnlyTcpClientConfig(final TcpClientConfig from) {
        super(from);
        sslConfig = from.sslConfig();
        sslContext = sslConfig == null ? null : forClient(sslConfig);
        proxySslConfig = from.proxySslConfig();
        proxySslContext = proxySslConfig == null ? null : forClient(proxySslConfig);
    }

    private ReadOnlyTcpClientConfig(final ReadOnlyTcpClientConfig config, final String peerHost,
                                    final int peerPort, @Nullable final String sniHostname,
                                    @Nullable final String hostnameVerificationAlgorithm) {
        super(config);
        sslConfig = new PeerHostNameOverrideClientSslConfig(requireNonNull(config.sslConfig), peerHost, peerPort,
                sniHostname, hostnameVerificationAlgorithm);
        // peerHost, peerPort, sniHostname do not impact the sslContext, so we can avoid the costly rebuilding.
        sslContext = config.sslContext;
        // Proxy SSL config is bound to the proxy address; peer overrides are about the origin and do not apply.
        proxySslConfig = config.proxySslConfig;
        proxySslContext = config.proxySslContext;
    }

    /**
     * Returns the {@link SslContext}.
     *
     * @return {@link SslContext}, {@code null} if none specified
     */
    @Nullable
    @Override
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Get the preferred ALPN protocol. If a protocol sensitive decision must be made without knowing which protocol is
     * negotiated (e.g. at the client level) this protocol can be used as a best guess.
     * @return the preferred ALPN protocol.
     */
    @Nullable
    public String preferredAlpnProtocol() {
        if (sslConfig == null) {
            return null;
        }
        List<String> alpnProtocols = sslConfig.alpnProtocols();
        return alpnProtocols != null && !alpnProtocols.isEmpty() ? alpnProtocols.get(0) : null;
    }

    /**
     * Get the {@link ClientSslConfig}.
     *
     * @return the {@link ClientSslConfig}, or {@code null} if SSL/TLS is not configured.
     */
    @Nullable
    @Override
    public ClientSslConfig sslConfig() {
        return sslConfig;
    }

    /**
     * Get the {@link SslContext} used for the eager TLS handshake to a fronting proxy, if configured.
     *
     * @return the proxy {@link SslContext}, or {@code null} if not configured.
     */
    @Nullable
    SslContext proxySslContext() {
        return proxySslContext;
    }

    /**
     * Get the {@link ClientSslConfig} used for the eager TLS handshake to a fronting proxy, if configured.
     *
     * @return the proxy {@link ClientSslConfig}, or {@code null} if not configured.
     */
    @Nullable
    ClientSslConfig proxySslConfig() {
        return proxySslConfig;
    }

    /**
     * Create a new {@link ReadOnlyTcpClientConfig} replacing {@link ClientSslConfig#peerHost()},
     * {@link ClientSslConfig#peerPort()}, and {@link ClientSslConfig#sniHostname()}.
     * @param peerHost The new value for {@link ClientSslConfig#peerHost()}.
     * @param peerPort The new value for {@link ClientSslConfig#peerPort()}.
     * @param sniHostname The new value for {@link ClientSslConfig#sniHostname()}.
     * @param hostnameVerificationAlgorithm The new value for {@link ClientSslConfig#hostnameVerificationAlgorithm()}.
     * @return a new {@link ReadOnlyTcpClientConfig} replacing {@link ClientSslConfig#peerHost()} and
     * {@link ClientSslConfig#peerPort()}.
     */
    public ReadOnlyTcpClientConfig withSslConfigPeerHost(String peerHost, int peerPort, @Nullable String sniHostname,
                                                         @Nullable final String hostnameVerificationAlgorithm) {
        return new ReadOnlyTcpClientConfig(this, peerHost, peerPort, sniHostname, hostnameVerificationAlgorithm);
    }

    private static final class PeerHostNameOverrideClientSslConfig extends DelegatingClientSslConfig {
        private final String peerHost;
        private final int peerPort;
        @Nullable
        private final String sniHostname;
        @Nullable
        private final String hostnameVerificationAlgorithm;

        PeerHostNameOverrideClientSslConfig(final ClientSslConfig delegate, final String peerHost, final int peerPort,
                                            @Nullable final String sniHostname,
                                            @Nullable final String hostnameVerificationAlgorithm) {
            super(delegate);
            this.peerHost = peerHost;
            this.peerPort = peerPort;
            this.sniHostname = sniHostname;
            this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
        }

        @Override
        public String peerHost() {
            return peerHost;
        }

        @Override
        public int peerPort() {
            return peerPort;
        }

        @Override
        public String sniHostname() {
            return sniHostname;
        }

        @Override
        public String hostnameVerificationAlgorithm() {
            return hostnameVerificationAlgorithm;
        }
    }
}
