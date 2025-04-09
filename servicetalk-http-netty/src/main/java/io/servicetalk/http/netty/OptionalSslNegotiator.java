/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Negotiates SSL/non-SSL connections when SSL is enabled and {@code acceptInsecureConnections} is true.
 */
final class OptionalSslNegotiator {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptionalSslNegotiator.class);

    private OptionalSslNegotiator() {
        // No instances
    }

    static Single<HttpServerContext> bind(final HttpExecutionContext executionContext,
                                          final ReadOnlyHttpServerConfig roConfig,
                                          final ReadOnlyHttpServerConfig roConfigWithoutSsl,
                                          final SocketAddress listenAddress,
                                          @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
                                          final StreamingHttpService service,
                                          @Nullable final EarlyConnectionAcceptor earlyConnectionAcceptor,
                                          @Nullable final LateConnectionAcceptor lateConnectionAcceptor) {
        final BiFunction<Channel, ConnectionObserver, Single<NettyConnectionContext>> channelInit =
                (channel, connectionObserver) -> new OptionalSslChannelSingle(channel).flatMap(isTls -> {
                    assert channel.eventLoop().inEventLoop();
                    if (isTls) {
                        final ReadOnlyTcpServerConfig roTcpConfig = roConfig.tcpConfig();

                        // Important: This code needs to be kept in-sync with the similar, but not identical
                        // code inside DefaultHttpServerBuilder#doBind
                        if (roTcpConfig.isAlpnConfigured()) {
                            return DeferredServerChannelBinder.alpnInitChannel(listenAddress, channel,
                                    roConfig, executionContext,
                                    service, connectionObserver);
                        } else if (roTcpConfig.sniMapping() != null) {
                            return DeferredServerChannelBinder.sniInitChannel(listenAddress, channel,
                                    roConfig, executionContext,
                                    service, connectionObserver);
                        } else if (roConfig.isH2PriorKnowledge()) {
                            return H2ServerParentConnectionContext.initChannel(listenAddress, channel,
                                    executionContext, roConfig,
                                    new TcpServerChannelInitializer(roTcpConfig, connectionObserver,
                                            executionContext), service,
                                    connectionObserver);
                        } else {
                            return NettyHttpServer.initChannel(channel, executionContext, roConfig,
                                    new TcpServerChannelInitializer(roTcpConfig, connectionObserver,
                                            executionContext), service,
                                    connectionObserver);
                        }
                    } else {
                        if (roConfigWithoutSsl.h2Config() != null) {
                            return H2ServerParentConnectionContext.initChannel(listenAddress, channel,
                                    executionContext, roConfigWithoutSsl,
                                    new TcpServerChannelInitializer(roConfigWithoutSsl.tcpConfig(),
                                            connectionObserver, executionContext), service,
                                    connectionObserver);
                        } else {
                            return NettyHttpServer.initChannel(channel, executionContext, roConfigWithoutSsl,
                                    new TcpServerChannelInitializer(roConfigWithoutSsl.tcpConfig(),
                                            connectionObserver, executionContext), service,
                                    connectionObserver);
                        }
                    }
                });

        final Consumer<NettyConnectionContext> connectionConsumer = serverConnection -> {
            if (serverConnection instanceof NettyHttpServer.NettyHttpServerConnection) {
                ((NettyHttpServer.NettyHttpServerConnection) serverConnection).process(true);
            }
        };

        return TcpServerBinder.bind(listenAddress, roConfig.tcpConfig(), executionContext, connectionAcceptor,
                        channelInit, connectionConsumer, earlyConnectionAcceptor, lateConnectionAcceptor)
                .map(delegate -> {
                    LOGGER.debug("Started HTTP server with Optional TLS for address {}", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServer.NettyHttpServerContext(delegate, service, executionContext);
                });
    }
}
