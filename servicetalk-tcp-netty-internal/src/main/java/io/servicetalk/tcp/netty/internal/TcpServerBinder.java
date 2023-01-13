/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.BuilderUtils;
import io.servicetalk.transport.netty.internal.ChannelSet;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.transport.netty.internal.InfluencerConnectionAcceptor;
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
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toNettyAddress;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.close;
import static io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.POOLED_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.ExecutionContextUtils.channelExecutionContext;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.getOption;
import static java.util.Objects.requireNonNull;

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
     * @param autoRead if {@code true} auto read will be enabled for new {@link Channel}s.
     * @param executionContext The {@link ExecutionContext} to use for the bind socket.
     * @param connectionAcceptor The {@link ConnectionAcceptor} used to filter newly accepted sockets.
     * @param connectionFunction Used to create a new {@link NettyConnection} from a {@link Channel}.
     * @param connectionConsumer Used to consume the result of {@code connectionFunction} after initialization and
     * filtering is done. This can be used for protocol specific initialization and to start data flow.
     * @param <CC> The type of {@link ConnectionContext} that is created for each accepted socket.
     * @return a {@link Single} that completes with a {@link ServerContext} that represents a socket which is bound and
     * listening on the {@code listenAddress}.
     * @deprecated use the bind method with early and late acceptors instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <CC extends ConnectionContext> Single<ServerContext> bind(SocketAddress listenAddress,
            final ReadOnlyTcpServerConfig config, final boolean autoRead, final ExecutionContext<?> executionContext,
            @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
            final BiFunction<Channel, ConnectionObserver, Single<CC>> connectionFunction,
            final Consumer<CC> connectionConsumer) {
        return bind(listenAddress, config, autoRead, executionContext, connectionAcceptor, connectionFunction,
                connectionConsumer, null, null);
    }

    /**
     * Create a {@link ServerContext} that represents a socket which is bound and listening on the
     * {@code listenAddress}.
     *
     * @param listenAddress The address to bind to.
     * @param config The {@link ReadOnlyTcpServerConfig} to use for the bind socket and newly accepted sockets.
     * @param autoRead if {@code true} auto read will be enabled for new {@link Channel}s.
     * @param executionContext The {@link ExecutionContext} to use for the bind socket.
     * @param connectionAcceptor The {@link ConnectionAcceptor} used to filter newly accepted sockets.
     * @param connectionFunction Used to create a new {@link NettyConnection} from a {@link Channel}.
     * @param connectionConsumer Used to consume the result of {@code connectionFunction} after initialization and
     * filtering is done. This can be used for protocol specific initialization and to start data flow.
     * @param earlyConnectionAcceptor the {@link EarlyConnectionAcceptor} to filter newly accepted sockets early.
     * @param lateConnectionAcceptor the {@link LateConnectionAcceptor} to filter newly accepted sockets.
     * @param <CC> The type of {@link ConnectionContext} that is created for each accepted socket.
     * @return a {@link Single} that completes with a {@link ServerContext} that represents a socket which is bound and
     * listening on the {@code listenAddress}.
     */
    public static <CC extends ConnectionContext> Single<ServerContext> bind(SocketAddress listenAddress,
            final ReadOnlyTcpServerConfig config, final boolean autoRead, final ExecutionContext<?> executionContext,
            @Nullable final InfluencerConnectionAcceptor connectionAcceptor,
            final BiFunction<Channel, ConnectionObserver, Single<CC>> connectionFunction,
            final Consumer<CC> connectionConsumer,
            @Nullable final EarlyConnectionAcceptor earlyConnectionAcceptor,
            @Nullable final LateConnectionAcceptor lateConnectionAcceptor) {
        requireNonNull(connectionFunction);
        requireNonNull(connectionConsumer);
        listenAddress = toNettyAddress(listenAddress);
        EventLoopAwareNettyIoExecutor nettyIoExecutor = toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor());
        ServerBootstrap bs = new ServerBootstrap();
        configure(config, autoRead, bs, nettyIoExecutor.eventLoopGroup(), listenAddress.getClass());

        ChannelSet channelSet = new ChannelSet(
                executionContext.executionStrategy().isCloseOffloaded() ? executionContext.executor() : immediate());
        bs.handler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                // Verify that we do not leak pooled memory in the "accept" pipeline
                if (msg instanceof ReferenceCounted) {
                    try {
                        throw new IllegalArgumentException("Unexpected ReferenceCounted msg in 'accept' pipeline: " +
                                msg);
                    } finally {
                        ((ReferenceCounted) msg).release();
                    }
                }
                if (msg instanceof Channel) {
                    final Channel channel = (Channel) msg;
                    if (!channel.isActive()) {
                        channel.close();
                        LOGGER.debug("Channel ({}) is accepted, but was already inactive", msg);
                        return;
                    } else if (!channelSet.addIfAbsent(channel)) {
                        LOGGER.warn("Channel ({}) not added to ChannelSet", msg);
                        return;
                    }
                }
                ctx.fireChannelRead(msg);
            }
        });
        bs.childHandler(new io.netty.channel.ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel channel) {
                Single<CC> connectionSingle = connectionFunction.apply(channel,
                        config.transportObserver().onNewConnection(channel.localAddress(), channel.remoteAddress()));

                connectionSingle = wrapConnectionAcceptors(connectionSingle, channel, executionContext, config,
                        earlyConnectionAcceptor, lateConnectionAcceptor, connectionAcceptor);

                connectionSingle.beforeOnError(cause -> {
                    // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed to create a connection for remote address {}", channel.remoteAddress(),
                                cause);
                    }
                    close(channel, cause);
                }).subscribe(connectionConsumer);
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
                        close(channel, f.cause());
                        subscriber.onError(f.cause());
                    }
                });
            }
        };
    }

    /**
     * Wraps the connection function with early and late acceptors.
     *
     * @param connection the connection function which establishes the pipeline.
     * @param channel the netty channel on which the connection is run.
     * @param executionContext The {@link ExecutionContext} to use for the bind socket.
     * @param earlyConnectionAcceptor the {@link EarlyConnectionAcceptor} to filter newly accepted sockets early.
     * @param lateConnectionAcceptor the {@link LateConnectionAcceptor} to filter newly accepted sockets.
     * @param connectionAcceptor The {@link ConnectionAcceptor} used to filter newly accepted sockets.
     * @param <CC> The type of {@link ConnectionContext} that is created for each accepted socket.
     * @return the wrapped connection function with the connection acceptors (if any).
     */
    private static <CC extends ConnectionContext> Single<CC> wrapConnectionAcceptors(
            Single<CC> connection,
            final Channel channel,
            final ExecutionContext<?> executionContext,
            final ReadOnlyTcpServerConfig config,
            @Nullable final EarlyConnectionAcceptor earlyConnectionAcceptor,
            @Nullable final LateConnectionAcceptor lateConnectionAcceptor,
            @Nullable final InfluencerConnectionAcceptor connectionAcceptor) {
        final Executor offloadExecutor = executionContext.executor();

        if (earlyConnectionAcceptor != null) {
            final ExecutionContext<?> channelExecutionContext = channelExecutionContext(channel, executionContext);

            ConnectionInfo info = new ConnectionInfo() {
                @Override
                public SocketAddress localAddress() {
                    return channel.localAddress();
                }

                @Override
                public SocketAddress remoteAddress() {
                    return channel.remoteAddress();
                }

                @Override
                public ExecutionContext<?> executionContext() {
                    return channelExecutionContext;
                }

                @Nullable
                @Override
                public SslConfig sslConfig() {
                    return config.sslConfig();
                }

                @Nullable
                @Override
                public SSLSession sslSession() {
                    return null;
                }

                @Nullable
                @Override
                public <T> T socketOption(final SocketOption<T> option) {
                    return getOption(option, channel.config(), config.idleTimeoutMs());
                }

                @Override
                public Protocol protocol() {
                    return () -> "TCP";
                }
            };

            final EarlyConnectionAcceptorHandler acceptorHandler = new EarlyConnectionAcceptorHandler();
            channel.pipeline().addLast(acceptorHandler);

            // Defer is required to isolate the context between the user accept callback and the rest of the connection
            Completable earlyCompletable = Completable.defer(() -> earlyConnectionAcceptor.accept(info));

            if (earlyConnectionAcceptor.requiredOffloads().isConnectOffloaded()) {
                earlyCompletable = earlyCompletable.subscribeOn(offloadExecutor);
            }

            connection = earlyCompletable
                    .publishOn(channelExecutionContext.ioExecutor(), () -> !channel.eventLoop().inEventLoop())
                    .concat(connection)
                    .whenFinally(acceptorHandler::releaseEvents);
        }

        if (lateConnectionAcceptor != null) {
            connection = connection.flatMap(conn -> {
                // Defer is required to isolate the context between the user accept callback and
                // the rest of the connection
                Single<CC> deferred = defer(() -> lateConnectionAcceptor.accept(conn).concat(succeeded(conn)));
                if (lateConnectionAcceptor.requiredOffloads().isConnectOffloaded()) {
                    // TODO: change in 0.43 after removal of the old connection acceptor
                    // currentThreadIsIoThread check can be removed once the old connectionAcceptor is removed.
                    deferred = deferred.subscribeOn(offloadExecutor, IoThreadFactory.IoThread::currentThreadIsIoThread);
                }
                return deferred;
            });
        }

        if (connectionAcceptor != null) {
            connection = connection.flatMap(conn -> {
                // Defer is required to isolate the context between the user accept callback and
                // the rest of the connection
                Single<CC> deferred = defer(() -> connectionAcceptor.accept(conn).concat(succeeded(conn)));
                if (connectionAcceptor.requiredOffloads().isConnectOffloaded()) {
                    deferred = deferred.subscribeOn(offloadExecutor);
                }
                return deferred;
            });
        }

        return connection;
    }

    private static void configure(ReadOnlyTcpServerConfig config, boolean autoRead, ServerBootstrap bs,
                                  @Nullable EventLoopGroup eventLoopGroup,
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
        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.listenOptions().entrySet()) {
            @SuppressWarnings("unchecked")
            ChannelOption<Object> option = opt.getKey();
            bs.option(option, opt.getValue());
        }

        bs.childOption(ChannelOption.AUTO_READ, autoRead);

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        ByteBufAllocator byteBufAllocator = POOLED_ALLOCATOR;
        bs.option(ChannelOption.ALLOCATOR, byteBufAllocator);
        bs.childOption(ChannelOption.ALLOCATOR, byteBufAllocator);
    }
}
