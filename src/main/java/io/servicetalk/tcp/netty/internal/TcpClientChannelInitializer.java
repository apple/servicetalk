/**
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

import io.netty.channel.Channel;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.SslClientChannelInitializer;

/**
 * {@link ChannelInitializer} for TCP client.
 */
public class TcpClientChannelInitializer implements ChannelInitializer {

    private final ChannelInitializer delegate;

    /**
     * Creates a {@link ChannelInitializer} for the {@code config}.
     *
     * @param config to use for initialization.
     */
    public TcpClientChannelInitializer(ReadOnlyTcpClientConfig config) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();
        if (config.getWireLogger() != null) {
            delegate = delegate.andThen(config.getWireLogger());
        }
        if (config.getIdleTimeoutMs() > 0) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.getIdleTimeoutMs()));
        }
        if (config.getSslContext() != null) {
            delegate = delegate.andThen(new SslClientChannelInitializer(config.getSslContext(), config.getHostnameVerificationAlgorithm()));
        }
        this.delegate = delegate;
    }

    @Override
    public ConnectionContext init(Channel channel, ConnectionContext context) {
        return delegate.init(channel, context);
    }
}
