/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.AUTO_CLOSE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.Matchers.contentEqualTo;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class ConnectionClosedAfterResponseTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerSocketChannel server;
    private final BlockingHttpClient client;
    private final AtomicReference<CharSequence> encodedResponse = new AtomicReference<>();
    private final CountDownLatch connectionClosedLatch = new CountDownLatch(1);

    public ConnectionClosedAfterResponseTest() {
        EventLoopAwareNettyIoExecutor eventLoopAwareNettyIoExecutor =
                toEventLoopAwareNettyIoExecutor(globalExecutionContext().ioExecutor());
        EventLoop loop = eventLoopAwareNettyIoExecutor.eventLoopGroup().next();

        ServerBootstrap bs = new ServerBootstrap();
        bs.group(loop);
        bs.channel(serverChannel(loop, InetSocketAddress.class));
        bs.childHandler(new ChannelInitializer() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(new HttpRequestDecoder());
                ch.pipeline().addLast(new HttpObjectAggregator(MAX_VALUE));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                        if (msg instanceof FullHttpRequest) {
                            ctx.writeAndFlush(ByteBufUtil.writeAscii(ctx.alloc(), encodedResponse.get()))
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                        ctx.fireChannelRead(msg);
                    }
                });
            }
        });
        bs.childOption(AUTO_READ, true);
        bs.childOption(ALLOW_HALF_CLOSURE, true);
        bs.childOption(AUTO_CLOSE, false);
        server = (ServerSocketChannel) bs.bind(localAddress(0))
                .syncUninterruptibly().channel();

        client = HttpClients.forSingleAddress(HostAndPort.of(server.localAddress()))
                .protocols(h1().allowChunkedResponseWithoutBody().build())
                .buildBlocking();
    }

    @After
    public void turnDown() throws Exception {
        try {
            client.closeGracefully();
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    @Test
    public void testWithoutPayload() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" + "\r\n");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        HttpResponse response = connection.request(request);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    public void testWithPayload() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-length: 5\r\n" +
                "Connection: close\r\n" + "\r\n" +
                "hello");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        HttpResponse response = connection.request(request);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo("5"));
        assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        connectionClosedLatch.await();
    }

    /**
     * Some old servers may close the connection right after sending meta-data if the payload body is empty.
     */
    @Test
    public void testChunkedWithoutBody() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Connection: close\r\n" + "\r\n");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        HttpResponse response = connection.request(request);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    public void testChunkedWithoutChunkData() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Connection: close\r\n" + "\r\n" +
                "5\r\n" +
                // no chunk data of size 5 (e.g. "hello\r\n") and no last-chunk: 0\r\n
                "\r\n");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        assertThrows(ClosedChannelException.class, () -> connection.request(request));
        connectionClosedLatch.await();
    }

    @Test
    public void testChunkedWithoutLastChunk() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Connection: close\r\n" + "\r\n" +
                "5\r\n" +
                "hello\r\n" +
                // no last-chunk: 0\r\n
                "\r\n");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        assertThrows(ClosedChannelException.class, () -> connection.request(request));
        connectionClosedLatch.await();
    }

    @Test
    public void testChunkedWithEmptyPayload() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Connection: close\r\n" + "\r\n" +
                "0\r\n" + "\r\n");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        HttpResponse response = connection.request(request);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    public void testChunkedWithPayload() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Connection: close\r\n" + "\r\n" +
                "5\r\n" +
                "hello\r\n" +
                "0\r\n" + "\r\n");

        HttpRequest request = client.get("/");
        ReservedBlockingHttpConnection connection = client.reserveConnection(request);
        // Wait until a server closes the connection:
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

        HttpResponse response = connection.request(request);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        connectionClosedLatch.await();
    }
}
