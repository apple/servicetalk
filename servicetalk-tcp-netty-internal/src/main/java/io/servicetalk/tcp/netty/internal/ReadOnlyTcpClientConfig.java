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

import io.netty.handler.ssl.SslContext;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SslContextFactory.forClient;

/**
 Read only view of {@link TcpClientConfig}.
 */
public final class ReadOnlyTcpClientConfig extends AbstractReadOnlyTcpConfig<ClientSslConfig> {
    @Nullable
    private final SslContext sslContext;
    @Nullable
    private final ClientSslConfig sslConfig;

    ReadOnlyTcpClientConfig(final TcpClientConfig from) {
        super(from);
        sslConfig = from.sslConfig();
        sslContext = sslConfig == null ? null : forClient(sslConfig);
    }

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
    public ClientSslConfig sslConfig() {
        return sslConfig;
    }
}
