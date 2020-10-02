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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.FlushStrategyOnServerTest.OutboundWriteEventsInterceptor;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.ExecutorRule.newRule;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.NettyHttpServer.initChannel;
import static io.servicetalk.transport.netty.internal.CloseHandlers.forPipelinedServer;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class ServerRespondsOnClosingTest {

    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = newRule();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final OutboundWriteEventsInterceptor interceptor;
    private final EmbeddedChannel channel;
    private final NettyHttpServerConnection serverConnection;

    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch releaseResponse = new CountDownLatch(1);

    public ServerRespondsOnClosingTest() throws Exception {
        interceptor = new OutboundWriteEventsInterceptor();
        channel = new EmbeddedChannel(interceptor);

        DefaultHttpExecutionContext httpExecutionContext = new DefaultHttpExecutionContext(DEFAULT_ALLOCATOR,
                fromNettyEventLoop(channel.eventLoop()), EXECUTOR_RULE.executor(), defaultStrategy());
        ReadOnlyHttpServerConfig config = new HttpServerConfig().asReadOnly();
        ConnectionObserver connectionObserver = NoopConnectionObserver.INSTANCE;
        BlockingHttpService service = (ctx, request, responseFactory) -> {
            releaseResponse.await();
            final HttpResponse response = responseFactory.ok().payloadBody("Hello World", textSerializer());
            if (request.hasQueryParameter("serverShouldClose")) {
                response.addHeader(CONNECTION, CLOSE);
            }
            return response;
        };
        serverConnection = initChannel(channel, httpExecutionContext, config, new TcpServerChannelInitializer(
                config.tcpConfig(), connectionObserver),
                toStreamingHttpService(service, defaultStreamingInfluencer()).adaptor(), true,
                connectionObserver, forPipelinedServer(channel)).toFuture().get();
        serverConnection.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
        serverConnection.process(true);
    }

    @After
    public void tearDown() throws Exception {
        try {
            serverConnection.closeAsync().toFuture().get();
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    public void protocolClosingInboundPipelinedFirstInitiatesClosure() throws Exception {
        sendRequest("/first", true);
        sendRequest("/second", false);
        releaseResponse.countDown();
        // Verify that the server responded:
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // only first
        assertServerConnectionClosed();
    }

    @Test
    public void protocolClosingInboundPipelinedSecondInitiatesClosure() throws Exception {
        sendRequest("/first", false);
        sendRequest("/second", true);
        releaseResponse.countDown();
        // Verify that the server responded:
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // first
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // second
        assertServerConnectionClosed();
    }

    @Test
    public void protocolClosingOutboundPipelinedFirstInitiatesClosure() throws Exception {
        sendRequest("/first?serverShouldClose=true", true);
        sendRequest("/second", false);
        releaseResponse.countDown();
        // Verify that the server responded:
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // only first
        assertServerConnectionClosed();
    }

    @Test
    public void protocolClosingOutboundPipelinedSecondInitiatesClosure() throws Exception {
        sendRequest("/first", false);
        sendRequest("/second?serverShouldClose=true", true);
        releaseResponse.countDown();
        // Verify that the server responded:
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // first
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // second
        assertServerConnectionClosed();
    }

    @Test
    public void gracefulClosurePipelined() throws Exception {
        sendRequest("/first", false);
        sendRequest("/second", false);
        serverConnection.closeAsyncGracefully().subscribe();
        serverConnection.onClosing().toFuture().get();
        releaseResponse.countDown();
        // Verify that the server responded:
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // first
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3)); // second
        // Simulate ChannelInputShutdownReadComplete (FIN) on EmbeddedChannel to complete half-closure:
        channel.pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
        assertServerConnectionClosed();
    }

    private void sendRequest(String requestTarget, boolean addCloseHeader) {
        channel.writeInbound(writeAscii(PooledByteBufAllocator.DEFAULT, "GET " + requestTarget + " HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-length: 0\r\n" +
                (addCloseHeader ? "Connection: close\r\n" : "") +
                "\r\n"));
    }

    private void assertServerConnectionClosed() throws Exception {
        serverConnectionClosed.await();
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
        assertThat("Channel not closed.", channel.isOpen(), is(false));
    }
}
