/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.RetryableConnectException;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.FileDescriptorSocketAddress;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.NoopAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toNettyAddress;
import static io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.POOLED_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Utility class for TCP clients to connect.
 */
public final class TcpConnector {

    private TcpConnector() {
        // no instances.
    }

    /**
     * Connects to the passed {@code resolvedRemoteAddress} address, resolving the address, if required.
     *
     * @param localAddress The local address to bind to, or {@code null}.
     * @param resolvedRemoteAddress The address to connect to. This address should already be resolved at this point.
     * @param config The {@link ReadOnlyTcpClientConfig} to use while connecting.
     * @param autoRead if {@code true} auto read will be enabled for new {@link Channel}s.
     * @param executionContext The {@link ExecutionContext} to use for the returned {@link NettyConnection}.
     * @param connectionFactory {@link BiFunction} to create a {@link NettyConnection} asynchronously.
     * @param observer {@link TransportObserver} to use for new connections.
     * @param <C> Type of the created connection.
     * @return A {@link Single} that completes with a new {@link Channel} when connected.
     */
    public static <C extends ListenableAsyncCloseable> Single<C> connect(
            @Nullable final SocketAddress localAddress, final Object resolvedRemoteAddress,
            final ReadOnlyTcpClientConfig config, final boolean autoRead, final ExecutionContext<?> executionContext,
            final BiFunction<Channel, ConnectionObserver, Single<? extends C>> connectionFactory,
            final TransportObserver observer) {
        requireNonNull(resolvedRemoteAddress);
        requireNonNull(config);
        requireNonNull(executionContext);
        requireNonNull(connectionFactory);
        requireNonNull(observer);
        return new SubscribableSingle<C>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super C> subscriber) {
                ConnectHandler<C> connectHandler = new ConnectHandler<>(subscriber, connectionFactory, observer);
                try {

                    Future<?> connectFuture = connect0(localAddress, resolvedRemoteAddress, config, autoRead,
                            executionContext, connectHandler);
                    connectHandler.connectFuture(connectFuture);
                    connectFuture.addListener(f -> {
                        Throwable cause = f.cause();
                        if (cause != null) {
                            if (cause instanceof ConnectTimeoutException) {
                                String msg = resolvedRemoteAddress instanceof FileDescriptorSocketAddress ?
                                        "Failed to register: " + resolvedRemoteAddress :
                                        "Failed to connect: " + resolvedRemoteAddress + " (localAddress: " +
                                        localAddress + ")";
                                cause = new io.servicetalk.client.api.ConnectTimeoutException(msg, cause);
                            } else if (cause instanceof ConnectException) {
                                cause = new RetryableConnectException((ConnectException) cause);
                            }
                            connectHandler.connectFailed(cause);
                        }
                    });
                } catch (Throwable t) {
                    connectHandler.unexpectedFailure(t);
                }
            }
        };
    }

    private static Future<?> connect0(@Nullable SocketAddress localAddress, Object resolvedRemoteAddress,
                                      ReadOnlyTcpClientConfig config, boolean autoRead,
                                      ExecutionContext<?> executionContext,
                                      Consumer<? super Channel> subscriber) {
        // Create the handler here and ensure in connectWithBootstrap / initFileDescriptorBasedChannel it is added
        // to the ChannelPipeline after registration is complete as otherwise we may miss channelActive events.
        ChannelHandler handler = new io.netty.channel.ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                subscriber.accept(channel);
            }
        };

        EventLoop loop = toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor()).eventLoopGroup().next();
        if (!(resolvedRemoteAddress instanceof FileDescriptorSocketAddress)) {
            return connectWithBootstrap(localAddress, resolvedRemoteAddress, config, autoRead, loop, handler);
        }
        if (localAddress != null) {
            return loop.newFailedFuture(new IllegalArgumentException("local address cannot be specified when " +
                    FileDescriptorSocketAddress.class.getSimpleName() + " is used"));
        }
        Channel channel = socketChannel(loop, (FileDescriptorSocketAddress) resolvedRemoteAddress);
        if (channel == null) {
            return loop.newFailedFuture(new IllegalArgumentException(
                    FileDescriptorSocketAddress.class.getSimpleName() + " not supported"));
        }
        return initFileDescriptorBasedChannel(config, autoRead, loop, channel, handler);
    }

    private static ChannelFuture connectWithBootstrap(
            @Nullable SocketAddress localAddress, Object resolvedRemoteAddress, ReadOnlyTcpClientConfig config,
            boolean autoRead, EventLoop loop, ChannelHandler handler) {
        final SocketAddress nettyresolvedRemoteAddress = toNettyAddress(resolvedRemoteAddress);
        Bootstrap bs = new Bootstrap();
        bs.resolver(NoopNettyAddressResolverGroup.INSTANCE);
        bs.group(loop);
        bs.channel(socketChannel(loop, nettyresolvedRemoteAddress.getClass()));
        bs.handler(handler);

        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.options().entrySet()) {
            //noinspection unchecked
            bs.option(opt.getKey(), opt.getValue());
        }
        bs.option(ChannelOption.AUTO_READ, autoRead);

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        bs.option(ChannelOption.ALLOCATOR, POOLED_ALLOCATOR);

        // If the connect operation fails we must take care to fail the promise.
        return bs.connect(nettyresolvedRemoteAddress, localAddress);
    }

    private static ChannelFuture initFileDescriptorBasedChannel(
            ReadOnlyTcpClientConfig config, boolean autoRead, EventLoop loop, Channel channel, ChannelHandler handler) {
        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.options().entrySet()) {
            //noinspection unchecked
            channel.config().setOption(opt.getKey(), opt.getValue());
        }
        channel.config().setOption(ChannelOption.AUTO_READ, autoRead);

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        channel.config().setAllocator(POOLED_ALLOCATOR);
        channel.pipeline().addLast(handler);
        return loop.register(channel);
    }

    /**
     * A {@link AddressResolverGroup} that is used internally so Netty won't try to
     * resolve addresses, because ServiceTalk is responsible for resolution.
     */
    private static final class NoopNettyAddressResolverGroup extends AddressResolverGroup<SocketAddress> {
        static final AddressResolverGroup<SocketAddress> INSTANCE = new NoopNettyAddressResolverGroup();
        private static final AbstractAddressResolver<SocketAddress> NOOP_ADDRESS_RESOLVER =
                new NoopAddressResolver(ImmediateEventExecutor.INSTANCE);

        private NoopNettyAddressResolverGroup() {
            // singleton
        }

        @Override
        protected AddressResolver<SocketAddress> newResolver(EventExecutor executor) {
            return NOOP_ADDRESS_RESOLVER;
        }

        @Override
        public AddressResolver<SocketAddress> getResolver(final EventExecutor executor) {
            return NOOP_ADDRESS_RESOLVER;
        }
    }

    private static final class ConnectHandler<C extends ListenableAsyncCloseable> implements Consumer<Channel> {
        private static final Logger LOGGER = LoggerFactory.getLogger(ConnectHandler.class);
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<ConnectHandler> terminatedUpdater =
                newUpdater(ConnectHandler.class, "terminated");

        private final DelayedCancellable futureCancellable = new DelayedCancellable();
        private final DelayedCancellable flatMapCancellable = new DelayedCancellable();
        private final Subscriber<? super C> target;
        private final BiFunction<Channel, ConnectionObserver, Single<? extends C>> connectionFactory;
        private final ConnectionObserver connectionObserver;

        private volatile int terminated;

        ConnectHandler(final Subscriber<? super C> target,
                       final BiFunction<Channel, ConnectionObserver, Single<? extends C>> connectionFactory,
                       final TransportObserver observer) {
            this.target = target;
            this.connectionFactory = connectionFactory;
            target.onSubscribe(() -> {
                try {
                    futureCancellable.cancel();
                } finally {
                    flatMapCancellable.cancel();
                }
            });
            connectionObserver = observer.onNewConnection();
        }

        @Override
        public void accept(final Channel channel) {
            toSource(connectionFactory.apply(channel, connectionObserver)
                    .subscribeShareContext())
                    .subscribe(new Subscriber<C>() {
                        @Override
                        public void onSubscribe(final Cancellable cancellable) {
                            flatMapCancellable.delayedCancellable(cancellable);
                        }

                        @Override
                        public void onSuccess(@Nullable final C connection) {
                            if (terminatedUpdater.compareAndSet(ConnectHandler.this, 0, 1)) {
                                target.onSuccess(connection);
                            } else {
                                LOGGER.debug("Connection {} created for a channel: {} but connect failed previously. " +
                                                "Closing connection.",
                                        connection, channel);
                                if (connection != null) {
                                    connection.closeAsync().subscribe();
                                }
                            }
                        }

                        @Override
                        public void onError(final Throwable t) {
                            if (terminatedUpdater.compareAndSet(ConnectHandler.this, 0, 1)) {
                                target.onError(t);
                            } else {
                                // We assume the connection factor owns the lifetime of the channel, so we do not try
                                // to close it.
                                LOGGER.debug("Ignored duplicate connect failure for channel: {}.", channel, t);
                            }
                        }
                    });
        }

        void connectFuture(final Future<?> connectFuture) {
            futureCancellable.delayedCancellable(() -> connectFuture.cancel(false));
        }

        void connectFailed(final Throwable cause) {
            if (terminatedUpdater.compareAndSet(this, 0, 1)) {
                target.onError(cause);
            }
        }

        void unexpectedFailure(final Throwable cause) {
            if (terminatedUpdater.compareAndSet(this, 0, 1)) {
                target.onError(cause);
            }
        }
    }
}
