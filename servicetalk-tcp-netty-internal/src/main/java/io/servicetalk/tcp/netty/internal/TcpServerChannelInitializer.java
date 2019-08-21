/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.PooledRecvByteBufAllocatorInitializer;
import io.servicetalk.transport.netty.internal.SslServerChannelInitializer;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;

/**
 * {@link ChannelInitializer} for TCP.
 */
public class TcpServerChannelInitializer implements ChannelInitializer {

    private final ChannelInitializer delegate;

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     */
    public TcpServerChannelInitializer(ReadOnlyTcpServerConfig config) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();
        delegate = delegate.andThen(new PooledRecvByteBufAllocatorInitializer());
        if (config.idleTimeoutMs() > 0) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.idleTimeoutMs()));
        }
        if (config.isSniEnabled()) {
            delegate = delegate.andThen(new SslServerChannelInitializer(config.domainNameMapping()));
        } else {
            final SslContext sslContext = config.sslContext();
            if (sslContext != null) {
                delegate = delegate.andThen(new SslServerChannelInitializer(sslContext));
            }
        }
        if (config.wireLoggingInitializer() != null) {
            delegate = delegate.andThen(config.wireLoggingInitializer());
        }
        this.delegate = delegate;
    }

    @Override
    public ConnectionContext init(Channel channel, ConnectionContext context) {
        return delegate.init(channel, context);
    }
}
