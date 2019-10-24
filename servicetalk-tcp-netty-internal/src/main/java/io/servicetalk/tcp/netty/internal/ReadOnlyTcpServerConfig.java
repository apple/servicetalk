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

import io.servicetalk.transport.netty.internal.ReadOnlyServerSecurityConfig;

import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainNameMapping;
import io.netty.util.DomainNameMappingBuilder;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SslContextFactory.forServer;

/**
 * Read only view of {@link TcpServerConfig}.
 */
public final class ReadOnlyTcpServerConfig
        extends AbstractReadOnlyTcpConfig<ReadOnlyServerSecurityConfig, ReadOnlyTcpServerConfig> {

    @Nullable
    private final SslContext sslContext;
    @Nullable
    private final DomainNameMapping<SslContext> mappings;
    private final int backlog;

    /**
     * Copy constructor.
     *
     * @param from Source to copy from.
     */
    ReadOnlyTcpServerConfig(final TcpServerConfig from, final List<String> supportedAlpnProtocols) {
        super(from, !supportedAlpnProtocols.isEmpty());
        final ReadOnlyServerSecurityConfig securityConfig = from.securityConfig();
        if (from.sniConfigs() != null) {
            if (securityConfig == null) {
                throw new IllegalStateException("No default security config defined but found SNI config mappings");
            }
            sslContext = forServer(securityConfig, supportedAlpnProtocols);
            final DomainNameMappingBuilder<SslContext> mappingBuilder = new DomainNameMappingBuilder<>(sslContext);
            for (Map.Entry<String, ReadOnlyServerSecurityConfig> sniConfigEntries : from.sniConfigs().entrySet()) {
                mappingBuilder.add(sniConfigEntries.getKey(),
                        forServer(sniConfigEntries.getValue(), supportedAlpnProtocols));
            }
            mappings = mappingBuilder.build();
        } else if (securityConfig != null) {
            sslContext = forServer(securityConfig, supportedAlpnProtocols);
            mappings = null;
        } else {
            sslContext = null;
            mappings = null;
        }
        backlog = from.backlog();
    }

    @Nullable
    @Override
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Gets {@link DomainNameMapping}, if any.
     *
     * @return Configured mapping, {@code null} if none configured
     */
    @Nullable
    public DomainNameMapping<SslContext> domainNameMapping() {
        return mappings;
    }

    /**
     * Returns the maximum queue length for incoming connection indications (a request to connect).
     *
     * @return backlog
     */
    public int backlog() {
        return backlog;
    }
}
