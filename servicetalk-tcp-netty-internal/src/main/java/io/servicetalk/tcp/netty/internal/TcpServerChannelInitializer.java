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

import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.SslServerChannelInitializer;
import io.servicetalk.transport.netty.internal.TransportObserverInitializer;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.channel.Channel;

/**
 * {@link ChannelInitializer} for TCP.
 */
public class TcpServerChannelInitializer implements ChannelInitializer {

    private static final ChannelInitializer SERVER_TRANSPORT_OBSERVER_INITIALIZER = new TransportObserverInitializer();

    private final ChannelInitializer delegate;

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     */
    public TcpServerChannelInitializer(final ReadOnlyTcpServerConfig config) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();

        if (config.transportObserver() != null) {
            delegate = delegate.andThen(SERVER_TRANSPORT_OBSERVER_INITIALIZER);
        }

        if (config.idleTimeoutMs() != null) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.idleTimeoutMs()));
        }

        if (config.domainNameMapping() != null) {
            delegate = delegate.andThen(new SslServerChannelInitializer(config.domainNameMapping()));
        } else if (config.sslContext() != null) {
            delegate = delegate.andThen(new SslServerChannelInitializer(config.sslContext()));
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
