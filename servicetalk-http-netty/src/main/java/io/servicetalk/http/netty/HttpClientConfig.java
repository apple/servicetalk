/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.ProxyConfig;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.DelegatingClientSslConfig;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_ENABLE_PUSH;
import static io.servicetalk.http.netty.HttpServerConfig.httpAlpnProtocols;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV4Address;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV6Address;
import static java.util.Objects.requireNonNull;

final class HttpClientConfig {

    private final TcpClientConfig tcpConfig;
    private final HttpConfig protocolConfigs;
    @Nullable
    private ProxyConfig<String> proxyConfig;
    @Nullable
    private String fallbackPeerHost;
    private int fallbackPeerPort = -1;
    private boolean inferPeerHost = true;
    private boolean inferPeerPort = true;
    private boolean inferSniHostname = true;

    HttpClientConfig() {
        tcpConfig = new TcpClientConfig();
        protocolConfigs = new HttpConfig(h2Config -> {
            final Http2Settings settings = h2Config.initialSettings();
            final Long pushEnabled = settings.settingValue(SETTINGS_ENABLE_PUSH);
            if (pushEnabled != null && pushEnabled != 0) {
                throw new IllegalArgumentException("Server Push is not supported by the client, " +
                        "expected SETTINGS_ENABLE_PUSH value is null or 0, settings=" + settings);
            }
            final Long maxConcurrentStreams = settings.maxConcurrentStreams();
            if (maxConcurrentStreams != null && maxConcurrentStreams != 0) {
                throw new IllegalArgumentException("Server Push is not supported by the client, " +
                        "expected MAX_CONCURRENT_STREAMS value is null or 0, settings=" + settings);
            }
        });
    }

    HttpClientConfig(final HttpClientConfig from) {
        tcpConfig = new TcpClientConfig(from.tcpConfig());
        protocolConfigs = new HttpConfig(from.protocolConfigs());
        proxyConfig = from.proxyConfig;
        fallbackPeerHost = from.fallbackPeerHost;
        fallbackPeerPort = from.fallbackPeerPort;
        inferPeerHost = from.inferPeerHost;
        inferPeerPort = from.inferPeerPort;
        inferSniHostname = from.inferSniHostname;
    }

    TcpClientConfig tcpConfig() {
        return tcpConfig;
    }

    HttpConfig protocolConfigs() {
        return protocolConfigs;
    }

    @Nullable
    ProxyConfig<String> proxyConfig() {
        return proxyConfig;
    }

    void proxyConfig(final CharSequence connectAddress, final ProxyConfig<?> proxyConfig) {
        // Original ProxyConfig.address() is used only by DefaultSingleAddressHttpClientBuilder. For the actual
        // ProxyConnectLBHttpConnectionFactory, we need only "connectAddress". To simplify internal state, we override
        // ProxyConfig.address() with "connectAddress" and delegate all other methods to original ProxyConfig.
        this.proxyConfig = new DelegatingProxyConfig(connectAddress.toString(), proxyConfig);
    }

    void fallbackPeerHost(@Nullable String fallbackPeerHost) {
        this.fallbackPeerHost = fallbackPeerHost;
    }

    void fallbackPeerPort(int fallbackPeerPort) {
        this.fallbackPeerPort = fallbackPeerPort;
    }

    void inferPeerHost(boolean shouldInfer) {
        this.inferPeerHost = shouldInfer;
    }

    void inferPeerPort(boolean shouldInfer) {
        this.inferPeerPort = shouldInfer;
    }

    void inferSniHostname(boolean shouldInfer) {
        this.inferSniHostname = shouldInfer;
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
        final List<String> httpAlpnProtocols = protocolConfigs.supportedAlpnProtocols();
        ClientSslConfig sslConfig = tcpConfig.sslConfig();
        if (sslConfig != null) {
            final List<String> configAlpn = sslConfig.alpnProtocols();
            final String configPeerHost = sslConfig.peerHost();
            final int configPeerPort = sslConfig.peerPort();
            final String configSni = sslConfig.sniHostname();
            tcpConfig.sslConfig(new DelegatingClientSslConfig(sslConfig) {
                @Nullable
                private final List<String> alpnProtocols = httpAlpnProtocols(configAlpn, httpAlpnProtocols);

                @Nullable
                private final String peerHost =
                        (configPeerHost == null && inferPeerHost) ? fallbackPeerHost : configPeerHost;

                private final int peerPort =
                        (configPeerPort < 0 && inferPeerPort) ? fallbackPeerPort : configPeerPort;

                @Nullable
                private final String sniHostname =
                        (configSni == null && inferSniHostname) ? filterSniName(fallbackPeerHost) : configSni;

                @Override
                public List<String> alpnProtocols() {
                    return alpnProtocols;
                }

                @Nullable
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
    private static String filterSniName(@Nullable String peerHost) {
        // https://tools.ietf.org/html/rfc6066#section-3
        // Literal IPv4 and IPv6 addresses are not permitted in "HostName".
        return peerHost == null || isValidIpV4Address(peerHost) || isValidIpV6Address(peerHost) ? null : peerHost;
    }

    private static final class DelegatingProxyConfig implements ProxyConfig<String> {

        private final String address;
        private final ProxyConfig<?> delegate;

        DelegatingProxyConfig(final String address, final ProxyConfig<?> delegate) {
            this.address = requireNonNull(address);
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public String address() {
            return address;
        }

        @Override
        public Consumer<HttpHeaders> connectRequestHeadersInitializer() {
            return delegate.connectRequestHeadersInitializer();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DelegatingProxyConfig)) {
                return false;
            }

            final DelegatingProxyConfig that = (DelegatingProxyConfig) o;
            if (!address.equals(that.address)) {
                return false;
            }
            return delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            int result = address.hashCode();
            result = 31 * result + delegate.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{address='" + address + '\'' +
                    ", delegate=" + delegate +
                    '}';
        }
    }
}
