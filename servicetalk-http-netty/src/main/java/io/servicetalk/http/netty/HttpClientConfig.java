/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ClientSslConfig;

import java.util.List;
import javax.annotation.Nullable;

import static io.netty.util.NetUtil.isValidIpV4Address;
import static io.netty.util.NetUtil.isValidIpV6Address;
import static io.netty.util.internal.StringUtil.isNullOrEmpty;
import static io.servicetalk.http.netty.HttpServerConfig.httpAlpnProtocols;
import static java.util.Objects.requireNonNull;

final class HttpClientConfig {

    private final TcpClientConfig tcpConfig;
    private final HttpConfig protocolConfigs;
    @Nullable
    private CharSequence connectAddress;
    private String fallbackPeerHost = "";
    private int fallbackPeerPort;

    HttpClientConfig() {
        tcpConfig = new TcpClientConfig();
        protocolConfigs = new HttpConfig();
    }

    HttpClientConfig(final HttpClientConfig from) {
        tcpConfig = new TcpClientConfig(from.tcpConfig());
        protocolConfigs = new HttpConfig(from.protocolConfigs());
        connectAddress = from.connectAddress;
        fallbackPeerHost = from.fallbackPeerHost;
        fallbackPeerPort = from.fallbackPeerPort;
    }

    TcpClientConfig tcpConfig() {
        return tcpConfig;
    }

    HttpConfig protocolConfigs() {
        return protocolConfigs;
    }

    @Nullable
    CharSequence connectAddress() {
        return connectAddress;
    }

    void connectAddress(@Nullable final CharSequence connectAddress) {
        this.connectAddress = connectAddress;
    }

    void fallbackPeerHost(String fallbackPeerHost) {
        this.fallbackPeerHost = requireNonNull(fallbackPeerHost);
    }

    void fallbackPeerPort(int fallbackPeerPort) {
        this.fallbackPeerPort = fallbackPeerPort;
    }

    ReadOnlyHttpClientConfig asReadOnly() {
        applySslConfigOverrides();
        final ReadOnlyHttpClientConfig roConfig = new ReadOnlyHttpClientConfig(this);
        if (roConfig.tcpConfig().sslContext() == null && roConfig.h1Config() != null && roConfig.h2Config() != null) {
            throw new IllegalStateException("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        }
        return roConfig;
    }

    private void applySslConfigOverrides() {
        final List<String> fallbackAlpnProtocols = protocolConfigs.supportedAlpnProtocols();
        ClientSslConfig sslConfig = tcpConfig.sslConfig();
        if (sslConfig != null) {
            final List<String> sslConfigAlpn = sslConfig.alpnProtocols();
            final String sslConfigPeerHost = sslConfig.peerHost();
            final int sslConfigPeerPort = sslConfig.peerPort();
            final String sslConfigSni = sslConfig.sniHostname();
            tcpConfig.sslConfig(new DelegatingClientSslConfig(sslConfig) {
                @Nullable
                private final List<String> alpnProtocols = httpAlpnProtocols(sslConfigAlpn, fallbackAlpnProtocols);
                private final String peerHost = isNullOrEmpty(sslConfigPeerHost) ? fallbackPeerHost : sslConfigPeerHost;
                private final int peerPort = sslConfigPeerPort < 0 ? fallbackPeerPort : sslConfigPeerPort;
                @Nullable
                private final String sniHostname = sslConfigSni != null ? sslConfigSni :
                        filterSniName(fallbackPeerHost);

                @Override
                public List<String> alpnProtocols() {
                    return alpnProtocols;
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
            });
        }
    }

    @Nullable
    private static String filterSniName(String peerHost) {
        // https://tools.ietf.org/html/rfc6066#section-3
        // Literal IPv4 and IPv6 addresses are not permitted in "HostName".
        return peerHost.isEmpty() || isValidIpV4Address(peerHost) || isValidIpV6Address(peerHost) ? null : peerHost;
    }
}
