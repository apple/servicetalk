/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.net.SocketOption;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.transport.netty.internal.SocketOptionUtils.getOption;

final class EarlyConnectionContext extends NettyChannelListenableAsyncCloseable implements ConnectionContext {

    private static final Protocol TCP_PROTOCOL = () -> "TCP";

    private final Channel channel;
    private final ExecutionContext<?> executionContext;
    @Nullable
    private final SslConfig sslConfig;
    private final long idleTimeoutMs;

    EarlyConnectionContext(final Channel channel,
                           final ExecutionContext<?> executionContext,
                           @Nullable final SslConfig sslConfig,
                           final long idleTimeoutMs) {
        super(channel, executionContext.executor());
        this.channel = channel;
        this.executionContext = executionContext;
        this.sslConfig = sslConfig;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    @Override
    public String connectionId() {
        return "0x" + channel.id().asShortText();
    }

    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public ExecutionContext<?> executionContext() {
        return executionContext;
    }

    @Nullable
    @Override
    public SslConfig sslConfig() {
        return sslConfig;
    }

    @Nullable
    @Override
    public SSLSession sslSession() {
        return null;    // Expected to always be null at this point for EarlyConnectionAcceptor
    }

    @Nullable
    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return getOption(option, channel.config(), idleTimeoutMs);
    }

    @Override
    public Protocol protocol() {
        return TCP_PROTOCOL;
    }

    @Override
    public String toString() {
        return channel.toString();
    }
}
