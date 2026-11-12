/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.TransportObserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Raw Netty HTTP/2 peers for tests that need to see or control individual frames, which neither the ServiceTalk client
 * nor the ServiceTalk server expose. It owns what it opens: {@link #close()} closes every channel, then the event loop.
 */
final class RawH2Peers implements AutoCloseable {

    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final List<Channel> channels = new ArrayList<>();

    /**
     * Binds a server that creates a new handler from {@code streamHandler} for every stream.
     */
    Channel bindServer(final Supplier<ChannelHandler> streamHandler) throws InterruptedException {
        return register(new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forServer().build(),
                                new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                    @Override
                                    protected void initChannel(final Http2StreamChannel stream) {
                                        stream.pipeline().addLast(streamHandler.get());
                                    }
                                }));
                    }
                }).bind(localAddress(0)).sync().channel());
    }

    /**
     * Connects a client to {@code address}.
     */
    Channel connect(final SocketAddress address) throws InterruptedException {
        return register(new Bootstrap().group(group).channel(NioSocketChannel.class).remoteAddress(address)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(),
                                new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                    }
                }).connect().sync().channel());
    }

    /**
     * Opens a stream on {@code connection}, and queues every frame it receives, including a reset.
     */
    Http2StreamChannel openStream(final Channel connection, final BlockingQueue<Http2StreamFrame> frames)
            throws Exception {
        return new Http2StreamChannelBootstrap(connection)
                .handler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx, final Http2StreamFrame frame) {
                        frames.add(frame);
                    }

                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                        // Netty delivers a received RST_STREAM to a stream channel as a user event.
                        if (evt instanceof Http2ResetFrame) {
                            frames.add((Http2ResetFrame) evt);
                        }
                        ctx.fireUserEventTriggered(evt);
                    }
                }).open().sync().get();
    }

    /**
     * Opens a stream on {@code connection} for a test that does not look at what it receives.
     */
    Http2StreamChannel openStream(final Channel connection) throws Exception {
        return openStream(connection, new LinkedBlockingQueue<>());
    }

    @Override
    public void close() throws InterruptedException {
        try {
            for (int i = channels.size() - 1; i >= 0; i--) {
                channels.get(i).close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 0, MILLISECONDS).sync();
        }
    }

    static Http2Headers requestHeaders(final String method, final String path) {
        return new DefaultHttp2Headers(false).method(method).path(path).scheme("http").authority("localhost");
    }

    /**
     * Collects frames into {@code received} until the one that ends the stream arrives, which is the final response.
     */
    static void awaitFinalResponse(final BlockingQueue<Http2StreamFrame> frames,
                                   final List<Http2StreamFrame> received) throws InterruptedException {
        for (;;) {
            // Bounded below the test timeout, so a failure can report what did arrive.
            final Http2StreamFrame frame = frames.poll(DEFAULT_TIMEOUT_SECONDS / 2, SECONDS);
            assertThat("No final response after frames " + received, frame, is(notNullValue()));
            received.add(frame);
            if (frame instanceof Http2HeadersFrame && ((Http2HeadersFrame) frame).isEndStream()) {
                return;
            }
        }
    }

    /**
     * Counts down {@code streamClosed} when a stream closes, however it closes.
     */
    static TransportObserver streamClosedObserver(final CountDownLatch streamClosed) {
        final StreamObserver streamObserver = new StreamObserver() {
            @Override
            public void streamClosed(final Throwable error) {
                streamClosed.countDown();
            }

            @Override
            public void streamClosed() {
                streamClosed.countDown();
            }
        };
        final MultiplexedObserver multiplexedObserver = new MultiplexedObserver() {
            @Override
            public StreamObserver onNewStream() {
                return streamObserver;
            }
        };
        final ConnectionObserver connectionObserver = new ConnectionObserver() {
            @Override
            public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
                return multiplexedObserver;
            }
        };
        return (localAddress, remoteAddress) -> connectionObserver;
    }

    private Channel register(final Channel channel) {
        channels.add(channel);
        return channel;
    }
}
