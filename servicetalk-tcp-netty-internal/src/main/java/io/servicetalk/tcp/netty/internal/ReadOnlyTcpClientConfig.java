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

import io.servicetalk.transport.netty.internal.ReadOnlyClientSecurityConfig;

import io.netty.handler.ssl.SslContext;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SslContextFactory.forClient;

/**
 Read only view of {@link TcpClientConfig}.
 */
public final class ReadOnlyTcpClientConfig
        extends AbstractReadOnlyTcpConfig<ReadOnlyClientSecurityConfig, ReadOnlyTcpClientConfig> {
    @Nullable
    private final SslContext sslContext;
    @Nullable
    private final ReadOnlyClientSecurityConfig sslConfig;

    /**
     * Copy constructor.
     *
     * @param from Source to copy from.
     */
    ReadOnlyTcpClientConfig(final TcpClientConfig from, final List<String> supportedAlpnProtocols) {
        super(from, supportedAlpnProtocols.isEmpty() ? null : supportedAlpnProtocols.get(0));
        sslConfig = from.securityConfig();
        sslContext = sslConfig != null ? forClient(sslConfig, supportedAlpnProtocols) : null;
    }

    @Nullable
    @Override
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Get the {@link ReadOnlyClientSecurityConfig}.
     *
     * @return the {@link ReadOnlyClientSecurityConfig}.
     */
    @Nullable
    public ReadOnlyClientSecurityConfig sslConfig() {
        return sslConfig;
    }
}
