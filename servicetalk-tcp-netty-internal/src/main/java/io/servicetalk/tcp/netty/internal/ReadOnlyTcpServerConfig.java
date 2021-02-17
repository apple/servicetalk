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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.Mapping;

import java.net.SocketOption;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

import static io.servicetalk.transport.api.ServiceTalkSocketOptions.SO_BACKLOG;
import static io.servicetalk.transport.api.TransportObservers.asSafeObserver;
import static io.servicetalk.transport.netty.internal.SslContextFactory.forServer;

/**
 * Read only view of {@link TcpServerConfig}.
 */
public final class ReadOnlyTcpServerConfig extends AbstractReadOnlyTcpConfig<ServerSslConfig> {
    @SuppressWarnings("rawtypes")
    private final Map<ChannelOption, Object> listenOptions;
    private final TransportObserver transportObserver;
    @Nullable
    private final SslContext sslContext;
    @Nullable
    private final Mapping<String, SslContext> sniMapping;
    private final boolean alpnConfigured;

    ReadOnlyTcpServerConfig(final TcpServerConfig from) {
        super(from);
        listenOptions = nonNullOptions(from.listenOptions());
        final TransportObserver transportObserver = from.transportObserver();
        this.transportObserver = transportObserver == NoopTransportObserver.INSTANCE ? transportObserver :
                asSafeObserver(transportObserver);
        final ServerSslConfig sslConfig = from.sslConfig();
        final Map<String, ServerSslConfig> sniMap = from.sniConfig();
        if (sniMap != null) {
            if (sslConfig == null) {
                throw new IllegalStateException("No default security config defined but found SNI config mappings");
            }
            sslContext = forServer(sslConfig);
            boolean foundAlpn = !sslContext.applicationProtocolNegotiator().protocols().isEmpty();
            DomainWildcardMappingBuilder<SslContext> mappingBuilder = new DomainWildcardMappingBuilder<>(sslContext);
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
    }

    /**
     * Returns {@code true} if the <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a> is
     * configured on either default or any of the SNI configurations.
     *
     * @return {@code true} if the <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a> is
     * configured on either default or any of the SNI configurations.
     */
    public boolean isAlpnConfigured() {
        return alpnConfigured;
    }

    /**
     * Returns the {@link TransportObserver} if any for all channels.
     *
     * @return the {@link TransportObserver} if any
     */
    public TransportObserver transportObserver() {
        return transportObserver;
    }

    @Nullable
    @Override
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Gets the {@link Mapping} for SNI.
     *
     * @return the {@link Mapping} for SNI, {@code null} if SNI isn't enabled.
     */
    @Nullable
    public Mapping<String, SslContext> sniMapping() {
        return sniMapping;
    }

    /**
     * Returns the {@link SocketOption}s that are applied to the server socket channel which listens/accepts socket
     * channels.
     *
     * @return Unmodifiable map of options
     */
    @SuppressWarnings("rawtypes")
    public Map<ChannelOption, Object> listenOptions() {
        return listenOptions;
    }

    /**
     * Returns the maximum queue length for incoming connection indications (a request to connect).
     * @deprecated Use {@link #listenOptions()} with key {@link ServiceTalkSocketOptions#SO_BACKLOG}.
     * @return backlog
     */
    @Deprecated
    public int backlog() {
        final Integer i = (Integer) listenOptions.get(ChannelOption.SO_BACKLOG);
        if (i == null) {
            throw new IllegalStateException(SO_BACKLOG + " has not been set");
        }
        return i;
    }
}
