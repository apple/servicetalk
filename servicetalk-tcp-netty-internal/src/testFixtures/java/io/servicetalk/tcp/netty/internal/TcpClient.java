/*
 * Copyright © 2018, 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.FileDescriptorSocketAddress;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.BufferHandler;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.unix.UnixChannel;

import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.tcp.netty.internal.TcpProtocol.TCP;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.DefaultNettyConnection.initChannel;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A utility to create a TCP clients for tests.
 */
final class TcpClient {

    private final ReadOnlyTcpClientConfig config;
    private final TransportObserver observer;

    /**
     * New instance.
     *
     * @param config for the client.
     * @param observer {@link TransportObserver} for the newly created connection.
     */
    TcpClient(TcpClientConfig config, TransportObserver observer) {
        this.config = config.asReadOnly();
        this.observer = requireNonNull(observer);
    }

    /**
     * Connect and await for the connection.
     *
     * @param executionContext {@link ExecutionContext} to use for the connections.
     * @param address to connect.
     * @return New {@link NettyConnection}.
     * @throws ExecutionException If connect failed.
     * @throws InterruptedException If interrupted while waiting for connect to complete.
     */
    public NettyConnection<Buffer, Buffer> connectBlocking(ExecutionContext executionContext, SocketAddress address)
            throws ExecutionException, InterruptedException {
        return connect(executionContext, address).toFuture().get();
    }

    /**
     * Connect to the passed {@code address}.
     *
     * @param executionContext {@link ExecutionContext} to use for the connections.
     * @param address to connect.
     * @return New {@link NettyConnection}.
     */
    public Single<NettyConnection<Buffer, Buffer>> connect(ExecutionContext executionContext, SocketAddress address) {
        return TcpConnector.connect(null, address, config, false, executionContext,
                (channel, connectionObserver) -> initChannel(channel,
                        executionContext.bufferAllocator(), executionContext.executor(), buffer -> false,
                        UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, config.flushStrategy(), config.idleTimeoutMs(),
                        new TcpClientChannelInitializer(config, connectionObserver).andThen(
                                channel2 -> channel2.pipeline().addLast(BufferHandler.INSTANCE)),
                        executionContext.executionStrategy(), TCP, connectionObserver, true),
                observer);
    }

    /**
     * Connect using a {@link FileDescriptorSocketAddress} and await for the connection.
     *
     * @param executionContext {@link ExecutionContext} to use for the connections.
     * @param address to connect.
     * @return New {@link NettyConnection}.
     * @throws ExecutionException If connect failed.
     * @throws InterruptedException If interrupted while waiting for connect to complete.
     */
    public NettyConnection<Buffer, Buffer> connectWithFdBlocking(ExecutionContext executionContext,
                                                                 SocketAddress address)
            throws ExecutionException, InterruptedException {
        assumeTrue(executionContext.ioExecutor().isFileDescriptorSocketAddressSupported());
        assumeTrue(Epoll.isAvailable() || KQueue.isAvailable());

        final Class<? extends Channel> channelClass;
        final EventLoopGroup eventLoopGroup;
        if (Epoll.isAvailable()) {
            eventLoopGroup = new EpollEventLoopGroup(1);
            channelClass = EpollSocketChannel.class;
        } else {
            eventLoopGroup = new KQueueEventLoopGroup(1);
            channelClass = KQueueSocketChannel.class;
        }
        AtomicBoolean dataReadDirectlyFromNetty = new AtomicBoolean();
        // Bootstrap a netty channel to the server so we can access its FD and wrap it later in ST.
        Bootstrap bs = new Bootstrap();
        UnixChannel channel = (UnixChannel) bs.channel(channelClass)
                .group(eventLoopGroup)
                .handler(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
                        dataReadDirectlyFromNetty.set(true);
                    }
                })
                .connect(address)
                .syncUninterruptibly().channel();

        // Unregister it from the netty EventLoop as we want to to handle it via ST.
        channel.deregister().syncUninterruptibly();
        FileDescriptorSocketAddress fd = new FileDescriptorSocketAddress(channel.fd().intValue());
        NettyConnection<Buffer, Buffer> connection = connectBlocking(executionContext, fd);
        assertThat("Data read on the FileDescriptor from netty pipeline.",
                dataReadDirectlyFromNetty.get(), is(false));
        return connection;
    }

    /**
     * Returns configuration for this client.
     *
     * @return {@link ReadOnlyTcpClientConfig} for this client.
     */
    public ReadOnlyTcpClientConfig config() {
        return config;
    }

    private static TcpClientConfig defaultConfig() {
        TcpClientConfig config = new TcpClientConfig();
        // To test coverage of options.
        config.socketOption(StandardSocketOptions.SO_KEEPALIVE, true);
        return config;
    }
}
