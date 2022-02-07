/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.VerificationTestUtils;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.AUTO_CLOSE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.H1SpecExceptions.Builder;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrematureClosureBeforeResponsePayloadBodyTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX = ExecutionContextExtension.immediate().setClassLevel(true);

    private ServerSocketChannel server;
    private BlockingHttpClient client;
    private ReservedBlockingHttpConnection connection;
    private final AtomicReference<CharSequence> encodedResponse = new AtomicReference<>();
    private final CountDownLatch connectionClosedLatch = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws Exception {
        EventLoopGroup eventLoopGroup = toEventLoopAwareNettyIoExecutor(SERVER_CTX.ioExecutor()).eventLoopGroup();
        ServerBootstrap bs = new ServerBootstrap();
        bs.group(eventLoopGroup);
        bs.channel(serverChannel(eventLoopGroup, InetSocketAddress.class));
        bs.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new HttpRequestDecoder());
                ch.pipeline().addLast(new HttpObjectAggregator(MAX_VALUE));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                        if (msg instanceof FullHttpRequest) {
                            ctx.writeAndFlush(writeAscii(ctx.alloc(), encodedResponse.get()))
                                .addListener(ChannelFutureListener.CLOSE);
                        }
                        release(msg);
                    }
                });
            }
        });
        bs.childOption(AUTO_READ, true);
        bs.childOption(ALLOW_HALF_CLOSURE, true);
        bs.childOption(AUTO_CLOSE, false);
        server = (ServerSocketChannel) bs.bind(localAddress(0))
            .sync().channel();

        client = HttpClients.forSingleAddress(HostAndPort.of(server.localAddress()))
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .protocols(h1()
                    .specExceptions(new Builder().allowPrematureClosureBeforePayloadBody(true).build())
                    .build())
                .buildBlocking();
        connection = client.reserveConnection(client.get("/"));
        connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();
    }

    @AfterEach
    void turnDown() throws Exception {
        try {
            client.closeGracefully();
        } finally {
            server.close().sync();
        }
    }

    @Test
    void notAllHeadersReceived() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n");   // no final CRLF after headers

        VerificationTestUtils.assertThrows(PrematureChannelClosureException.class, CloseEventObservedException.class,
                                           () -> connection.request(connection.get("/")));
        connectionClosedLatch.await();
    }

    @Test
    void noPayloadNoMessageLengthHeader() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Connection: close\r\n" + "\r\n");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    void payloadWithoutMessageLengthHeader() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "hello");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        connectionClosedLatch.await();
    }

    @Test
    void noPayloadWithContentLength() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" + "\r\n");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo("0"));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    void payloadWithContentLength() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 5\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "hello");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo("5"));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        connectionClosedLatch.await();
    }

    @Test
    void truncatedPayloadWithContentLength() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 5\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "he");   // not the whole payload body

        assertThrows(ClosedChannelException.class, () -> connection.request(connection.get("/")));
        connectionClosedLatch.await();
    }

    /**
     * Some old servers may close the connection right after sending meta-data if the payload body is empty.
     */
    @Test
    void chunkedWithoutBody() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    void chunkedWithEmptyPayload() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "0\r\n" + "\r\n");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        assertThat(response.payloadBody().readableBytes(), is(0));
        connectionClosedLatch.await();
    }

    @Test
    void chunkedWithPayload() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "5\r\n" +
                            "hello\r\n" +
                            "0\r\n" + "\r\n");

        HttpResponse response = connection.request(connection.get("/"));
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        assertThat(response.headers().get(TRANSFER_ENCODING), contentEqualTo(CHUNKED));
        assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        connectionClosedLatch.await();
    }

    @Test
    void chunkedWithSomeBytesAfterHeaders() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "5");   // can be a chunk-size, but impossible to interpret it correctly

        assertThrows(ClosedChannelException.class, () -> connection.request(connection.get("/")));
        connectionClosedLatch.await();
    }

    @Test
    void chunkedWithoutChunkData() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "5\r\n" +
                            // no chunk data of size 5 (e.g. "hello\r\n") and no last-chunk: 0\r\n
                            "\r\n");

        assertThrows(ClosedChannelException.class, () -> connection.request(connection.get("/")));
        connectionClosedLatch.await();
    }

    @Test
    void chunkedWithoutLastChunk() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "5\r\n" +
                            "hello\r\n" +
                            // no last-chunk: 0\r\n
                            "\r\n");

        assertThrows(ClosedChannelException.class, () -> connection.request(connection.get("/")));
        connectionClosedLatch.await();
    }

    @Test
    void chunkedWithoutFinalCRLF() throws Exception {
        encodedResponse.set("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" + "\r\n" +
                            "5\r\n" +
                            "hello\r\n" +
                            "0\r\n");   // no final CRLF

        assertThrows(ClosedChannelException.class, () -> connection.request(connection.get("/")));
        connectionClosedLatch.await();
    }
}
