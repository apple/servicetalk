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

import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer;
import io.servicetalk.transport.netty.internal.IdleTimeoutInitializer;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.netty.internal.SniServerChannelInitializer;
import io.servicetalk.transport.netty.internal.SslServerChannelInitializer;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.channel.Channel;

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
     * @param observer {@link ConnectionObserver} to report network events.
     */
    public TcpServerChannelInitializer(final ReadOnlyTcpServerConfig config,
                                       final ConnectionObserver observer) {
        ChannelInitializer delegate = ChannelInitializer.defaultInitializer();

        if (observer != NoopConnectionObserver.INSTANCE) {
            delegate = delegate.andThen(
                    new ConnectionObserverInitializer(observer, config.sslContext() != null, false));
        }

        if (config.idleTimeoutMs() > 0L) {
            delegate = delegate.andThen(new IdleTimeoutInitializer(config.idleTimeoutMs()));
        }

        if (config.sniMapping() != null) {
            delegate = delegate.andThen(new SniServerChannelInitializer(config.sniMapping()));
        } else if (config.sslContext() != null) {
            delegate = delegate.andThen(new SslServerChannelInitializer(config.sslContext()));
        }

        this.delegate = initWireLogger(delegate, config.wireLoggerConfig());
    }

    @Override
    public void init(final Channel channel) {
        delegate.init(channel);
    }

    static ChannelInitializer initWireLogger(ChannelInitializer delegate,
                                             @Nullable UserDataLoggerConfig wireLoggerConfig) {
        if (wireLoggerConfig == null) {
            return delegate;
        }
        return delegate.andThen(new WireLoggingInitializer(wireLoggerConfig.loggerName(),
                wireLoggerConfig.logLevel(), wireLoggerConfig.logUserData()));
    }
}
