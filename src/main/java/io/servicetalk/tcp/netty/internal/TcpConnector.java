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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.netty.BufferUtil;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.FileDescriptorSocketAddress;
import io.servicetalk.transport.netty.internal.AbstractChannelReadHandler;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.RefCountedTrapper;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;

import java.net.SocketAddress;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toNettyAddress;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.NettyConnectionContext.newContext;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for TCP clients to connect.
 *
 * @param <Read> Type of objects read from the channels established by this connector.
 * @param <Write> Type of objects written to the channels established by this connector.
 */
public final class TcpConnector<Read, Write> {
    private final ReadOnlyTcpClientConfig config;
    private final ChannelInitializer channelInitializer;
    private final Supplier<Predicate<Read>> terminalItemPredicate;
    @Nullable
    private final SocketAddress local;

    /**
     * New instance.
     * @param config to use for initialization.
     * @param channelInitializer for each connected channel.
     * @param terminalItemPredicate used for creating a new {@link Predicate} per channel to be passed to
     * {@link AbstractChannelReadHandler}.
     * @param local address.
     */
    public TcpConnector(ReadOnlyTcpClientConfig config, ChannelInitializer channelInitializer,
                        Supplier<Predicate<Read>> terminalItemPredicate,
                        @Nullable SocketAddress local) {
        this.config = requireNonNull(config);
        this.channelInitializer = requireNonNull(channelInitializer);
        this.terminalItemPredicate = requireNonNull(terminalItemPredicate);
        this.local = local;
    }

    /**
     * New instance.
     * @param config to use for initialization.
     * @param channelInitializer for each connected channel.
     * @param terminalItemPredicate used for creating a new {@link Predicate} per channel to be passed to
     * {@link AbstractChannelReadHandler}.
     */
    public TcpConnector(ReadOnlyTcpClientConfig config, ChannelInitializer channelInitializer,
                        Supplier<Predicate<Read>> terminalItemPredicate) {
        this(config, channelInitializer, terminalItemPredicate, null);
    }

    /**
     * Connects to the passed {@code remote} address, resolving the address, if required.
     *
     * @param executionContext Determines which {@link ExecutionContext} should be used for the connection.
     * @param remote address to connect.
     * @return {@link Single} that contains the {@link ConnectionContext} for the connection.
     */
    public Single<Connection<Read, Write>> connect(ExecutionContext executionContext, Object remote) {
        requireNonNull(remote);
        return new Single<Connection<Read, Write>>() {
            @Override
            protected void handleSubscribe(Subscriber<? super Connection<Read, Write>> subscriber) {
                connectFutureToListener(connect0(remote, executionContext, subscriber, true), subscriber, remote);
            }
        };
    }

    /**
     * Connects to the passed {@code remote} address, resolving the address, if required.
     *
     * @param executionContext Determines which {@link ExecutionContext} should be used for the connection.
     * @param remote address to connect.
     * @param checkForRefCountedTrapper log a warning if a {@link RefCountedTrapper} is not found in the pipeline
     * @return {@link Single} that contains the {@link ConnectionContext} for the connection.
     */
    public Single<Connection<Read, Write>> connect(ExecutionContext executionContext,
                                                   Object remote, boolean checkForRefCountedTrapper) {
        requireNonNull(remote);
        return new Single<Connection<Read, Write>>() {
            @Override
            protected void handleSubscribe(Subscriber<? super Connection<Read, Write>> subscriber) {
                connectFutureToListener(connect0(remote, executionContext, subscriber,
                        checkForRefCountedTrapper), subscriber, remote);
            }
        };
    }

    private Future<?> connect0(Object resolvedAddress, ExecutionContext executionContext,
                               Single.Subscriber<? super Connection<Read, Write>> subscriber,
                               boolean checkForRefCountedTrapper) {
        // We have to subscribe before any possibility that we complete the single, so subscribe now and hookup the
        // cancellable after we get the future.
        final DelayedCancellable cancellable = new DelayedCancellable();
        subscriber.onSubscribe(cancellable);

        try {
            // The ConnectionContext should be given an IoExecutor which correlates to the specific thread used for IO,
            // so we select it here up front.
            EventLoopAwareNettyIoExecutor ioExecutorThread =
                    toEventLoopAwareNettyIoExecutor(executionContext.getIoExecutor()).next();

            // next() of an EventLoop will just return itself, which is expected because we did the selection above.
            EventLoop loop = ioExecutorThread.getEventLoopGroup().next();

            // Create the handler here and ensure in connectWithBootstrap / initFileDescriptorBasedChannel it is added to
            // the ChannelPipeline after registration is complete as otherwise we may miss channelActive events.
            ChannelHandler handler = new io.netty.channel.ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    ConnectionContext context = newContext(channel, ioExecutorThread, executionContext.getExecutor(),
                            executionContext.getBufferAllocator(), channelInitializer, checkForRefCountedTrapper);
                    AbstractChannelReadHandler readHandler = channel.pipeline().get(AbstractChannelReadHandler.class);
                    if (readHandler != null) {
                        subscriber.onError(new IllegalStateException(
                            format("A handler %s of type %s already found, can not connect with this existing handler.",
                                        readHandler, AbstractChannelReadHandler.class.getName())));
                    } else {
                        Connection.TerminalPredicate<Read> predicate =
                                new Connection.TerminalPredicate<>(terminalItemPredicate.get());
                        // TODO(scott): what executor should be used here?
                        channel.pipeline().addLast(new AbstractChannelReadHandler<Read>(predicate, immediate()) {
                            @Override
                            protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Read> newPublisher) {
                                subscriber.onSuccess(new NettyConnection<>(channel, context, newPublisher, predicate));
                            }
                        });
                    }
                }
            };

            if (!(resolvedAddress instanceof FileDescriptorSocketAddress)) {
                return attachCancelSubscriber(connectWithBootstrap(resolvedAddress, loop,
                        executionContext.getBufferAllocator(), handler), cancellable);
            }
            if (local != null) {
                throw new IllegalArgumentException("local cannot be specified when " +
                        FileDescriptorSocketAddress.class.getSimpleName() + " is used");
            }
            Channel channel = socketChannel(loop, (FileDescriptorSocketAddress) resolvedAddress);
            if (channel == null) {
                throw new IllegalArgumentException(FileDescriptorSocketAddress.class.getSimpleName() + " not supported");
            }
            return attachCancelSubscriber(initFileDescriptorBasedChannel(loop, channel,
                    executionContext.getBufferAllocator(), handler), cancellable);
        } catch (Throwable cause) {
            cancellable.setDelayedCancellable(IGNORE_CANCEL);
            return ImmediateEventExecutor.INSTANCE.newFailedFuture(cause);
        }
    }

    private static ChannelFuture attachCancelSubscriber(ChannelFuture channelFuture, DelayedCancellable cancellable) {
        cancellable.setDelayedCancellable(() -> channelFuture.cancel(false));
        return channelFuture;
    }

    private ChannelFuture connectWithBootstrap(Object resolvedAddress, EventLoop loop,
                                               BufferAllocator bufferAllocator, ChannelHandler handler) {
        final SocketAddress nettyResolvedRemote = toNettyAddress(resolvedAddress);
        Bootstrap bs = new Bootstrap();
        bs.group(loop);
        bs.channel(socketChannel(loop, nettyResolvedRemote.getClass()));
        bs.handler(handler);

        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.getOptions().entrySet()) {
            //noinspection unchecked
            bs.option(opt.getKey(), opt.getValue());
        }

        // we disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        bs.option(ChannelOption.AUTO_READ, config.isAutoRead());
        if (!config.isAutoRead()) {
            bs.option(ChannelOption.MAX_MESSAGES_PER_READ, 1);
        }

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        bs.option(ChannelOption.ALLOCATOR, BufferUtil.getByteBufAllocator(bufferAllocator));

        // If the connect operation fails we must take care to fail the promise.
        return bs.connect(nettyResolvedRemote, local);
        /*//TODO: Request Context fix.
        if (logger.isDebugEnabled()) {
            // Preserve context in listener so that context information is logged

            RequestContext context = RequestContexts.container().context();
            channelFuture.addListener((ChannelFutureListener) f -> {
                RequestContext original = RequestContexts.container().context();
                RequestContexts.container().context(context);
                try {
                    if (f.isSuccess()) {
                        Channel channel = f.channel();
                        logger.debug("SRC={} DST={} Client connected to remote address",
                                formatCanonicalAddress(channel.localAddress()),
                                formatCanonicalAddress(channel.remoteAddress()));
                    } else {
                        logger.debug("DST={} Client connection failed", resolvedAddress, f.cause());
                    }
                } finally {
                    RequestContexts.container().context(original);
                }
            });
        }
        */
    }

    private ChannelFuture initFileDescriptorBasedChannel(EventLoop loop, Channel channel,
                                                         BufferAllocator bufferAllocator, ChannelHandler handler) {
        for (@SuppressWarnings("rawtypes") Map.Entry<ChannelOption, Object> opt : config.getOptions().entrySet()) {
            //noinspection unchecked
            channel.config().setOption(opt.getKey(), opt.getValue());
        }

        // we disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        channel.config().setOption(ChannelOption.AUTO_READ, config.isAutoRead());
        if (!config.isAutoRead()) {
            channel.config().setOption(ChannelOption.MAX_MESSAGES_PER_READ, 1);
        }

        // Set the correct ByteBufAllocator based on our BufferAllocator to minimize memory copies.
        channel.config().setAllocator(BufferUtil.getByteBufAllocator(bufferAllocator));
        channel.pipeline().addLast(handler);
        return loop.register(channel);
    }

    private void connectFutureToListener(Future<?> future,
                                         Single.Subscriber<? super Connection<Read, Write>> subscriber,
                                         Object resolvedAddress) {
        future.addListener(f -> {
            Throwable cause = f.cause();
            if (cause != null) {
                if (cause instanceof ConnectTimeoutException) {
                    String msg = resolvedAddress instanceof FileDescriptorSocketAddress ? "Failed to register: " +
                            resolvedAddress : "Failed to connect: " + resolvedAddress + " (local: " + local + ")";
                    cause = new io.servicetalk.client.api.ConnectTimeoutException(msg, cause);
                }
                subscriber.onError(cause);
            }
        });
    }
}
