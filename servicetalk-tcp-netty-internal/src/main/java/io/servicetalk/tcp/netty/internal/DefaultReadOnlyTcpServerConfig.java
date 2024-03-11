/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.SslListenMode;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.Mapping;

import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

import static io.servicetalk.transport.api.TransportObservers.asSafeObserver;
import static io.servicetalk.transport.netty.internal.SslContextFactory.forServer;

/**
 * Read only view of {@link TcpServerConfig}.
 */
public final class DefaultReadOnlyTcpServerConfig extends AbstractReadOnlyTcpConfig<ServerSslConfig>
        implements ReadOnlyTcpServerConfig {
    @SuppressWarnings("rawtypes")
    private final Map<ChannelOption, Object> listenOptions;
    private final TransportObserver transportObserver;
    @Nullable
    private final ServerSslConfig sslConfig;
    private final SslListenMode sslListenMode;
    @Nullable
    private final SslContext sslContext;
    @Nullable
    private final Mapping<String, SslContext> sniMapping;
    private final int sniMaxClientHelloLength;
    private final Duration sniClientHelloTimeout;
    private final boolean alpnConfigured;

    DefaultReadOnlyTcpServerConfig(final TcpServerConfig from) {
        super(from);
        listenOptions = nonNullOptions(from.listenOptions());
        final TransportObserver transportObserver = from.transportObserver();
        this.transportObserver = transportObserver == NoopTransportObserver.INSTANCE ? transportObserver :
                asSafeObserver(transportObserver);
        this.sslConfig = from.sslConfig();
        this.sslListenMode = from.sslListenMode();
        final Map<String, ServerSslConfig> sniMap = from.sniConfig();
        if (sniMap != null) {
            if (sslConfig == null) {
                throw new IllegalStateException("No default security config defined but found SNI config mappings");
            }
            sslContext = forServer(sslConfig);
            boolean foundAlpn = !sslContext.applicationProtocolNegotiator().protocols().isEmpty();
            final DomainWildcardMappingBuilder<SslContext> mappingBuilder =
                    new DomainWildcardMappingBuilder<>(sniMap.size(), sslContext);
            for (Entry<String, ServerSslConfig> sniConfigEntry : sniMap.entrySet()) {
                SslContext sniContext = forServer(sniConfigEntry.getValue());
                foundAlpn |= !sniContext.applicationProtocolNegotiator().protocols().isEmpty();
                mappingBuilder.add(sniConfigEntry.getKey(), sniContext);
            }
            sniMapping = mappingBuilder.build();
            alpnConfigured = foundAlpn;
        } else if (sslConfig != null) {
            sslContext = forServer(sslConfig);
            sniMapping = null;
            alpnConfigured = !sslContext.applicationProtocolNegotiator().protocols().isEmpty();
        } else {
            sslContext = null;
            sniMapping = null;
            alpnConfigured = false;
        }
        sniMaxClientHelloLength = from.sniMaxClientHelloLength();
        sniClientHelloTimeout = from.sniClientHelloTimeout();
    }

    @Override
    public boolean isAlpnConfigured() {
        return alpnConfigured;
    }

    @Override
    public TransportObserver transportObserver() {
        return transportObserver;
    }

    @Nullable
    @Override
    public ServerSslConfig sslConfig() {
        return sslConfig;
    }

    @Override
    public SslListenMode sslListenMode() {
        return sslListenMode;
    }

    @Nullable
    @Override
    public SslContext sslContext() {
        return sslContext;
    }

    @Nullable
    @Override
    public Mapping<String, SslContext> sniMapping() {
        return sniMapping;
    }

    @Override
    public int sniMaxClientHelloLength() {
        return sniMaxClientHelloLength;
    }

    @Override
    public Duration sniClientHelloTimeout() {
        return sniClientHelloTimeout;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Map<ChannelOption, Object> listenOptions() {
        return listenOptions;
    }
}
