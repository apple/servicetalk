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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.BufferHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.tcp.netty.internal.TcpProtocol.TCP;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.util.Collections.emptyList;

/**
 * A utility to create a TCP server for tests.
 */
public class TcpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServer.class);
    private final ReadOnlyTcpServerConfig config;

    /**
     * New instance with default configuration.
     */
    public TcpServer() {
        this(new TcpServerConfig());
    }

    /**
     * New instance.
     *
     * @param config for the server.
     */
    public TcpServer(TcpServerConfig config) {
        this.config = config.asReadOnly(emptyList());
    }

    /**
     * Starts the server at the passed {@code port} and invoke the passed {@code service} for each accepted connection.
     * Awaits for the server to start.
     *
     * @param executionContext {@link ExecutionContext} to use for incoming connections.
     * @param port Port for the server.
     * @param connectionAcceptor to use for filtering accepted connections. The returned {@link ServerContext} manages
     * the lifecycle of the {@code connectionAcceptor}, ensuring it is closed when the {@link ServerContext} is closed.
     * @param service {@link Function} that is invoked for each accepted connection.
     * @param executionStrategy {@link ExecutionStrategy} to use.
     * @return {@link ServerContext} for the started server.
     * @throws ExecutionException If the server start failed.
     * @throws InterruptedException If the calling thread was interrupted waiting for the server to start.
     */
    public ServerContext bind(ExecutionContext executionContext, int port,
                              @Nullable ConnectionAcceptor connectionAcceptor,
                              Function<NettyConnection<Buffer, Buffer>, Completable> service,
                              ExecutionStrategy executionStrategy)
            throws ExecutionException, InterruptedException {
        return TcpServerBinder.bind(localAddress(port), config, false,
                executionContext, connectionAcceptor,
                channel -> {
                    final TransportObserver observer = config.transportObserver();
                    final ConnectionObserver connectionObserver = observer == null ? null : observer.onNewConnection();
                    return DefaultNettyConnection.<Buffer, Buffer>initChannel(channel,
                            executionContext.bufferAllocator(), executionContext.executor(), buffer -> false,
                            UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, config.flushStrategy(), config.idleTimeoutMs(),
                            new TcpServerChannelInitializer(config, connectionObserver)
                                    .andThen(getChannelInitializer(service, executionContext)), executionStrategy, TCP,
                            connectionObserver);
                },
                serverConnection -> service.apply(serverConnection)
                        .beforeOnError(throwable -> LOGGER.error("Error handling a connection.", throwable))
                        .beforeFinally(() -> serverConnection.closeAsync().subscribe())
                        .subscribe())
                .beforeOnSuccess(ctx -> LOGGER.info("Server started on port {}.", getServerPort(ctx)))
                .beforeOnError(throwable -> LOGGER.error("Failed starting server on port {}.", port))
                .toFuture().get();
    }

    // Visible to allow tests to override.
    ChannelInitializer getChannelInitializer(final Function<NettyConnection<Buffer, Buffer>, Completable> service,
                                             final ExecutionContext executionContext) {
        return channel -> channel.pipeline().addLast(BufferHandler.INSTANCE);
    }

    /**
     * Returns the listening port for the server represented by {@link ServerContext}.
     *
     * @param context for the server.
     * @return Listening port.
     * @throws ClassCastException If the {@link SocketAddress} returned by {@link ServerContext#listenAddress()} is
     * not an {@link InetSocketAddress}.
     */
    public static int getServerPort(ServerContext context) {
        return ((InetSocketAddress) context.listenAddress()).getPort();
    }
}
