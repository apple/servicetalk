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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.tcp.netty.internal.TcpServerConfig;
import io.servicetalk.transport.api.DelegatingServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_ENABLE_PUSH;
import static java.util.Objects.requireNonNull;

final class HttpServerConfig {

    private final TcpServerConfig tcpConfig;
    private final HttpConfig httpConfig;
    @Nullable
    private HttpLifecycleObserver lifecycleObserver;

    HttpServerConfig() {
        tcpConfig = new TcpServerConfig();
        httpConfig = new HttpConfig(h2Config -> {
            final Http2Settings settings = h2Config.initialSettings();
            final Long pushEnabled = settings.settingValue(SETTINGS_ENABLE_PUSH);
            if (pushEnabled != null && pushEnabled != 0) {
                throw new IllegalArgumentException(
                        "Server cannot set SETTINGS_ENABLE_PUSH value other than 0, settings=" + settings);
            }
        });
    }

    HttpServerConfig(final HttpServerConfig from) {
        tcpConfig = new TcpServerConfig(from.tcpConfig);
        httpConfig = new HttpConfig(from.httpConfig);
        lifecycleObserver = from.lifecycleObserver;
    }

    TcpServerConfig tcpConfig() {
        return tcpConfig;
    }

    HttpConfig httpConfig() {
        return httpConfig;
    }

    @Nullable
    HttpLifecycleObserver lifecycleObserver() {
        return lifecycleObserver;
    }

    HttpServerConfig lifecycleObserver(final HttpLifecycleObserver observer) {
        this.lifecycleObserver = requireNonNull(observer);
        return this;
    }

    ReadOnlyHttpServerConfig asReadOnly() {
        applySslConfigOverrides();
        final ReadOnlyHttpServerConfig roConfig = new ReadOnlyHttpServerConfig(this);
        if (roConfig.tcpConfig().sslContext() == null && roConfig.h1Config() != null && roConfig.h2Config() != null) {
            throw new IllegalStateException("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        }
        return roConfig;
    }

    private void applySslConfigOverrides() {
        ServerSslConfig sslConfig = tcpConfig.sslConfig();
        if (sslConfig != null) {
            sslConfig = new DelegatingHttpServerSslConfig(sslConfig,
                    httpAlpnProtocols(sslConfig.alpnProtocols(), httpConfig.supportedAlpnProtocols()));
            Map<String, ServerSslConfig> sniMap = tcpConfig.sniConfig();
            if (sniMap == null) {
                tcpConfig.sslConfig(sslConfig, tcpConfig.acceptInsecureConnections());
            } else {
                // Make a copy in case the original map is unmodifiable. Use LinkedHashMap to preserve iteration order
                // in case there is order precedence in the matching algorithm.
                Map<String, ServerSslConfig> sniMapOverrides = new LinkedHashMap<>(sniMap.size());
                for (Entry<String, ServerSslConfig> sniConfigEntry : sniMap.entrySet()) {
                    ServerSslConfig sniConfig = sniConfigEntry.getValue();
                    sniMapOverrides.put(sniConfigEntry.getKey(), new DelegatingHttpServerSslConfig(sniConfig,
                                    httpAlpnProtocols(sniConfig.alpnProtocols(), httpConfig.supportedAlpnProtocols())));
                }
                tcpConfig.sslConfig(sslConfig, sniMapOverrides, tcpConfig.sniMaxClientHelloLength(),
                        tcpConfig.sniClientHelloTimeout(), tcpConfig.acceptInsecureConnections());
            }
        }
    }

    @Nullable
    static List<String> httpAlpnProtocols(@SuppressWarnings("unused") @Nullable List<String> sslConfigAlpn,
                                          List<String> httpAlpn) {
        // We ignore sslConfigAlpn because otherwise we would need to coordinate the HttpProtocolConfig and associated
        // channel initialization with which ALPN protocol is selected.
        return !httpAlpn.isEmpty() ? httpAlpn : null;
    }

    private static final class DelegatingHttpServerSslConfig extends DelegatingServerSslConfig {
        @Nullable
        private final List<String> alpnProtocols;

        DelegatingHttpServerSslConfig(ServerSslConfig sslConfig,
                                      @Nullable List<String> alpnProtocols) {
            super(sslConfig);
            this.alpnProtocols = alpnProtocols;
        }

        @Override
        public List<String> alpnProtocols() {
            return alpnProtocols;
        }
    }
}
