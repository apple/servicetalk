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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpServerInitializer;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AbstractChannelReadHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection.TerminalPredicate;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;

final class NettyHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Predicate<Object> LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE =
            p -> p instanceof LastHttpPayloadChunk;

    private NettyHttpServer() {
        // No instances
    }

    static Single<ServerContext> bind(final ReadOnlyHttpServerConfig config, final SocketAddress address,
                                      final ContextFilter contextFilter, final Executor executor,
                                      final HttpService<HttpPayloadChunk, HttpPayloadChunk> service) {
        final TcpServerInitializer initializer = new TcpServerInitializer(config.getTcpConfig());

        final ChannelInitializer channelInitializer = new TcpServerChannelInitializer(config.getTcpConfig())
                .andThen(getChannelInitializer(config, contextFilter, executor, service));

        // The ServerContext returned by TcpServerInitializer takes care of closing the contextFilter.
        return initializer.start(address, contextFilter, channelInitializer, false)
                .map((ServerContext delegate) -> new NettyHttpServerContext(delegate, service, executor));
    }

    private static ChannelInitializer getChannelInitializer(
            final ReadOnlyHttpServerConfig config, final ContextFilter contextFilter, final Executor executor,
            final HttpService<HttpPayloadChunk, HttpPayloadChunk> service) {
        return (channel, context) -> {

            // TODO: Context filtering should be moved somewhere central. Maybe TcpServerInitializer.start?
            final Single<Boolean> filterResultSingle = contextFilter.filter(context);
            filterResultSingle.subscribe(new io.servicetalk.concurrent.Single.Subscriber<Boolean>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    // Don't need to do anything.
                }

                @Override
                public void onSuccess(@Nullable final Boolean result) {
                    if (result != null && result) {
                        // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Accepted connection from {}", context.getRemoteAddress());
                        }
                        handleAcceptedConnection(config, executor, service, channel, context);
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Rejected connection from {}", context.getRemoteAddress());
                        }
                        handleRejectedConnection(context);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    LOGGER.warn("Context filter threw exception.", t);
                    handleRejectedConnection(context);
                }
            });

            return context;
        };
    }

    private static void handleRejectedConnection(final ConnectionContext context) {
        context.closeAsync().subscribe();
    }

    private static void handleAcceptedConnection(final ReadOnlyHttpServerConfig config, final Executor executor,
                                                 final HttpService<HttpPayloadChunk, HttpPayloadChunk> service,
                                                 final Channel channel, final ConnectionContext context) {
        channel.pipeline().addLast(new HttpRequestDecoder(config.getHeadersFactory(),
                config.getMaxInitialLineLength(), config.getMaxHeaderSize(), config.getMaxChunkSize(), true));
        channel.pipeline().addLast(new HttpResponseEncoder(config.getHeadersEncodedSizeEstimate(),
                config.getTrailersEncodedSizeEstimate()));
        channel.pipeline().addLast(new AbstractChannelReadHandler<Object>(LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE,
                executor) {
            @Override
            protected void onPublisherCreation(final ChannelHandlerContext channelHandlerContext,
                                               final Publisher<Object> requestObjectPublisher) {
                final NettyHttpServerConnection connection = new NettyHttpServerConnection(
                        channelHandlerContext.channel(), requestObjectPublisher,
                        new TerminalPredicate<>(LAST_HTTP_PAYLOAD_CHUNK_OBJECT_PREDICATE),
                        executor, context, service);
                connection.process().subscribe();
            }
        });
    }

    private static final class NettyHttpServerContext implements ServerContext {

        private final ServerContext delegate;
        private final ListenableAsyncCloseable asyncCloseable;

        NettyHttpServerContext(final ServerContext delegate, final HttpService service, final Executor executor) {
            this.delegate = delegate;
            asyncCloseable = toAsyncCloseable(() -> newCompositeCloseable().concat(service, delegate, executor).closeAsync());
        }

        @Override
        public SocketAddress getListenAddress() {
            return delegate.getListenAddress();
        }

        @Override
        public Completable closeAsync() {
            return asyncCloseable.closeAsync();
        }

        @Override
        public Completable onClose() {
            return asyncCloseable.onClose();
        }
    }
}
