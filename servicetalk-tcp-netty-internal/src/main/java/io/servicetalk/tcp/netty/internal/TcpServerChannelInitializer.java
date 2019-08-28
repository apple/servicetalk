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

import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.PooledRecvByteBufAllocatorUtils;
import io.servicetalk.transport.netty.internal.SslServerChannelInitializer;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;

import javax.annotation.Nullable;

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
    public TcpServerChannelInitializer(final ReadOnlyTcpServerConfig config) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();
        if (config.idleTimeoutMs() > 0) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.idleTimeoutMs()));
        }

        delegate = delegate.andThen(PooledRecvByteBufAllocatorUtils.wrap(sslInitializer(config)));

        final WireLoggingInitializer wireLoggingInitializer = config.wireLoggingInitializer();
        if (wireLoggingInitializer != null) {
            delegate = delegate.andThen(wireLoggingInitializer);
        }
        this.delegate = delegate;
    }

    @Nullable
    private static ChannelInitializer sslInitializer(final ReadOnlyTcpServerConfig config) {
        if (config.isSniEnabled()) {
            return new SslServerChannelInitializer(config.domainNameMapping());
        } else {
            final SslContext sslContext = config.sslContext();
            return sslContext != null ? new SslServerChannelInitializer(sslContext) : null;
        }
    }

    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext context) {
        return delegate.init(channel, context);
    }
}
