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

import io.servicetalk.buffer.netty.BufferUtil;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ContextFilter;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.ChannelSet;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.transport.netty.internal.NettyServerContext;
import io.servicetalk.transport.netty.internal.RefCountedTrapper;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.AUTO_CLOSE;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toNettyAddress;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.NettyConnectionContext.newContext;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static java.util.Objects.requireNonNull;

/**
 * Utility class to start a TCP based server.
 */
public final class TcpServerInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServerInitializer.class);

    private final ExecutionContext executionContext;
    private final EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private final ReadOnlyTcpServerConfig config;

    /**
     * New instance.
     *
     * @param executionContext The {@link ExecutionContext} that is used for the IO and asynchronous source creation.
     * @param config to use for initialization.
     */
    public TcpServerInitializer(ExecutionContext executionContext, ReadOnlyTcpServerConfig config) {
        this.executionContext = executionContext;
        nettyIoExecutor = toEventLoopAwareNettyIoExecutor(executionContext.getIoExecutor());
        this.config = config;
    }

    /**
     * Starts a server listening on the {@code listenAddress}.
     *
     * @param listenAddress for the server.
     * @return Single which completes when the server is started.
     */
    public Single<ServerContext> startWithDefaults(SocketAddress listenAddress) {
        return startWithDefaults(listenAddress, ContextFilter.ACCEPT_ALL);
    }

    /**
     * Starts a server listening on the {@code listenAddress}.
     *
     * @param listenAddress for the server.
     * @param contextFilter to use for filtering accepted connections.
     * @return Single which completes when the server is started.
     */
    public Single<ServerContext> startWithDefaults(SocketAddress listenAddress, ContextFilter contextFilter) {
        return start(listenAddress, contextFilter, new TcpServerChannelInitializer(config, contextFilter));
    }

    /**
     * Starts a server using the passed {@code channelInitializer} on the {@code listenAddress}.
     *
     * @param listenAddress for the server.
     * @param contextFilter to use for filtering accepted connections.
     * @param channelInitializer to use for initializing all accepted connections.
     * @return {@link Single} which completes when the server is started.
     */
    public Single<ServerContext> start(SocketAddress listenAddress, ContextFilter contextFilter,
                                       ChannelInitializer channelInitializer) {
        return start(listenAddress, contextFilter, channelInitializer, true, false);
    }

    /**
     * Starts a server using the passed {@code channelInitializer} on the {@code listenAddress}.
     *
     * @param listenAddress for the server.
     * @param contextFilter to use for filtering accepted connections.
     * @param channelInitializer to use for initializing all accepted connections.
     * @param checkForRefCountedTrapper Whether to log a warning if a {@link RefCountedTrapper} is not found in the
     * pipeline.
     * @param enableHalfClosure whether half-closure should be enabled and a handler will be installed to manage closure
     * @return {@link Single} which completes when the server is started.
     * @see CloseHandler to manage half-closing connections from the protocol
     */
    public Single<ServerContext> start(SocketAddress listenAddress, ContextFilter contextFilter,
                                       ChannelInitializer channelInitializer,
                                       boolean checkForRefCountedTrapper, boolean enableHalfClosure) {
        requireNonNull(channelInitializer);
        requireNonNull(contextFilter);
        listenAddress = toNettyAddress(requireNonNull(listenAddress));
        ServerBootstrap bs = new ServerBootstrap();
        configure(bs, nettyIoExecutor.getEventLoopGroup(), listenAddress.getClass(), enableHalfClosure);

        ChannelSet channelSet = new ChannelSet(executionContext.getExecutor());
        bs.handler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                if (msg instanceof Channel && !channelSet.addIfAbsent((Channel) msg)) {
                    LOGGER.warn("Channel ({}) not added to ChannelSet", msg);
                }
                ctx.fireChannelRead(msg);
            }
        });
        bs.childHandler(new io.netty.channel.ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                // The ConnectionContext should be given an IoExecutor which correlates to the specific thread
                // used for IO.
                newContext(new DefaultExecutionContext(executionContext.getBufferAllocator(),
                                fromNettyEventLoop(channel.eventLoop()), executionContext.getExecutor()),
                        channel,
                        channelInitializer,
                        checkForRefCountedTrapper);
            }
        });

        ChannelFuture future = bs.bind(listenAddress);
        return new Single<ServerContext>() {
            @Override
            protected void handleSubscribe(Subscriber<? super ServerContext> subscriber) {
                subscriber.onSubscribe(() -> future.cancel(true));
                ChannelFutureListener channelFutureListener = f -> {
                    Channel channel = f.channel();
                    Throwable cause = f.cause();
                    if (cause == null) {
                        subscriber.onSuccess(NettyServerContext.wrap(channel, channelSet, contextFilter,
                                executionContext.getExecutor()));
                    } else {
                        channel.close();
                        subscriber.onError(f.cause());
                    }
                };
                future.addListener(channelFutureListener);
            }
        };
    }

    @SuppressWarnings("deprecation")
    private void configure(ServerBootstrap bs, @Nullable EventLoopGroup eventLoopGroup,
                           Class<? extends SocketAddress> bindAddressClass, final boolean enableHalfClosure) {
        if (eventLoopGroup == null) {
            throw new IllegalStateException("IoExecutor must be specified before building");
        }
        bs.group(eventLoopGroup);
        bs.channel(BuilderUtils.serverChannel(eventLoopGroup, bindAddressClass));

        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.getOptions().entrySet()) {
            @SuppressWarnings("unchecked")
            ChannelOption<Object> option = opt.getKey();
            bs.childOption(option, opt.getValue());
        }

        // we disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        bs.childOption(ChannelOption.AUTO_READ, config.isAutoRead());
        if (!config.isAutoRead()) {
            bs.childOption(ChannelOption.MAX_MESSAGES_PER_READ, 1);
        }

        if (enableHalfClosure) {
            bs.childOption(ALLOW_HALF_CLOSURE, true);
            bs.childOption(AUTO_CLOSE, false);
        }

        bs.option(ChannelOption.SO_BACKLOG, config.getBacklog());

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        bs.option(ChannelOption.ALLOCATOR, BufferUtil.getByteBufAllocator(executionContext.getBufferAllocator()));
        bs.childOption(ChannelOption.ALLOCATOR, BufferUtil.getByteBufAllocator(executionContext.getBufferAllocator()));
    }
}
