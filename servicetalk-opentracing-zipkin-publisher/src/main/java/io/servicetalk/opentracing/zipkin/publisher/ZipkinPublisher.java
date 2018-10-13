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
package io.servicetalk.opentracing.zipkin.publisher;

import io.servicetalk.opentracing.core.internal.InMemorySpan;
import io.servicetalk.opentracing.core.internal.InMemorySpanEventListener;
import io.servicetalk.opentracing.core.internal.Log;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.RCVBUF_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A publisher of {@link io.opentracing.Span}s to the zipkin transport.
 */
public final class ZipkinPublisher implements InMemorySpanEventListener, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(ZipkinPublisher.class);

    /**
     * The default maximum {@link DatagramPacket} size.
     */
    private static final int DEFAULT_MAX_DATAGRAM_PACKET_SIZE = 2048;
    private static final MaxMessagesRecvByteBufAllocator DEFAULT_RECV_BUF_ALLOCATOR =
            new FixedRecvByteBufAllocator(DEFAULT_MAX_DATAGRAM_PACKET_SIZE);

    private final Endpoint endpoint;
    private final EventLoopGroup group;
    private final Channel channel;

    /**
     * The serialization format for the zipkin write format data.
     */
    public enum Encoder {
        JSON_V1(SpanBytesEncoder.JSON_V1), JSON_V2(SpanBytesEncoder.JSON_V2),
        THRIFT(SpanBytesEncoder.THRIFT), PROTO3(SpanBytesEncoder.PROTO3);

        final SpanBytesEncoder encoder;

        Encoder(SpanBytesEncoder encoder) {
            this.encoder = encoder;
        }
    }

    /**
     * The networking transport to use.
     */
    public enum Transport {
        UDP {
            Bootstrap buildBootstrap(EventLoopGroup group, Encoder encoder, SocketAddress collectorAddress) {
                if (!(collectorAddress instanceof InetSocketAddress)) {
                    throw new IllegalArgumentException("collectorAddress " + collectorAddress +
                            " is invalid for transport " + this);
                }
                return new Bootstrap()
                        .group(group)
                        .channel(datagramChannel(group))
                        .option(RCVBUF_ALLOCATOR, DEFAULT_RECV_BUF_ALLOCATOR)
                        .handler(new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                                if (msg instanceof Span) {
                                    byte[] bytes = encoder.encoder.encode((Span) msg);
                                    ctx.write(new DatagramPacket(Unpooled.wrappedBuffer(bytes),
                                            (InetSocketAddress) collectorAddress));
                                } else {
                                    ctx.write(msg, promise);
                                }
                            }
                        });
            }
        };

        abstract Bootstrap buildBootstrap(EventLoopGroup group, Encoder encoder, SocketAddress collectorAddress);
    }

    /**
     * Builder for {@link ZipkinPublisher}.
     */
    public static final class Builder {
        private String serviceName;
        private SocketAddress collectorAddress;
        @Nullable
        private InetSocketAddress localAddress;
        private Encoder encoder = Encoder.JSON_V2;
        private Transport transport = Transport.UDP;

        /**
         * Create a new instance.
         * @param serviceName the service name.
         * @param collectorAddress the {@link SocketAddress} of the collector.
         */
        public Builder(String serviceName, SocketAddress collectorAddress) {
            this.serviceName = requireNonNull(serviceName);
            this.collectorAddress = requireNonNull(collectorAddress);
        }

        /**
         * Configures the service name.
         *
         * @param serviceName the service name.
         * @return this.
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = requireNonNull(serviceName);
            return this;
        }

        /**
         * Configures the collector address.
         *
         * @param collectorAddress the {@link SocketAddress} of the collector.
         * @return this.
         */
        public Builder collectorAddress(SocketAddress collectorAddress) {
            this.collectorAddress = requireNonNull(collectorAddress);
            return this;
        }

        /**
         * Configures the local address.
         *
         * @param localAddress the local {@link InetSocketAddress}.
         * @return this.
         */
        public Builder localAddress(InetSocketAddress localAddress) {
            this.localAddress = localAddress;
            return this;
        }

        /**
         * Configures the format.
         *
         * @param encoder the {@link Encoder} to use.
         * @return this.
         */
        public Builder encoder(Encoder encoder) {
            this.encoder = requireNonNull(encoder);
            return this;
        }

        /**
         * Configures the transport.
         *
         * @param transport the {@link Transport} to use.
         * @return this.
         */
        public Builder protocol(Transport transport) {
            this.transport = requireNonNull(transport);
            return this;
        }

        /**
         * Note that this may block while the underlying channel is bound/connected.
         * @return An interface which can publish tracing data using the zipkin API.
         */
        public ZipkinPublisher build() {
            return new ZipkinPublisher(serviceName, collectorAddress, localAddress, encoder, transport);
        }
    }

    private ZipkinPublisher(String serviceName,
                            SocketAddress collectorAddress,
                            @Nullable InetSocketAddress localAddress,
                            Encoder encoder,
                            Transport transport) {
        requireNonNull(serviceName);
        requireNonNull(collectorAddress);
        requireNonNull(encoder);
        requireNonNull(transport);

        endpoint = buildEndpoint(serviceName, localAddress);

        group = createEventLoopGroup(1, new DefaultThreadFactory("zipkin-publisher", true));
        try {
            final Bootstrap bootstrap = transport.buildBootstrap(group, encoder, collectorAddress);
            channel = bootstrap.bind(0).sync().channel();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Failed to create " + transport + " client");
        } catch (Exception e) {
            logger.warn("Failed to create {} client", transport, e);
            group.shutdownGracefully(0, 0, SECONDS);
            throw e;
        }
    }

    static Endpoint buildEndpoint(String serviceName, @Nullable InetSocketAddress localSocketAddress) {
        final Endpoint.Builder builder = Endpoint.newBuilder().serviceName(serviceName);
        if (localSocketAddress != null) {
            final InetAddress localAddress = localSocketAddress.getAddress();
            builder.ip(localAddress).port(localSocketAddress.getPort());
        }
        return builder.build();
    }

    @Override
    public void close() {
        channel.close();
        group.shutdownGracefully(0, 1, SECONDS);
    }

    @Override
    public void onSpanStarted(final InMemorySpan span) {
    }

    @Override
    public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
    }

    @Override
    public void onEventLogged(final InMemorySpan span, final long epochMicros, final Map<String, ?> fields) {
    }

    @Override
    public void onSpanFinished(final InMemorySpan span, long durationMicros) {
        final long begin = span.startEpochMicros();
        final long end = begin + durationMicros;

        Span.Builder builder = Span.newBuilder()
            .name(span.operationName())
            .traceId(span.traceIdHex())
            .id(span.spanId())
            .parentId(span.parentSpanIdHex())
            .timestamp(begin)
            .addAnnotation(end, "end")
            .localEndpoint(endpoint)
            .duration(durationMicros);
        span.tags().forEach((k, v) -> builder.putTag(k, v.toString()));
        List<Log> logs = span.logs();
        if (logs != null) {
            logs.forEach(log -> builder.addAnnotation(log.epochMicros(), log.eventName()));
        }
        Object type = span.tags().get(Tags.SPAN_KIND.getKey());
        if (Tags.SPAN_KIND_SERVER.equals(type)) {
            builder.kind(Span.Kind.SERVER);
        } else if (Tags.SPAN_KIND_CLIENT.equals(type)) {
            builder.kind(Span.Kind.CLIENT);
        }

        channel.writeAndFlush(builder.build());
    }
}
