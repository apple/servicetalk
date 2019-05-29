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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;

import java.util.function.Predicate;

import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;

final class StreamingConnectionFactory {

    private static final Predicate<Object> LAST_CHUNK_PREDICATE = p -> p instanceof HttpHeaders;

    private StreamingConnectionFactory() {
        // No instances.
    }

    static <ResolvedAddress> Single<? extends NettyConnection<Object, Object>> buildStreaming(
            final HttpExecutionContext executionContext, ResolvedAddress resolvedAddress,
            ReadOnlyHttpClientConfig roConfig) {
        // This state is read only, so safe to keep a copy across Subscribers
        final ReadOnlyTcpClientConfig roTcpClientConfig = roConfig.tcpClientConfig();
        return TcpConnector.connect(null, resolvedAddress, roTcpClientConfig, executionContext)
                .flatMap(channel -> {
                    CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
                    return DefaultNettyConnection.initChannel(channel, executionContext.bufferAllocator(),
                            executionContext.executor(), new TerminalPredicate<>(LAST_CHUNK_PREDICATE), closeHandler,
                            roTcpClientConfig.flushStrategy(), new TcpClientChannelInitializer(
                                    roConfig.tcpClientConfig()).andThen(new HttpClientChannelInitializer(roConfig,
                                    closeHandler)), executionContext.executionStrategy());
                });
    }
}
