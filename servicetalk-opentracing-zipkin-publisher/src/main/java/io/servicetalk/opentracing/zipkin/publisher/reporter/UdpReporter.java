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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.NettyFutureCompletable;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;

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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.Reporter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.RCVBUF_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Span} {@link Reporter} that will publish to a UDP listener with a configurable encoding {@link Codec}.
 */
public final class UdpReporter extends Component implements Reporter<Span>, AsyncCloseable {

    private static final Logger logger = LoggerFactory.getLogger(UdpReporter.class);

    private static final int DEFAULT_MAX_DATAGRAM_PACKET_SIZE = 2048; // 2Kib
    private static final MaxMessagesRecvByteBufAllocator DEFAULT_RECV_BUF_ALLOCATOR =
            new FixedRecvByteBufAllocator(DEFAULT_MAX_DATAGRAM_PACKET_SIZE);

    private final Channel channel;
    private final ListenableAsyncCloseable closeable;

    private UdpReporter(final Builder builder) {
        EventLoopGroup group;
        if (builder.ioExecutor != null) {
            group = toEventLoopAwareNettyIoExecutor(builder.ioExecutor).eventLoopGroup();
        } else {
            group = toEventLoopAwareNettyIoExecutor(globalExecutionContext().ioExecutor()).eventLoopGroup();
        }
        try {
            final Bootstrap bootstrap = buildBootstrap(group, builder.codec, builder.collectorAddress,
                    builder.loggerName);
            channel = bootstrap.bind(0).sync().channel();
        } catch (InterruptedException e) {
            currentThread().interrupt(); // Reset the interrupted flag.
            throw new IllegalStateException("Failed to create UDP client");
        } catch (Exception e) {
            logger.warn("Failed to create UDP client", e);
            throw e;
        }
        closeable = toAsyncCloseable(graceful -> new NettyFutureCompletable(channel::closeFuture));
    }

    /**
     * The serialization format for the zipkin write format data.
     */
    public enum Codec {
        JSON_V1(SpanBytesEncoder.JSON_V1), JSON_V2(SpanBytesEncoder.JSON_V2),
        THRIFT(SpanBytesEncoder.THRIFT), PROTO3(SpanBytesEncoder.PROTO3);

        final SpanBytesEncoder encoder;

        Codec(SpanBytesEncoder encoder) {
            this.encoder = encoder;
        }
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
        private String loggerName;

        /**
         * Create a new {@link UdpReporter.Builder} for a given collectorAddress.
         *
         * @param collectorAddress the collector SocketAddress
         */
        public Builder(SocketAddress collectorAddress) {
            this.collectorAddress = collectorAddress;
        }

        /**
         * Sets the {@link UdpReporter.Codec} to encode the Spans with.
         *
         * @param codec the codec to use for this span.
         * @return {@code this}
         */
        public Builder codec(Codec codec) {
            this.codec = requireNonNull(codec);
            return this;
        }

        /**
         * Sets an IoExecutor to use for writing to the datagram channel.
         *
         * @param ioExecutor IoExecutor to use to write with.
         * @return {@code this}
         */
        public Builder ioExecutor(IoExecutor ioExecutor) {
            this.ioExecutor = requireNonNull(ioExecutor);
            return this;
        }

        /**
         * Enables wire-logging for udp packets sent.
         * <p>
         * All wire events will be logged at {@link Level#TRACE TRACE} level.
         *
         * @param loggerName The name of the logger to log wire events.
         * @return {@code this}
         */
        public Builder enableWireLogging(String loggerName) {
            this.loggerName = loggerName;
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

    private Bootstrap buildBootstrap(EventLoopGroup group, Codec codec, SocketAddress collectorAddress,
                                     @Nullable String loggerName) {
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
                        if (loggerName != null) {
                            ch.pipeline().addLast(new LoggingHandler(loggerName, LogLevel.TRACE));
                        }
                        ch.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                                if (msg instanceof Span) {
                                    byte[] bytes = codec.encoder.encode((Span) msg);
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

    /**
     * Non-blocking report method.
     *
     * @param span the span to report
     */
    @Override
    public void report(final Span span) {
        if (!channel.isActive()) {
            throw new RuntimeException(StacklessClosedChannelException.newInstance(this.getClass(), "report"));
        }
        channel.writeAndFlush(span);
    }

    /**
     * Blocking close method delegates to {@link #closeAsync()}).
     */
    @Override
    public void close() {
        closeable.onClose();
    }

    /**
     * Marks the {@link UdpReporter} as closed to prevent accepting any more {@link Span}s.
     *
     * @return a {@link Completable} that is completed when close is done.
     */
    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }
}
