/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.AlpnChannelSingle.NoopChannelInitializer;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.netty.AlpnIds.HTTP_1_1;
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;

final class DeferredServerChannelBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredServerChannelBinder.class);

    private DeferredServerChannelBinder() {
        // No instances
    }

    static Single<ServerContext> bind(final HttpExecutionContext executionContext,
                                      final ReadOnlyHttpServerConfig config,
                                      final SocketAddress listenAddress,
                                      @Nullable final ConnectionAcceptor connectionAcceptor,
                                      final StreamingHttpService service,
                                      final boolean drainRequestPayloadBody,
                                      final boolean sniOnly) {
        final ReadOnlyTcpServerConfig tcpConfig = config.tcpConfig();
        assert tcpConfig.sslContext() != null;

        final BiFunction<Channel, ConnectionObserver, Single<NettyConnectionContext>> channelInit = sniOnly ?
                (channel, connectionObserver) -> sniInitChannel(listenAddress, channel, config, executionContext,
                        service, drainRequestPayloadBody, connectionObserver) :
                (channel, connectionObserver) -> alpnInitChannel(listenAddress, channel, config, executionContext,
                        service, drainRequestPayloadBody, connectionObserver);

        // We disable auto read by default so we can handle stuff in the ConnectionFilter before we accept any content.
        // In case ALPN negotiates h2, h2 connection MUST enable auto read for its Channel.
        return TcpServerBinder.bind(listenAddress, tcpConfig, false, executionContext, connectionAcceptor, channelInit,
                serverConnection -> {
                    // Start processing requests on http/1.1 connection:
                    if (serverConnection instanceof NettyHttpServerConnection) {
                        ((NettyHttpServerConnection) serverConnection).process(true);
                    }
                    // Nothing to do otherwise as h2 uses auto read on the parent channel
                })
                .map(delegate -> {
                    LOGGER.debug("Started HTTP server with ALPN for address {}", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServer.NettyHttpServerContext(delegate, service);
                });
    }

    private static Single<NettyConnectionContext> alpnInitChannel(final SocketAddress listenAddress,
                                                                  final Channel channel,
                                                                  final ReadOnlyHttpServerConfig config,
                                                                  final HttpExecutionContext httpExecutionContext,
                                                                  final StreamingHttpService service,
                                                                  final boolean drainRequestPayloadBody,
                                                                  final ConnectionObserver observer) {
        return new AlpnChannelSingle(channel,
                new TcpServerChannelInitializer(config.tcpConfig(), observer), true).flatMap(protocol -> {
            switch (protocol) {
                case HTTP_1_1:
                    return NettyHttpServer.initChannel(channel, httpExecutionContext, config,
                            NoopChannelInitializer.INSTANCE, service, drainRequestPayloadBody, observer);
                case HTTP_2:
                    return H2ServerParentConnectionContext.initChannel(listenAddress, channel, httpExecutionContext,
                            config, NoopChannelInitializer.INSTANCE, service, drainRequestPayloadBody, observer);
                default:
                    return failed(new IllegalStateException("Unknown ALPN protocol negotiated: " + protocol));
            }
        });
    }

    private static Single<NettyConnectionContext> sniInitChannel(final SocketAddress listenAddress,
                                                                 final Channel channel,
                                                                 final ReadOnlyHttpServerConfig config,
                                                                 final HttpExecutionContext httpExecutionContext,
                                                                 final StreamingHttpService service,
                                                                 final boolean drainRequestPayloadBody,
                                                                 final ConnectionObserver observer) {
        return new SniCompleteChannelSingle(channel,
                new TcpServerChannelInitializer(config.tcpConfig(), observer)).flatMap(sniEvt -> {
            Throwable failureCause = sniEvt.cause();
            if (failureCause != null) {
                return failed(failureCause);
            }

            if (config.h2Config() != null) {
                return H2ServerParentConnectionContext.initChannel(listenAddress, channel, httpExecutionContext, config,
                        NoopChannelInitializer.INSTANCE, service, drainRequestPayloadBody, observer);
            }
            if (config.h1Config() != null) {
                return NettyHttpServer.initChannel(channel, httpExecutionContext, config,
                        NoopChannelInitializer.INSTANCE, service, drainRequestPayloadBody, observer);
            }
            return failed(new IllegalStateException(
                    "SSL handshake completed, but no protocols to initialize. Consider using ALPN to explicitly " +
                            "negotiate the protocol and/or configure protocols on the client/server builder."));
        });
    }
}
