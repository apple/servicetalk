/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_1_1;
import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_2;

final class AlpnServerContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlpnServerContext.class);

    private AlpnServerContext() {
        // No instances
    }

    static Single<ServerContext> bind(final HttpExecutionContext executionContext,
                                      final ReadOnlyHttpServerConfig config,
                                      final SocketAddress listenAddress,
                                      @Nullable final ConnectionAcceptor connectionAcceptor,
                                      final StreamingHttpService service,
                                      final boolean drainRequestPayloadBody) {

        final ReadOnlyTcpServerConfig tcpConfig = config.tcpConfig();
        assert tcpConfig.isSniEnabled() || tcpConfig.sslContext() != null;

        return TcpServerBinder.bind(listenAddress, tcpConfig, executionContext, connectionAcceptor,
                channel -> initChannel(listenAddress, channel, config, executionContext, service,
                        drainRequestPayloadBody),
                serverConnection -> {
                    // Start processing requests on http/1.1 connection:
                    if (serverConnection instanceof NettyHttpServerConnection) {
                        NettyHttpServer.startProcessing((NettyHttpServerConnection) serverConnection, true);
                    }
                    // Nothing to do otherwise as h2 uses auto read on the parent channel
                })
                .map(delegate -> {
                    LOGGER.debug("Started HTTP server for address {}.", delegate.listenAddress());
                    // The ServerContext returned by TcpServerBinder takes care of closing the connectionAcceptor.
                    return new NettyHttpServer.NettyHttpServerContext(delegate, service);
                });
    }

    private static Single<NettyConnectionContext> initChannel(final SocketAddress listenAddress,
                                                              final Channel channel,
                                                              final ReadOnlyHttpServerConfig config,
                                                              final HttpExecutionContext httpExecutionContext,
                                                              final StreamingHttpService service,
                                                              final boolean drainRequestPayloadBody) {
        return new AlpnChannelSingle(channel,
                new TcpServerChannelInitializer(config.tcpConfig()), true).flatMap(protocol -> {
            switch (protocol) {
                case HTTP_1_1:
                    return NettyHttpServer.initChannel(channel, httpExecutionContext, config,
                            NoopChannelInitializer.INSTANCE, service, drainRequestPayloadBody);
                case HTTP_2:
                    return H2ServerParentConnectionContext.initChannel(listenAddress, channel, httpExecutionContext,
                            config, NoopChannelInitializer.INSTANCE, service, drainRequestPayloadBody);
                default:
                    return failed(new IllegalStateException("Unknown ALPN protocol negotiated: " + protocol));
            }
        });
    }
}
