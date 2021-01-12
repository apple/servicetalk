/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.Channel;

import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HeaderUtils.LAST_CHUNK_PREDICATE;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;

final class StreamingConnectionFactory {
    private StreamingConnectionFactory() {
        // No instances.
    }

    static <ResolvedAddress> Single<? extends NettyConnection<Object, Object>> buildStreaming(
            final HttpExecutionContext executionContext, final ResolvedAddress resolvedAddress,
            final ReadOnlyHttpClientConfig roConfig, final TransportObserver observer) {
        // We disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        return TcpConnector.connect(null, resolvedAddress, roConfig.tcpConfig(), false, executionContext,
                (channel, connectionObserver) -> createConnection(channel, executionContext, roConfig,
                        new TcpClientChannelInitializer(roConfig.tcpConfig(), connectionObserver, roConfig.hasProxy()),
                        connectionObserver),
                observer);
    }

    static Single<? extends DefaultNettyConnection<Object, Object>> createConnection(final Channel channel,
            final HttpExecutionContext executionContext, final ReadOnlyHttpClientConfig config,
            final ChannelInitializer initializer, final ConnectionObserver connectionObserver) {
        final CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        assert config.h1Config() != null;
        return showPipeline(DefaultNettyConnection.initChannel(channel, executionContext.bufferAllocator(),
                executionContext.executor(), LAST_CHUNK_PREDICATE, closeHandler, config.tcpConfig().flushStrategy(),
                config.tcpConfig().idleTimeoutMs(), initializer.andThen(new HttpClientChannelInitializer(
                        getByteBufAllocator(executionContext.bufferAllocator()), config.h1Config(), closeHandler)),
                executionContext.executionStrategy(), HTTP_1_1, connectionObserver, true), HTTP_1_1, channel);
    }
}
