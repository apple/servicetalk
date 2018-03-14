/**
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
package io.servicetalk.tcp.netty.internal;

import io.netty.channel.ChannelHandlerContext;
import io.servicetalk.buffer.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoExecutorGroup;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AbstractChannelReadHandler;
import io.servicetalk.transport.netty.internal.BufferHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.NettyConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;

/**
 * A utility to create a TCP server for tests.
 */
public final class TcpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServer.class);
    private final ReadOnlyTcpServerConfig config;

    /**
     * New instance with default configuration.
     * @param ioExecutorGroup Determines the {@link IoExecutor} for new connections.
     */
    public TcpServer(IoExecutorGroup ioExecutorGroup) {
        this(new TcpServerConfig(false, ioExecutorGroup));
    }

    /**
     * New instance.
     * @param config for the server.
     */
    public TcpServer(TcpServerConfig config) {
        this.config = config.asReadOnly();
    }

    /**
     * Starts the server at the passed {@code port} and invoke the passed {@code service} for each accepted connection.
     *
     * @param port    Port for the server.
     * @param service {@link Function} that is invoked for each accepted connection.
     * @return {@link ServerContext} for the started server.
     * @throws ExecutionException   If the server start failed.
     * @throws InterruptedException If the calling thread was interrupted waiting for the server to start.
     */
    public ServerContext start(int port, Function<Connection<Buffer, Buffer>, Completable> service) throws ExecutionException, InterruptedException {
        TcpServerInitializer initializer = new TcpServerInitializer(config);
        ChannelInitializer channelInitializer = new TcpServerChannelInitializer(config).andThen((channel, context) -> {
            channel.pipeline().addLast(new BufferHandler(config.getAllocator()));
            channel.pipeline().addLast(new AbstractChannelReadHandler<Buffer>(buffer -> false) {
                @Override
                protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Buffer> newPublisher) {
                    Connection<Buffer, Buffer> conn = new NettyConnection<>(ctx.channel(), context, newPublisher);
                    service.apply(conn)
                            .doBeforeError(throwable -> LOGGER.error("Error handling a connection.", throwable))
                            .doBeforeFinally(() -> ctx.channel().close())
                            .subscribe(NoOpSubscriber.NOOP_SUBSCRIBER);
                }
            });
            return context;
        });
        //noinspection ConstantConditions
        return awaitIndefinitely(initializer.start(new InetSocketAddress(port), ContextFilter.ACCEPT_ALL, channelInitializer)
                .doBeforeSuccess(ctx -> LOGGER.info("Server started on port {}.", getServerPort(ctx)))
                .doBeforeError(throwable -> LOGGER.error("Failed starting server on port {}.", port)));
    }

    /**
     * Returns the listening port for the server represented by {@link ServerContext}.
     *
     * @param context for the server.
     * @return Listening port.
     * @throws  ClassCastException If the {@link SocketAddress} returned by {@link ServerContext#getListenAddress()} is not an {@link InetSocketAddress}.
     */
    public static int getServerPort(ServerContext context) {
        return ((InetSocketAddress) context.getListenAddress()).getPort();
    }

    private static final class NoOpSubscriber implements Completable.Subscriber {

        static final NoOpSubscriber NOOP_SUBSCRIBER = new NoOpSubscriber();

        private NoOpSubscriber() {
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            // No Op
        }

        @Override
        public void onComplete() {
            // No Op
        }

        @Override
        public void onError(Throwable ignore) {
            // No op
        }
    }
}
