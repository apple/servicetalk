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

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.SslClientChannelInitializer;
import io.servicetalk.transport.netty.internal.TransportObserverInitializer;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import javax.annotation.Nullable;

/**
 * {@link ChannelInitializer} for TCP client.
 */
public class TcpClientChannelInitializer implements ChannelInitializer {

    private final ChannelInitializer delegate;

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     * @param observer {@link ConnectionObserver} to report network events.
     */
    public TcpClientChannelInitializer(final ReadOnlyTcpClientConfig config,
                                       @Nullable final ConnectionObserver observer) {
        this(config, observer, false);
    }

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     * @param observer {@link ConnectionObserver} to report network events.
     * @param deferSslHandler {@code true} to wrap the {@link SslHandler} in a {@link DeferSslHandler}.
     */
    public TcpClientChannelInitializer(final ReadOnlyTcpClientConfig config,
                                       @Nullable final ConnectionObserver observer,
                                       final boolean deferSslHandler) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();

        final SslContext sslContext = config.sslContext();
        if (observer != null) {
            delegate = delegate.andThen(new TransportObserverInitializer(observer,
                    sslContext != null && !deferSslHandler));
        }

        if (config.idleTimeoutMs() != null) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.idleTimeoutMs()));
        }

        if (sslContext != null) {
            delegate = delegate.andThen(new SslClientChannelInitializer(sslContext,
                    config.sslHostnameVerificationAlgorithm(), config.sslHostnameVerificationHost(),
                    config.sslHostnameVerificationPort(), deferSslHandler, observer != null));
        }

        final WireLoggingInitializer wireLoggingInitializer = config.wireLoggingInitializer();
        if (wireLoggingInitializer != null) {
            delegate = delegate.andThen(wireLoggingInitializer);
        }
        this.delegate = delegate;
    }

    @Override
    public void init(final Channel channel) {
        delegate.init(channel);
    }
}
