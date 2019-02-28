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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.netty.BufferUtil;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.ChannelSet;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyServerContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toNettyAddress;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * Utility class to start a TCP based server.
 */
public final class TcpServerBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServerBinder.class);

    private TcpServerBinder() {
        // no instances
    }

    /**
     * Create a {@link ServerContext} that represents a socket which is bound and listening on the
     * {@code listenAddress}.
     *
     * @param listenAddress The address to bind to.
     * @param config The {@link ReadOnlyTcpServerConfig} to use for the bind socket and newly accepted sockets.
     * @param executionContext The {@link ExecutionContext} to use for the bind socket.
     * @param connectionAcceptor The {@link ConnectionAcceptor} used to filter newly accepted sockets.
     * @param connectionFunction Used to create a new {@link NettyConnection} from a {@link Channel}.
     * @param connectionConsumer Used to consume the result of {@code connectionFunction} after initialization and
     * filtering is done. This can be used for protocol specific initialization and to start data flow.
     * @param <T> The type of {@link ConnectionContext} that is created for each accepted socket.
     * @return a {@link Single} that completes with a {@link ServerContext} that represents a socket which is bound and
     * listening on the {@code listenAddress}.
     */
    public static <T extends ConnectionContext> Single<ServerContext> bind(
         SocketAddress listenAddress, ReadOnlyTcpServerConfig config, ExecutionContext executionContext,
         @Nullable ConnectionAcceptor connectionAcceptor,
         Function<Channel, Single<T>> connectionFunction, Consumer<T> connectionConsumer) {
        requireNonNull(connectionFunction);
        requireNonNull(connectionConsumer);
        listenAddress = toNettyAddress(listenAddress);
        EventLoopAwareNettyIoExecutor nettyIoExecutor = toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor());
        ServerBootstrap bs = new ServerBootstrap();
        configure(config, executionContext.bufferAllocator(), bs, nettyIoExecutor.eventLoopGroup(),
                listenAddress.getClass());

        ChannelSet channelSet = new ChannelSet(executionContext.executor());
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
                Single<T> connectionSingle = connectionFunction.apply(channel);
                if (connectionAcceptor != null) {
                    connectionSingle = connectionSingle
                            .flatMap(conn ->
                                executionContext.executor().submit(() ->
                                        connectionAcceptor.accept(conn).concatWith(success(conn))
                            ).flatMap(identity())
                            // The defer gives us isolation for AsyncContext. We don't want the Acceptor's AsyncContext
                            // to leak into the Service's AsyncContext.
                            // TODO(scott): can we improve this to use less operators?
                            // .flatMap(conn -> defer(() -> connectionAcceptor.accept(conn))
                            // TODO(scott): this isn't providing the expected offloading behavior ... fix.
                            // .subscribeOn(executionContext.executor())
                            //         .flatMap(result -> result != null && result ? success(conn) :
                            //                 error(new ConnectionRejectedException(
                            //                         "connection acceptor rejected a connection")))
                            ).doOnError(cause -> {
                                // Getting the remote-address may involve volatile reads and potentially a
                                // syscall, so guard it.
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Rejected connection from {}", channel.remoteAddress(), cause);
                                }
                                channel.close();
                            });
                }

                connectionSingle.subscribe(connectionConsumer);
            }
        });

        ChannelFuture future = bs.bind(listenAddress);
        return new SubscribableSingle<ServerContext>() {
            @Override
            protected void handleSubscribe(Subscriber<? super ServerContext> subscriber) {
                subscriber.onSubscribe(() -> future.cancel(true));
                future.addListener((ChannelFuture f) -> {
                    Channel channel = f.channel();
                    Throwable cause = f.cause();
                    if (cause == null) {
                        subscriber.onSuccess(NettyServerContext.wrap(channel, channelSet,
                                connectionAcceptor, executionContext));
                    } else {
                        channel.close();
                        subscriber.onError(f.cause());
                    }
                });
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static void configure(ReadOnlyTcpServerConfig config, BufferAllocator bufferAllocator,
                                  ServerBootstrap bs, @Nullable EventLoopGroup eventLoopGroup,
                                  Class<? extends SocketAddress> bindAddressClass) {
        if (eventLoopGroup == null) {
            throw new IllegalStateException("IoExecutor must be specified before building");
        }
        bs.group(eventLoopGroup);
        bs.channel(BuilderUtils.serverChannel(eventLoopGroup, bindAddressClass));

        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.options().entrySet()) {
            @SuppressWarnings("unchecked")
            ChannelOption<Object> option = opt.getKey();
            bs.childOption(option, opt.getValue());
        }

        // we disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        bs.childOption(ChannelOption.AUTO_READ, config.isAutoRead());
        if (!config.isAutoRead()) {
            bs.childOption(ChannelOption.MAX_MESSAGES_PER_READ, 1);
        }

        bs.option(ChannelOption.SO_BACKLOG, config.backlog());

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        ByteBufAllocator byteBufAllocator = BufferUtil.getByteBufAllocator(bufferAllocator);
        bs.option(ChannelOption.ALLOCATOR, byteBufAllocator);
        bs.childOption(ChannelOption.ALLOCATOR, byteBufAllocator);
    }
}
