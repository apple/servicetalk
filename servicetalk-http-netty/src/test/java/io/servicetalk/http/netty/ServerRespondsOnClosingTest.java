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

import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.netty.internal.EmbeddedDuplexChannel;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.NettyHttpServer.initChannel;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class ServerRespondsOnClosingTest {

    private static final HttpResponseFactory RESPONSE_FACTORY = new DefaultHttpResponseFactory(
            DefaultHttpHeadersFactory.INSTANCE, DEFAULT_ALLOCATOR, HTTP_1_1);
    private static final String RESPONSE_PAYLOAD_BODY = "Hello World";

    private final EmbeddedDuplexChannel channel;
    private final NettyHttpServerConnection serverConnection;
    private final Queue<Exchange> requests = new ArrayDeque<>();

    ServerRespondsOnClosingTest() throws Exception {
        channel = new EmbeddedDuplexChannel(false);
        DefaultHttpExecutionContext httpExecutionContext = new DefaultHttpExecutionContext(DEFAULT_ALLOCATOR,
                fromNettyEventLoop(channel.eventLoop()), immediate(), noOffloadsStrategy());
        final HttpServerConfig httpServerConfig = new HttpServerConfig();
        httpServerConfig.tcpConfig().enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true);
        ReadOnlyHttpServerConfig config = httpServerConfig.asReadOnly();
        ConnectionObserver connectionObserver = NoopConnectionObserver.INSTANCE;
        HttpService service = (ctx, request, responseFactory) -> {
            Processor<HttpResponse, HttpResponse> responseProcessor = newSingleProcessor();
            requests.add(new Exchange(request, responseProcessor));
            return fromSource(responseProcessor);
        };
        serverConnection = initChannel(channel, httpExecutionContext, config, new TcpServerChannelInitializer(
                config.tcpConfig(), connectionObserver),
                toStreamingHttpService(service, strategy -> strategy).adaptor(), true,
                connectionObserver).toFuture().get();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            serverConnection.closeAsyncGracefully().toFuture().get();
        } finally {
            channel.finishAndReleaseAll();
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    void protocolClosingInboundPipelinedFirstInitiatesClosure() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first", true);
        // The following request after "Connection: close" header violates the spec, but we want to verify that server
        // discards those requests and do not respond to them:
        sendRequest("/second", false);
        handleRequests();
        verifyResponse("/first");
        assertServerConnectionClosed();
    }

    @Test
    void protocolClosingInboundPipelinedSecondInitiatesClosure() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first", false);
        sendRequest("/second", true);
        handleRequests();
        verifyResponse("/first");
        verifyResponse("/second");
        assertServerConnectionClosed();
    }

    @Test
    void protocolClosingOutboundPipelinedFirstInitiatesClosure() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first?serverShouldClose=true", false);
        sendRequest("/second", false);
        handleRequests();
        verifyResponse("/first");
        // Second request is discarded
        respondWithFIN();
        assertServerConnectionClosed();
    }

    @Test
    void protocolClosingOutboundPipelinedSecondInitiatesClosure() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first", false);
        sendRequest("/second?serverShouldClose=true", false);
        handleRequests();
        verifyResponse("/first");
        verifyResponse("/second");
        respondWithFIN();
        assertServerConnectionClosed();
    }

    @Test
    void gracefulClosurePipelined() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first", false);
        sendRequest("/second", false);
        serverConnection.closeAsyncGracefully().subscribe();
        serverConnection.onClosing().toFuture().get();
        sendRequest("/third", false);   // should be discarded
        handleRequests();
        verifyResponse("/first");
        verifyResponse("/second");
        respondWithFIN();
        assertServerConnectionClosed();
    }

    @Test
    void gracefulClosurePipelinedDiscardPartialRequest() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first", false);
        // Send only initial line with CRLF that should hang in ByteToMessage cumulation buffer and will be discarded:
        channel.writeInbound(writeAscii(PooledByteBufAllocator.DEFAULT, "GET /second HTTP/1.1"));
        serverConnection.closeAsyncGracefully().subscribe();
        serverConnection.onClosing().toFuture().get();
        handleRequests();
        verifyResponse("/first");
        respondWithFIN();
        assertServerConnectionClosed();
    }

    @Test
    void gracefulClosurePipelinedFirstResponseClosesConnection() throws Exception {
        serverConnection.process(true); // Start request processing (read and write)
        sendRequest("/first?serverShouldClose=true", false);    // PROTOCOL_CLOSING_OUTBOUND
        sendRequest("/second", false);
        serverConnection.closeAsyncGracefully().subscribe();
        serverConnection.onClosing().toFuture().get();
        sendRequest("/third", false);   // should be discarded
        handleRequests();
        verifyResponse("/first");
        respondWithFIN();
        assertServerConnectionClosed();
    }

    @Test
    void protocolClosingInboundBeforeProcessingStarts() throws Exception {
        sendRequest("/first", true);
        // Start request processing (read and write) after request was received:
        serverConnection.process(true);
        handleRequests();
        verifyResponse("/first");
        assertServerConnectionClosed();
    }

    @Test
    void gracefulClosureBeforeProcessingStarts() throws Exception {
        sendRequest("/first", false);
        serverConnection.closeAsyncGracefully().subscribe();

        // Start request processing (read and write) after request was received:
        serverConnection.process(true);
        handleRequests();
        verifyResponse("/first");
        respondWithFIN();
        assertServerConnectionClosed();
    }

    private void sendRequest(String requestTarget, boolean addCloseHeader) {
        channel.writeInbound(writeAscii(PooledByteBufAllocator.DEFAULT, "GET " + requestTarget + " HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-length: 0\r\n" +
                (addCloseHeader ? "Connection: close\r\n" : "") +
                "\r\n"));
    }

    private void handleRequests() {
        Exchange exchange;
        while ((exchange = requests.poll()) != null) {
            HttpRequest request = exchange.request;
            HttpResponse response = RESPONSE_FACTORY.ok()
                    .setHeader("Request-Path", request.path())
                    .payloadBody(RESPONSE_PAYLOAD_BODY, textSerializerUtf8());
            if (request.hasQueryParameter("serverShouldClose")) {
                response.setHeader(CONNECTION, CLOSE);
            }
            exchange.responseProcessor.onSuccess(response);
        }
    }

    private void verifyResponse(String requestPath) {
        // 3 items expected: meta-data, payload body, trailers
        assertThat("Not a full response was written", channel.outboundMessages(), hasSize(greaterThanOrEqualTo(3)));
        ByteBuf metaData = channel.readOutbound();
        assertThat("Unexpected response meta-data", metaData.toString(US_ASCII), containsString(requestPath));
        ByteBuf payloadBody = channel.readOutbound();
        assertThat("Unexpected response payload body", payloadBody.toString(US_ASCII), equalTo(RESPONSE_PAYLOAD_BODY));
        ByteBuf trailers = channel.readOutbound();
        assertThat("Unexpected response trailers object", trailers.readableBytes(), is(0));
    }

    private void respondWithFIN() {
        assertThat("Server did not shutdown output", channel.isOutputShutdown(), is(true));
        channel.shutdownInput().syncUninterruptibly();    // simulate FIN from the client
    }

    private void assertServerConnectionClosed() throws Exception {
        serverConnection.onClose().toFuture().get();
        assertThat("Unexpected writes", channel.outboundMessages(), hasSize(0));
        assertThat("Channel is not closed", channel.isOpen(), is(false));
    }

    private static final class Exchange {
        final HttpRequest request;
        final Processor<HttpResponse, HttpResponse> responseProcessor;

        Exchange(HttpRequest request, Processor<HttpResponse, HttpResponse> responseProcessor) {
            this.request = request;
            this.responseProcessor = responseProcessor;
        }
    }
}
