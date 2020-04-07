/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UdpReporterTest {
    private static final int DEFAULT_MAX_DATAGRAM_PACKET_SIZE = 2048;
    private static final MaxMessagesRecvByteBufAllocator DEFAULT_RECV_BUF_ALLOCATOR =
            new FixedRecvByteBufAllocator(DEFAULT_MAX_DATAGRAM_PACKET_SIZE);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private EventLoopGroup group;

    @Before
    public void setUp() {
        group = new NioEventLoopGroup(2);
    }

    @After
    public void tearDown() {
        group.shutdownGracefully(0, 0, SECONDS);
    }

    @Test
    public void testJsonV1RoundTrip() throws Exception {
        testRoundTrip(UdpReporter.Codec.JSON_V1, SpanBytesDecoder.JSON_V1);
    }

    @Test
    public void testJsonV2RoundTrip() throws Exception {
        testRoundTrip(UdpReporter.Codec.JSON_V2, SpanBytesDecoder.JSON_V2);
    }

    @Test
    public void testThriftRoundTrip() throws Exception {
        testRoundTrip(UdpReporter.Codec.THRIFT, SpanBytesDecoder.THRIFT);
    }

    @Test
    public void testProto3RoundTrip() throws Exception {
        testRoundTrip(UdpReporter.Codec.PROTO3, SpanBytesDecoder.PROTO3);
    }

    private void testRoundTrip(UdpReporter.Codec codec, SpanBytesDecoder decoder) throws Exception {
        try (TestReceiver receiver = new TestReceiver(decoder)) {
            try (UdpReporter reporter = buildReporter((InetSocketAddress) receiver.channel.localAddress(), codec)) {
                Span span = Span.newBuilder()
                        .name("test operation")
                        .traceId("1234")
                        .id(2)
                        .timestamp(123456789L)
                        .duration(SECONDS.toMicros(1))
                        .putTag("stringKey", "string")
                        .putTag("boolKey", String.valueOf(true))
                        .putTag("shortKey", String.valueOf(Short.MAX_VALUE))
                        .putTag("intKey", String.valueOf(Integer.MAX_VALUE))
                        .putTag("longKey", String.valueOf(Long.MAX_VALUE))
                        .putTag("floatKey", String.valueOf(Float.MAX_VALUE))
                        .putTag("doubleKey", String.valueOf(Double.MAX_VALUE))
                        .addAnnotation(System.currentTimeMillis() * 1000, "some event happened")
                        .build();
                reporter.report(span);
            }

            Span span = receiver.queue.take();

            assertNotNull(span);
            assertEquals("test operation", span.name());
            assertEquals("0000000000001234", span.traceId());
            assertEquals("0000000000000002", span.id());
            assertEquals(123456789L, (long) span.timestamp());
            assertEquals(1000 * 1000, (long) span.duration());
            Map<String, String> tags = span.tags();
            assertEquals("string", tags.get("stringKey"));
            assertEquals(Boolean.TRUE.toString(), tags.get("boolKey"));
            assertEquals(String.valueOf(Short.MAX_VALUE), tags.get("shortKey"));
            assertEquals(String.valueOf(Integer.MAX_VALUE), tags.get("intKey"));
            assertEquals(String.valueOf(Long.MAX_VALUE), tags.get("longKey"));
            assertEquals(String.valueOf(Float.MAX_VALUE), tags.get("floatKey"));
            assertEquals(String.valueOf(Double.MAX_VALUE), tags.get("doubleKey"));
            assertTrue(span.annotations().stream().anyMatch(a -> a.value().equals("some event happened")));
        }
    }

    private UdpReporter buildReporter(InetSocketAddress remoteAddress, UdpReporter.Codec codec) {
        return new UdpReporter.Builder(remoteAddress)
                .codec(codec)
                .build();
    }

    final class TestReceiver implements Closeable {
        final BlockingQueue<Span> queue = new LinkedBlockingDeque<>();
        final Channel channel;

        TestReceiver(SpanBytesDecoder decoder) throws Exception {
            channel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, DEFAULT_RECV_BUF_ALLOCATOR)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                                    byte[] b = new byte[msg.content().readableBytes()];
                                    msg.content().readBytes(b);
                                    decoder.decode(b, queue);
                                }
                            });
                        }
                    })
                    .localAddress(localAddress(0))
                    .bind().sync().channel();
        }

        @Override
        public void close() {
            channel.close();
        }
    }
}
