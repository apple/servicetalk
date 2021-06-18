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

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SocketOptionUtils.addOption;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for TCP based servers.
 */
public final class TcpServerConfig extends AbstractTcpConfig<ServerSslConfig> {

    @Nullable
    @SuppressWarnings("rawtypes")
    private Map<ChannelOption, Object> listenOptions;
    private TransportObserver transportObserver = NoopTransportObserver.INSTANCE;
    @Nullable
    private Map<String, ServerSslConfig> sniConfig;

    TransportObserver transportObserver() {
        return transportObserver;
    }

    @Nullable
    public Map<String, ServerSslConfig> sniConfig() {
        return sniConfig;
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    Map<ChannelOption, Object> listenOptions() {
        return listenOptions;
    }

    /**
     * Sets a {@link TransportObserver} that provides visibility into transport events.
     *
     * @param transportObserver A {@link TransportObserver} that provides visibility into transport events.
     */
    public void transportObserver(final TransportObserver transportObserver) {
        this.transportObserver = requireNonNull(transportObserver);
    }

    /**
     * Add SSL/TLS and SNI related config.
     *
     * @param defaultSslConfig the default {@link ServerSslConfig} used when no SNI match is found.
     * @param sniConfig client SNI hostname values are matched against keys in this {@link Map} and if a match is
     * found the corresponding {@link ServerSslConfig} is used.
     * @return {@code this}
     */
    public TcpServerConfig sslConfig(ServerSslConfig defaultSslConfig, Map<String, ServerSslConfig> sniConfig) {
        sslConfig(defaultSslConfig);
        this.sniConfig = requireNonNull(sniConfig);
        return this;
    }

    /**
     * Adds a {@link SocketOption} that is applied to the server socket channel which listens/accepts socket channels.
     *
     * @param <T> the type of the value
     * @param option the option to apply
     * @param value the value
     * @throws IllegalArgumentException if the {@link SocketOption} is not supported
     * @see StandardSocketOptions
     * @see ServiceTalkSocketOptions
     */
    public <T> void listenSocketOption(final SocketOption<T> option, T value) {
        if (listenOptions == null) {
            listenOptions = new HashMap<>();
        }
        addOption(listenOptions, option, value);
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection may time out.
     * @deprecated Use {@link #listenSocketOption(SocketOption, Object)} with
     * {@link ServiceTalkSocketOptions#SO_BACKLOG}.
     * @param backlog the backlog to use when accepting connections
     * @return {@code this}
     */
    @Deprecated
    public TcpServerConfig backlog(final int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog must be >= 0");
        }
        listenSocketOption(ServiceTalkSocketOptions.SO_BACKLOG, backlog);
        return this;
    }

    /**
     * Create a read only view of this object.
     * @return a read only view of this object.
     */
    public ReadOnlyTcpServerConfig asReadOnly() {
        return new ReadOnlyTcpServerConfig(this);
    }
}
