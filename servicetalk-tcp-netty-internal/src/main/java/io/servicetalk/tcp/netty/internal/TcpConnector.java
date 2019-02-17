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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.netty.BufferUtil;
import io.servicetalk.client.api.RetryableConnectException;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.FileDescriptorSocketAddress;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toNettyAddress;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.util.Objects.requireNonNull;

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
     * @param executionContext The {@link ExecutionContext} to use for the returned {@link NettyConnection}.
     * @return A {@link Single} that completes with a new {@link Channel} when connected.
     */
    public static Single<Channel> connect(@Nullable SocketAddress localAddress, Object resolvedRemoteAddress,
                                          ReadOnlyTcpClientConfig config, ExecutionContext executionContext) {
        requireNonNull(resolvedRemoteAddress);
        requireNonNull(config);
        requireNonNull(executionContext);
        return new Single<Channel>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Channel> subscriber) {
                connectFutureToListener(localAddress, resolvedRemoteAddress, subscriber,
                        connect0(localAddress, resolvedRemoteAddress, config, executionContext, subscriber));
            }
        };
    }

    private static Future<?> connect0(@Nullable SocketAddress localAddress, Object resolvedRemoteAddress,
                                      ReadOnlyTcpClientConfig config, ExecutionContext executionContext,
                                      SingleSource.Subscriber<? super Channel> subscriber) {
        // We have to subscribe before any possibility that we complete the single, so subscribe now and hookup the
        // cancellable after we get the future.
        final DelayedCancellable cancellable = new DelayedCancellable();
        subscriber.onSubscribe(cancellable);

        try {
            // Create the handler here and ensure in connectWithBootstrap / initFileDescriptorBasedChannel it is added to
            // the ChannelPipeline after registration is complete as otherwise we may miss channelActive events.
            ChannelHandler handler = new io.netty.channel.ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    subscriber.onSuccess(channel);
                }
            };

            EventLoop loop = toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor()).getEventLoopGroup().next();
            if (!(resolvedRemoteAddress instanceof FileDescriptorSocketAddress)) {
                return attachCancelSubscriber(connectWithBootstrap(localAddress, resolvedRemoteAddress, config,
                        loop, executionContext.bufferAllocator(), handler), cancellable);
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
            return attachCancelSubscriber(initFileDescriptorBasedChannel(config, loop, channel,
                    executionContext.bufferAllocator(), handler), cancellable);
        } catch (Throwable cause) {
            cancellable.setDelayedCancellable(IGNORE_CANCEL);
            return ImmediateEventExecutor.INSTANCE.newFailedFuture(cause);
        }
    }

    private static ChannelFuture attachCancelSubscriber(ChannelFuture channelFuture, DelayedCancellable cancellable) {
        cancellable.setDelayedCancellable(() -> channelFuture.cancel(false));
        return channelFuture;
    }

    private static ChannelFuture connectWithBootstrap(
            @Nullable SocketAddress localAddress, Object resolvedRemoteAddress, ReadOnlyTcpClientConfig config,
            EventLoop loop, BufferAllocator bufferAllocator, ChannelHandler handler) {
        final SocketAddress nettyresolvedRemoteAddress = toNettyAddress(resolvedRemoteAddress);
        Bootstrap bs = new Bootstrap();
        bs.group(loop);
        bs.channel(socketChannel(loop, nettyresolvedRemoteAddress.getClass()));
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
        return bs.connect(nettyresolvedRemoteAddress, localAddress);
    }

    private static ChannelFuture initFileDescriptorBasedChannel(
            ReadOnlyTcpClientConfig config, EventLoop loop, Channel channel,
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

    private static void connectFutureToListener(@Nullable SocketAddress localAddress, Object resolvedRemoteAddress,
                                                SingleSource.Subscriber<? super Channel> subscriber, Future<?> future) {
        future.addListener(f -> {
            Throwable cause = f.cause();
            if (cause != null) {
                if (cause instanceof ConnectTimeoutException) {
                    String msg = resolvedRemoteAddress instanceof FileDescriptorSocketAddress ? "Failed to register: " +
                            resolvedRemoteAddress : "Failed to connect: " + resolvedRemoteAddress + " (localAddress: " +
                            localAddress + ")";
                    cause = new io.servicetalk.client.api.ConnectTimeoutException(msg, cause);
                } else if (cause instanceof ConnectException) {
                    cause = new RetryableConnectException((ConnectException) cause);
                }
                subscriber.onError(cause);
            }
        });
    }
}
