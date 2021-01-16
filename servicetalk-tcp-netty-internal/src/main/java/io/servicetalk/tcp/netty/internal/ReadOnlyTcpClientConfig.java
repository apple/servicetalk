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
    private final String sslHostnameVerificationAlgorithm;
    @Nullable
    private final String sslHostnameVerificationHost;
    private final int sslHostnameVerificationPort;

    /**
     * Copy constructor.
     *
     * @param from Source to copy from.
     */
    ReadOnlyTcpClientConfig(final TcpClientConfig from, final List<String> supportedAlpnProtocols) {
        super(from, supportedAlpnProtocols.isEmpty() ? null : supportedAlpnProtocols.get(0));
        final ReadOnlyClientSecurityConfig securityConfig = from.securityConfig();
        if (securityConfig != null) {
            sslContext = forClient(securityConfig, supportedAlpnProtocols);
            sslHostnameVerificationAlgorithm = securityConfig.hostnameVerificationAlgorithm();
            sslHostnameVerificationHost = securityConfig.hostnameVerificationHost();
            sslHostnameVerificationPort = securityConfig.hostnameVerificationPort();
        } else {
            sslContext = null;
            sslHostnameVerificationAlgorithm = null;
            sslHostnameVerificationHost = null;
            sslHostnameVerificationPort = -1;
        }
    }

    @Nullable
    @Override
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Returns the hostname verification algorithm, if any.
     *
     * @return hostname verification algorithm, {@code null} if none specified
     */
    @Nullable
    public String sslHostnameVerificationAlgorithm() {
        return sslHostnameVerificationAlgorithm;
    }

    /**
     * Get the non-authoritative name of the host.
     *
     * @return the non-authoritative name of the host
     */
    @Nullable
    public String sslHostnameVerificationHost() {
        return sslHostnameVerificationHost;
    }

    /**
     * Get the non-authoritative port.
     * <p>
     * Only valid if {@link #sslHostnameVerificationHost()} is not {@code null}.
     *
     * @return the non-authoritative port
     */
    public int sslHostnameVerificationPort() {
        return sslHostnameVerificationPort;
    }
}
