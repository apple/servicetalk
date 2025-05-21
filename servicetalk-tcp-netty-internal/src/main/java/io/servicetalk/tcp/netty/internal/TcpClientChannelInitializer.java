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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.netty.internal.SslClientChannelInitializer;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import static io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer.initWireLogger;
import static io.servicetalk.transport.netty.internal.ExecutionContextUtils.channelExecutionContext;

/**
 * {@link ChannelInitializer} for TCP client.
 */
public class TcpClientChannelInitializer implements ChannelInitializer {    // FIXME: 0.43 - make this class final

    private final ChannelInitializer delegate;

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     * @param observer {@link ConnectionObserver} to report network events.
     * @deprecated Use
     * {@link #TcpClientChannelInitializer(ReadOnlyTcpClientConfig, ConnectionObserver, ExecutionContext, boolean)}
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public TcpClientChannelInitializer(final ReadOnlyTcpClientConfig config,
                                       final ConnectionObserver observer) {
        this(config, observer, false);
    }

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param deferSslHandler {@code true} to wrap the {@link SslHandler} in a {@link DeferSslHandler}.
     * @deprecated Use
     * {@link #TcpClientChannelInitializer(ReadOnlyTcpClientConfig, ConnectionObserver, ExecutionContext, boolean)}
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    @SuppressWarnings("DataFlowIssue")
    public TcpClientChannelInitializer(final ReadOnlyTcpClientConfig config,
                                       final ConnectionObserver observer,
                                       final boolean deferSslHandler) {
        this(config, observer, null, deferSslHandler);
    }

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param executionContext {@link ExecutionContext} to use for {@link ConnectionInfo} reporting.
     * @param deferSslHandler {@code true} to wrap the {@link SslHandler} in a {@link DeferSslHandler}.
     */
    @SuppressWarnings("ConstantValue")
    public TcpClientChannelInitializer(final ReadOnlyTcpClientConfig config,
                                       final ConnectionObserver observer,
                                       final ExecutionContext<?> executionContext,
                                       final boolean deferSslHandler) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();

        delegate = delegate.andThen(new TransportConfigInitializer(config.transportConfig()));

        final ClientSslConfig sslConfig = config.sslConfig();
        if (observer != NoopConnectionObserver.INSTANCE) {
            delegate = delegate.andThen(new ConnectionObserverInitializer(observer,
                    channel -> new EarlyConnectionContext(channel,
                            // ExecutionContext can be null if users used deprecated ctor
                            executionContext == null ? null : channelExecutionContext(channel, executionContext),
                            sslConfig, config.idleTimeoutMs()), true, deferSslHandler ? null : sslConfig));
        }

        if (config.idleTimeoutMs() > 0L) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.idleTimeoutMs()));
        }

        if (sslConfig != null) {
            final SslContext sslContext = config.sslContext();
            assert sslContext != null;
            delegate = delegate.andThen(new SslClientChannelInitializer(sslContext, sslConfig, deferSslHandler));
        }

        this.delegate = initWireLogger(delegate, config.wireLoggerConfig());
    }

    @Override
    public void init(final Channel channel) {
        delegate.init(channel);
    }
}
