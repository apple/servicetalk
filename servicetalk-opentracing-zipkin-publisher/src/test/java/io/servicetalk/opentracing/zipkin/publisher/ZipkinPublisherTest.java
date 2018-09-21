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

import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.opentracing.core.AsyncContextInMemoryScopeManager;
import io.servicetalk.opentracing.core.internal.DefaultInMemoryTracer;
import io.servicetalk.opentracing.core.internal.InMemoryScope;
import io.servicetalk.opentracing.core.internal.InMemorySpan;
import io.servicetalk.opentracing.core.internal.InMemoryTracer;
import io.servicetalk.opentracing.zipkin.publisher.ZipkinPublisher.Encoder;

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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.opentracing.zipkin.publisher.ZipkinPublisher.Encoder.JSON_V1;
import static java.net.InetAddress.getLocalHost;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ZipkinPublisherTest {
    private static final int DEFAULT_MAX_DATAGRAM_PACKET_SIZE = 2048;
    private static final MaxMessagesRecvByteBufAllocator DEFAULT_RECV_BUF_ALLOCATOR =
            new FixedRecvByteBufAllocator(DEFAULT_MAX_DATAGRAM_PACKET_SIZE);
    private static final int DUMMY_PORT = Optional.ofNullable(System.getenv("DUMMY_PORT"))
            .map(Integer::parseInt).orElse(54321);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private EventLoopGroup group;
    private InMemoryTracer tracer;

    @Before
    public void setUp() {
        group = new NioEventLoopGroup(2);
        AsyncContextMap.Key<InMemoryScope> key = AsyncContextMap.Key.newKeyWithDebugToString("tracing");
        tracer = new DefaultInMemoryTracer.Builder(new AsyncContextInMemoryScopeManager(key)).persistLogs(true).build();
    }

    @After
    public void tearDown() {
        group.shutdownGracefully(0, 0, SECONDS);
    }

    @Test
    public void testJsonV1RoundTrip() throws Exception {
        testRoundTrip(JSON_V1, SpanBytesDecoder.JSON_V1);
    }

    @Test
    public void testJsonV2RoundTrip() throws Exception {
        testRoundTrip(Encoder.JSON_V2, SpanBytesDecoder.JSON_V2);
    }

    @Test
    public void testThriftRoundTrip() throws Exception {
        testRoundTrip(Encoder.THRIFT, SpanBytesDecoder.THRIFT);
    }

    @Test
    public void testProto3RoundTrip() throws Exception {
        testRoundTrip(Encoder.PROTO3, SpanBytesDecoder.PROTO3);
    }

    @Test
    public void testNoReceiver() throws Exception {
        try (ZipkinPublisher publisher = buildPublisher(new InetSocketAddress(getLocalHost(), DUMMY_PORT), JSON_V1)) {
            InMemorySpan span = tracer.buildSpan("test operation")
                    .withTag("stringKey", "string")
                    .withTag("boolKey", true)
                    .withTag("shortKey", Short.MAX_VALUE)
                    .withTag("intKey", Integer.MAX_VALUE)
                    .withTag("longKey", Long.MAX_VALUE)
                    .withTag("floatKey", Float.MAX_VALUE)
                    .withTag("doubleKey", Double.MAX_VALUE)
                    .start();
            span.log("some event happened");
            span.finish();

            for (int i = 0; i < 10000; ++i) {
                publisher.onSpanFinished(span, 1000 * 1000);
            }

            // Wait a little time, otherwise we may shutdown the channel too quickly
            Thread.sleep(1000);
        }
    }

    private void testRoundTrip(Encoder encoder, SpanBytesDecoder decoder) throws Exception {
        try (TestReceiver receiver = new TestReceiver(decoder)) {
            try (ZipkinPublisher publisher = buildPublisher((InetSocketAddress) receiver.channel.localAddress(),
                    encoder)) {
                InMemorySpan span = tracer.buildSpan("test operation")
                        .withTag("stringKey", "string")
                        .withTag("boolKey", true)
                        .withTag("shortKey", Short.MAX_VALUE)
                        .withTag("intKey", Integer.MAX_VALUE)
                        .withTag("longKey", Long.MAX_VALUE)
                        .withTag("floatKey", Float.MAX_VALUE)
                        .withTag("doubleKey", Double.MAX_VALUE)
                        .start();
                span.log("some event happened");
                span.finish();
                publisher.onSpanFinished(span, 1000 * 1000);
            }
            Span span = receiver.queue.take();

            assertNotNull(span);
            assertEquals("test operation", span.name());
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

    private ZipkinPublisher buildPublisher(InetSocketAddress remoteAddress, Encoder encoder) {
        return new ZipkinPublisher.Builder("test", remoteAddress)
                .withEncoder(encoder)
                .withProtocol(ZipkinPublisher.Transport.UDP)
                .build();
    }

    final class TestReceiver implements AutoCloseable {
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
                    .localAddress(new InetSocketAddress(getLocalHost(), 0))
                    .bind().sync().channel();
        }

        @Override
        public void close() {
            channel.close();
        }
    }
}
