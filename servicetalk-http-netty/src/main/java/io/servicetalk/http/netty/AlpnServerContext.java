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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
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
                        ((NettyHttpServerConnection) serverConnection).process(true).subscribe();
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
        return new SubscribableSingle<AlpnConnectionContext>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super AlpnConnectionContext> subscriber) {
                final AlpnConnectionContext context;
                try {
                    context = new AlpnConnectionContext(channel, httpExecutionContext);
                    new TcpServerChannelInitializer(config.tcpConfig()).init(channel, context);
                } catch (Throwable cause) {
                    channel.close();
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSubscribe(channel::close);
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                channel.pipeline().addLast(new AlpnServerHandler(context, subscriber));
            }
        }.flatMap(alpnContext -> {
            final String protocol = alpnContext.protocol();
            assert protocol != null;
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

    private static final class AlpnConnectionContext extends NettyChannelListenableAsyncCloseable
            implements ConnectionContext {

        private final ExecutionContext executionContext;
        @Nullable
        private String protocol;

        AlpnConnectionContext(final Channel channel, final ExecutionContext executionContext) {
            super(channel, executionContext.executor());
            this.executionContext = executionContext;
        }

        @Override
        public SocketAddress localAddress() {
            return channel().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return channel().remoteAddress();
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Nullable
        String protocol() {
            return protocol;
        }
    }

    private static final class AlpnServerHandler extends ApplicationProtocolNegotiationHandler {

        private final AlpnConnectionContext connectionContext;
        @Nullable
        private SingleSource.Subscriber<? super AlpnConnectionContext> subscriber;

        AlpnServerHandler(final AlpnConnectionContext connectionContext,
                          final SingleSource.Subscriber<? super AlpnConnectionContext> subscriber) {
            super(HTTP_1_1);
            this.connectionContext = connectionContext;
            this.subscriber = subscriber;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            // Force a read to get the SSL handshake started. We initialize pipeline before SslHandshakeCompletionEvent
            // will complete, therefore, no data will be propagated before we finish initialization.
            ctx.read();
        }

        @Override
        protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
            LOGGER.debug("ALPN negotiated {} protocol", protocol);
            connectionContext.protocol = protocol;

            assert subscriber != null;
            final SingleSource.Subscriber<? super AlpnConnectionContext> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(connectionContext);
        }

        @Override
        protected void handshakeFailure(final ChannelHandlerContext ctx, final Throwable cause) {
            failSubscriber(ctx, cause);
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            failSubscriber(ctx, cause);
        }

        private void failSubscriber(final ChannelHandlerContext ctx, final Throwable cause) {
            if (subscriber != null) {
                ctx.close();
                final SingleSource.Subscriber<? super AlpnConnectionContext> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
            }
        }
    }

    private static final class NoopChannelInitializer implements ChannelInitializer {

        static final ChannelInitializer INSTANCE = new NoopChannelInitializer();

        private NoopChannelInitializer() {
            // Singleton
        }

        @Override
        public ConnectionContext init(final Channel channel, final ConnectionContext context) {
            return context;
        }

        @Override
        public ChannelInitializer andThen(final ChannelInitializer after) {
            return after;
        }
    }
}
