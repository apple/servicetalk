/*
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpServerInitializer;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AbstractContextFilterAwareChannelReadHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.ConnectionHolderChannelHandler;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;

import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.http.netty.NettyHttpServerConnection.newConnection;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;

final class NettyHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Predicate<Object> LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE =
            p -> p instanceof HttpHeaders;

    private NettyHttpServer() {
        // No instances
    }

    static Single<ServerContext> bind(final ExecutionContext executionContext, final ReadOnlyHttpServerConfig config,
                                      final SocketAddress address, final ConnectionAcceptor connectionAcceptor,
                                      final StreamingHttpService service) {
        final TcpServerInitializer initializer = new TcpServerInitializer(executionContext, config.tcpConfig());

        final ChannelInitializer channelInitializer = new TcpServerChannelInitializer(config.tcpConfig(),
                connectionAcceptor).andThen(getChannelInitializer(config, service));

        // The ServerContext returned by TcpServerInitializer takes care of closing the connectionAcceptor.
        return initializer.start(address, connectionAcceptor, channelInitializer, false, true)
                .map((ServerContext delegate) -> {
                    LOGGER.debug("Started HTTP server for address {}.", delegate.listenAddress());
                    return new NettyHttpServerContext(delegate, service);
                });
    }

    private static ChannelInitializer getChannelInitializer(final ReadOnlyHttpServerConfig config,
                                                            final StreamingHttpService service) {
        return (channel, context) -> {
            final CloseHandler closeHandler = forPipelinedRequestResponse(false);
            Queue<HttpRequestMethod> methodQueue = new ArrayDeque<>(2);
            channel.pipeline().addLast(new HttpRequestDecoder(methodQueue, config.headersFactory(),
                    config.maxInitialLineLength(), config.maxHeaderSize(), closeHandler));
            channel.pipeline().addLast(new HttpResponseEncoder(methodQueue, config.headersEncodedSizeEstimate(),
                    config.trailersEncodedSizeEstimate(), closeHandler));
            channel.pipeline().addLast(new HttpChannelReadHandler(closeHandler, context, service,
                    config.tcpConfig().flushStrategy(), config.headersFactory()));
            return context;
        };
    }

    private static final class NettyHttpServerContext implements ServerContext {

        private final ServerContext delegate;
        private final ListenableAsyncCloseable asyncCloseable;

        NettyHttpServerContext(final ServerContext delegate, final StreamingHttpService service) {
            this.delegate = delegate;
            asyncCloseable = toListenableAsyncCloseable(newCompositeCloseable().appendAll(service, delegate));
        }

        @Override
        public SocketAddress listenAddress() {
            return delegate.listenAddress();
        }

        @Override
        public ExecutionContext executionContext() {
            return delegate.executionContext();
        }

        @Override
        public Completable closeAsync() {
            return asyncCloseable.closeAsync()
                    .doFinally(() -> LOGGER.debug("Stopped HTTP server for address {}.", listenAddress()));
        }

        @Override
        public Completable closeAsyncGracefully() {
            return asyncCloseable.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return asyncCloseable.onClose();
        }
    }

    private static final class HttpChannelReadHandler extends AbstractContextFilterAwareChannelReadHandler<Object>
            implements ConnectionHolderChannelHandler<Object, Object> {
        private final CloseHandler closeHandler;
        private final ConnectionContext context;
        private final StreamingHttpService service;
        private final FlushStrategy flushStrategy;
        private final HttpHeadersFactory headersFactory;
        @Nullable
        private NettyHttpServerConnection connection;

        HttpChannelReadHandler(final CloseHandler closeHandler, final ConnectionContext context,
                               final StreamingHttpService service, final FlushStrategy flushStrategy,
                               final HttpHeadersFactory headersFactory) {
            super(LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE, closeHandler);
            this.closeHandler = closeHandler;
            this.context = context;
            this.service = service;
            this.flushStrategy = flushStrategy;
            this.headersFactory = headersFactory;
        }

        @Override
        protected void onPublisherCreation(final ChannelHandlerContext channelHandlerContext,
                                           final Publisher<Object> requestObjectPublisher) {
            connection = newConnection(channelHandlerContext.channel(), requestObjectPublisher,
                    new TerminalPredicate<>(LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE), closeHandler, context, service,
                    flushStrategy, headersFactory);
        }

        @Override
        protected void onContextFilterSuccess(final ChannelHandlerContext ctx) {
            assert connection != null;
            connection.process().subscribe();
        }

        @Override
        public NettyConnection<Object, Object> connection() {
            return connection == null ? null : connection.connection();
        }
    }
}
