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
package io.servicetalk.opentracing.zipkin.publisher.reporter;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.logging.slf4j.internal.DefaultUserDataLoggerConfig;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;
import io.servicetalk.transport.netty.internal.WireLoggingInitializer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.RCVBUF_ALLOCATOR;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static zipkin2.CheckResult.OK;
import static zipkin2.CheckResult.failed;

/**
 * A {@link Span} {@link Reporter} that will publish to a UDP listener with a configurable encoding {@link Codec}.
 */
public final class UdpReporter extends Component implements Reporter<Span>, AsyncCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(UdpReporter.class);

    private static final int DEFAULT_MAX_DATAGRAM_PACKET_SIZE = 2048; // 2Kib
    private static final MaxMessagesRecvByteBufAllocator DEFAULT_RECV_BUF_ALLOCATOR =
            new FixedRecvByteBufAllocator(DEFAULT_MAX_DATAGRAM_PACKET_SIZE);

    private final Channel channel;
    private final AsyncCloseable closeable;

    private UdpReporter(final Builder builder) {
        EventLoopGroup group = toEventLoopAwareNettyIoExecutor(
                builder.ioExecutor != null ? builder.ioExecutor : globalExecutionContext().ioExecutor()
        ).eventLoopGroup();
        try {
            final Bootstrap bootstrap = buildBootstrap(group, builder.codec, builder.collectorAddress,
                    builder.wireLoggerConfig);
            channel = bootstrap.bind(0).sync().channel();
        } catch (InterruptedException e) {
            currentThread().interrupt(); // Reset the interrupted flag.
            throw new IllegalStateException("Failed to create UDP client");
        } catch (Exception e) {
            LOGGER.warn("Failed to create UDP client", e);
            throw e;
        }
        Executor executor = builder.executor != null ? builder.executor :
                globalExecutionContext().executor();
        closeable = new NettyChannelListenableAsyncCloseable(channel, executor);
    }

    /**
     * A builder to create a new {@link UdpReporter}.
     */
    public static final class Builder {
        private final SocketAddress collectorAddress;
        private Codec codec = Codec.JSON_V2;
        @Nullable
        private IoExecutor ioExecutor;
        @Nullable
        private Executor executor;
        @Nullable
        private UserDataLoggerConfig wireLoggerConfig;

        /**
         * Create a new {@link UdpReporter.Builder} for a given collectorAddress.
         *
         * @param collectorAddress the collector SocketAddress
         */
        public Builder(SocketAddress collectorAddress) {
            this.collectorAddress = collectorAddress;
        }

        /**
         * Sets the {@link Codec} to encode the Spans with.
         *
         * @param codec the codec to use for this span.
         * @return {@code this}
         */
        public Builder codec(Codec codec) {
            this.codec = requireNonNull(codec);
            return this;
        }

        /**
         * Sets an {@link IoExecutor} to use for writing to the datagram channel.
         *
         * @param ioExecutor IoExecutor to use to write with.
         * @return {@code this}
         */
        public Builder ioExecutor(IoExecutor ioExecutor) {
            this.ioExecutor = requireNonNull(ioExecutor);
            return this;
        }

        /**
         * Sets an {@link Executor} to use when required.
         *
         * @param executor {@link Executor} to use
         * @return {@code this}
         */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Enables wire-logging for UDP packets sent.
         *
         * @param loggerName The name of the logger to log wire events.
         * @param logLevel The level to log at.
         * @param logUserData {@code true} to include user data. {@code false} to exclude user data and log only
         * network events.
         * @return {@code this}
         */
        public Builder enableWireLogging(String loggerName, LogLevel logLevel, BooleanSupplier logUserData) {
            wireLoggerConfig = new DefaultUserDataLoggerConfig(loggerName, logLevel, logUserData);
            return this;
        }

        /**
         * Builds a new {@link UdpReporter} instance with this builder's options.
         * <p>
         * This method may block while the underlying UDP channel is being bound.
         *
         * @return a new {@link UdpReporter}
         */
        public UdpReporter build() {
            return new UdpReporter(this);
        }
    }

    private static Bootstrap buildBootstrap(EventLoopGroup group, Codec codec, SocketAddress collectorAddress,
                                            @Nullable UserDataLoggerConfig wireLoggerConfig) {
        if (!(collectorAddress instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("collectorAddress " + collectorAddress +
                    " is invalid for UDP");
        }
        return new Bootstrap()
                .group(group)
                .channel(datagramChannel(group))
                .option(RCVBUF_ALLOCATOR, DEFAULT_RECV_BUF_ALLOCATOR)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        if (wireLoggerConfig != null) {
                            new WireLoggingInitializer(wireLoggerConfig.loggerName(), wireLoggerConfig.logLevel(),
                                    wireLoggerConfig.logUserData()).init(ch);
                        }
                        ch.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                                if (msg instanceof Span) {
                                    byte[] bytes = codec.spanBytesEncoder().encode((Span) msg);
                                    ByteBuf buf = ctx.alloc().buffer(bytes.length).writeBytes(bytes);
                                    ctx.write(new DatagramPacket(buf, (InetSocketAddress) collectorAddress), promise);
                                } else {
                                    ctx.write(msg, promise);
                                }
                            }
                        });
                    }
                });
    }

    @Override
    public CheckResult check() {
        return channel.isActive() ? OK : failed(new IllegalStateException("Reporter is closed."));
    }

    @Override
    public void report(final Span span) {
        if (!channel.isActive()) {
            throw new RuntimeException(StacklessClosedChannelException.newInstance(this.getClass(), "report"));
        }
        channel.writeAndFlush(span);
    }

    @Override
    public void close() {
        awaitTermination(closeable.closeAsync().toFuture());
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }
}
