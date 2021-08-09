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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.api.HostAndPort.of;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MalformedDataAfterHttpMessageTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached("server-io", "server-executor");
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor");

    private static final String CONTENT = "hello";

    @Test
    void afterResponse() throws Exception {
        String responseMsg = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + CONTENT.length() + "\r\n\r\n" +
                             CONTENT +
                             valueOf(new char[]{0x00, 0x00});   // malformed data at the end of the response msg

        ServerSocketChannel server = nettyServer(responseMsg);
        try (BlockingHttpClient client = stClient(server.localAddress())) {

            ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"));
            CountDownLatch connectionClosedLatch = new CountDownLatch(1);
            connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

            HttpResponse response = connection.request(connection.get("/"));
            assertThat(response.status(), is(OK));
            assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(CONTENT.length())));
            assertThat(response.payloadBody(textSerializerUtf8()), equalTo(CONTENT));

            // Verify that the next request fails and connection gets closed:
            assertThrows(DecoderException.class, () -> connection.request(connection.get("/")));
            connectionClosedLatch.await();
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    @Test
    void afterRequest() throws Exception {
        try (ServerContext server = stServer();
             BlockingHttpClient client = stClient(server.listenAddress())) {

            ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"));
            CountDownLatch connectionClosedLatch = new CountDownLatch(1);
            connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

            Buffer malformedBody = client.executionContext().bufferAllocator().fromAscii(CONTENT)
                .writeShort(0); // malformed data at the end of the request msg
            HttpResponse response = connection.request(connection.post("/")
                                                           .setHeader(CONTENT_LENGTH, valueOf(CONTENT.length()))
                                                           .setHeader(CONTENT_TYPE, TEXT_PLAIN)
                                                           .payloadBody(malformedBody));
            assertThat(response.status(), is(OK));
            assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(CONTENT.length())));
            assertThat(response.payloadBody(textSerializerUtf8()), equalTo(CONTENT));

            // Server should close the connection:
            connectionClosedLatch.await();
        }
    }

    private static ServerSocketChannel nettyServer(String response) {
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
                            ctx.writeAndFlush(writeAscii(ctx.alloc(), response));
                        }
                        release(msg);
                    }
                });
            }
        });
        return (ServerSocketChannel) bs.bind(localAddress(0)).syncUninterruptibly().channel();
    }

    private static ServerContext stServer() throws Exception {
        return forAddress(localAddress(0))
            .ioExecutor(SERVER_CTX.ioExecutor())
            .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
            .bufferAllocator(SERVER_CTX.bufferAllocator())
            .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
            .listenBlockingAndAwait((ctx, request, responseFactory) ->
                                        responseFactory.ok().payloadBody(request.payloadBody(textSerializerUtf8()),
                                                textSerializerUtf8()));
    }

    private static BlockingHttpClient stClient(SocketAddress serverAddress) {
        return forSingleAddress(of((InetSocketAddress) serverAddress))
            .ioExecutor(CLIENT_CTX.ioExecutor())
            .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
            .bufferAllocator(CLIENT_CTX.bufferAllocator())
            .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
            .buildBlocking();
    }
}
