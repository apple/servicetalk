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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.FileDescriptorSocketAddress;
import io.servicetalk.transport.netty.internal.BufferHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assume.assumeTrue;

/**
 * A utility to create a TCP clients for tests.
 */
public final class TcpClient {

    private final TcpConnector<Buffer, Buffer> connector;
    private final ReadOnlyTcpClientConfig roConfig;
    private final ExecutionContext executionContext;

    /**
     * New instance.
     * @param nettyIoExecutor {@link NettyIoExecutor} for this client.
     */
    public TcpClient(NettyIoExecutor nettyIoExecutor) {
        this(nettyIoExecutor, defaultConfig());
    }

    /**
     * New instance.
     * @param nettyIoExecutor {@link NettyIoExecutor} for this client.
     * @param config for the client.
     */
    public TcpClient(NettyIoExecutor nettyIoExecutor, TcpClientConfig config) {
        this(nettyIoExecutor, newCachedThreadExecutor(), config);
    }

    /**
     * New instance.
     * @param nettyIoExecutor {@link NettyIoExecutor} for this client.
     * @param executor The {@link Executor} for this client.
     * @param config for the client.
     */
    public TcpClient(NettyIoExecutor nettyIoExecutor, Executor executor, TcpClientConfig config) {
        this(nettyIoExecutor, executor, DEFAULT_ALLOCATOR, config);
    }

    /**
     * New instance.
     * @param nettyIoExecutor {@link NettyIoExecutor} for this client.
     * @param executor The {@link Executor} for this client.
     * @param allocator the {@link BufferAllocator} for this client.
     * @param config for the client.
     */
    public TcpClient(NettyIoExecutor nettyIoExecutor, Executor executor, BufferAllocator allocator,
                     TcpClientConfig config) {
        this(new DefaultExecutionContext(allocator, nettyIoExecutor, executor), config);
    }

    /**
     * New instance.
     * @param executionContext {@link NettyIoExecutor} for this client.
     * @param config for the client.
     */
    public TcpClient(ExecutionContext executionContext, TcpClientConfig config) {
        this.executionContext = requireNonNull(executionContext);
        roConfig = config.asReadOnly();
        ChannelInitializer initializer = new TcpClientChannelInitializer(roConfig);
        initializer = initializer.andThen((channel, context) -> {
            channel.pipeline().addLast(new BufferHandler(executionContext.getBufferAllocator()));
            return context;
        });
        connector = new TcpConnector<>(roConfig, initializer, () -> buffer -> false);
    }

    /**
     * Connect and await for the connection.
     *
     * @return New {@link Connection}.
     * @throws ExecutionException If connect failed.
     * @throws InterruptedException If interrupted while waiting for connect to complete.
     */
    public Connection<Buffer, Buffer> connectBlocking(int port) throws ExecutionException, InterruptedException {
        return connectBlocking(new InetSocketAddress(port));
    }

    /**
     * Connect and await for the connection.
     *
     * @param address to connect.
     * @return New {@link Connection}.
     * @throws ExecutionException If connect failed.
     * @throws InterruptedException If interrupted while waiting for connect to complete.
     */
    public Connection<Buffer, Buffer> connectBlocking(SocketAddress address)
            throws ExecutionException, InterruptedException {
        //noinspection ConstantConditions
        return awaitIndefinitely(connector.connect(executionContext, address));
    }

    /**
     * Connect using a {@link FileDescriptorSocketAddress} and await for the connection.
     *
     * @param address to connect.
     * @return New {@link Connection}.
     * @throws ExecutionException If connect failed.
     * @throws InterruptedException If interrupted while waiting for connect to complete.
     */
    public Connection<Buffer, Buffer> connectWithFdBlocking(SocketAddress address)
            throws ExecutionException, InterruptedException {
        assumeTrue(executionContext.getIoExecutor().isFileDescriptorSocketAddressSupported());
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
        Connection<Buffer, Buffer> connection = connectBlocking(fd);
        assertThat("Data read on the FileDescriptor from netty pipeline.",
                dataReadDirectlyFromNetty.get(), is(false));
        return connection;
    }

    /**
     * Returns configuration for this client.
     *
     * @return {@link ReadOnlyTcpClientConfig} for this client.
     */
    public ReadOnlyTcpClientConfig getConfig() {
        return roConfig;
    }

    private static TcpClientConfig defaultConfig() {
        TcpClientConfig config = new TcpClientConfig(false);
        // To test coverage of options.
        config.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        return config;
    }
}
