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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

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
import io.netty.util.ReferenceCountUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.Matchers.contentEqualTo;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class MalformedDataAfterHttpMessageTest {

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached("server-io", "server-executor");
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached("client-io", "client-executor");

    private static final String CONTENT = "hello";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void afterResponse() throws Exception {
        String responseMsg = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + CONTENT.length() + "\r\n\r\n" +
                CONTENT +
                valueOf(new char[] {0x00, 0x00});   // malformed data at the end of the response msg

        ServerSocketChannel server = nettyServer(responseMsg);
        try (BlockingHttpClient client = stClient(server.localAddress())) {

            ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"));
            CountDownLatch connectionClosedLatch = new CountDownLatch(1);
            connection.connectionContext().onClose().whenFinally(connectionClosedLatch::countDown).subscribe();

            HttpResponse response = connection.request(connection.get("/"));
            assertThat(response.status(), is(OK));
            assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(CONTENT.length())));
            assertThat(response.payloadBody(textDeserializer()), equalTo(CONTENT));

            // Verify that the next request fails and connection gets closed:
            assertThrows(DecoderException.class, () -> connection.request(connection.get("/")));
            connectionClosedLatch.await();
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    @Test
    public void afterRequest() throws Exception {
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
            assertThat(response.payloadBody(textDeserializer()), equalTo(CONTENT));

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
                        ReferenceCountUtil.release(msg);
                    }
                });
            }
        });
        return (ServerSocketChannel) bs.bind(localAddress(0)).syncUninterruptibly().channel();
    }

    private static ServerContext stServer() throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
                .bufferAllocator(SERVER_CTX.bufferAllocator())
                .enableWireLogging("servicetalk-tests-wire-logger")
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody(request.payloadBody(textDeserializer()), textSerializer()));
    }

    private static BlockingHttpClient stClient(SocketAddress serverAddress) {
        return HttpClients.forSingleAddress(HostAndPort.of((InetSocketAddress) serverAddress))
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
                .bufferAllocator(CLIENT_CTX.bufferAllocator())
                .enableWireLogging("servicetalk-tests-wire-logger")
                .buildBlocking();
    }
}
