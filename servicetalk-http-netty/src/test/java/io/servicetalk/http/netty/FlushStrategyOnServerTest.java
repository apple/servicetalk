/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.tcp.netty.internal.TcpServerBinder;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpServerConfig;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.NettyHttpServer.initChannel;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

class FlushStrategyOnServerTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_RULE = ExecutorExtension.withCachedExecutor();
    private static final String USE_AGGREGATED_RESP = "aggregated-resp";
    private static final String USE_EMPTY_RESP_BODY = "empty-resp-body";

    private OutboundWriteEventsInterceptor interceptor;
    private HttpHeadersFactory headersFactory;

    private ServerContext serverContext;
    private BlockingHttpClient client;

    private enum Param {
        NO_OFFLOAD(noOffloadsStrategy()),
        DEFAULT(defaultStrategy()),
        OFFLOAD_ALL(customStrategyBuilder().offloadAll().build());
        private final HttpExecutionStrategy executionStrategy;
        Param(HttpExecutionStrategy executionStrategy) {
            this.executionStrategy = executionStrategy;
        }
    }

    private void setUp(final Param param) {
        this.interceptor = new OutboundWriteEventsInterceptor();
        this.headersFactory = DefaultHttpHeadersFactory.INSTANCE;

        final StreamingHttpService service = (ctx, request, responseFactory) -> {
            StreamingHttpResponse resp = responseFactory.ok();
            if (request.headers().get(USE_EMPTY_RESP_BODY) == null) {
                resp.payloadBody(from("Hello", "World"), appSerializerUtf8FixLen());
            }
            if (request.headers().get(USE_AGGREGATED_RESP) != null) {
                return resp.toResponse().map(HttpResponse::toStreamingResponse);
            }
            return succeeded(resp);
        };

        final DefaultHttpExecutionContext httpExecutionContext = new DefaultHttpExecutionContext(DEFAULT_ALLOCATOR,
                globalExecutionContext().ioExecutor(), EXECUTOR_RULE.executor(), param.executionStrategy);

        final ReadOnlyHttpServerConfig config = new HttpServerConfig().asReadOnly();
        final ConnectionObserver connectionObserver = config.tcpConfig().transportObserver().onNewConnection();
        final ReadOnlyTcpServerConfig tcpReadOnly = new TcpServerConfig().asReadOnly();

        try {
            serverContext = TcpServerBinder.bind(localAddress(0), tcpReadOnly, true,
                    httpExecutionContext, null,
                    (channel, observer) -> initChannel(channel, httpExecutionContext, config,
                            new TcpServerChannelInitializer(tcpReadOnly, connectionObserver)
                                    .andThen((channel1 -> channel1.pipeline().addLast(interceptor))), service,
                            true, connectionObserver),
                    connection -> connection.process(true))
                    .map(delegate -> new NettyHttpServer.NettyHttpServerContext(delegate, service)).toFuture().get();
        } catch (Exception e) {
            fail(e);
        }

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(h1Default())
                .buildBlocking();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoAggregatedResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoAggregatedEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true, true);
        assertFlushOnEnd();

        sendARequest(true, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void streamingResponsesFlushOnEach(final Param param) throws Exception {
        setUp(param);
        sendARequest(false);
        assertFlushOnEach();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoStreamingResponsesFlushOnEach(final Param param) throws Exception {
        setUp(param);
        sendARequest(false);
        assertFlushOnEach();

        sendARequest(false);
        assertFlushOnEach();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void streamingEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(false, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoStreamingEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(false, true);
        assertFlushOnEnd();

        sendARequest(false, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedAndThenStreamingResponse(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(false);
        assertFlushOnEach();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void streamingAndThenAggregatedResponse(final Param param) throws Exception {
        setUp(param);
        sendARequest(false);
        assertFlushOnEach();

        sendARequest(true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedStreamingEmptyResponse(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(false);
        assertFlushOnEach();

        sendARequest(false, true);
        assertFlushOnEnd();
    }

    private void assertFlushOnEnd() throws Exception {
        // aggregated response: headers, single (or empty) payload, and empty buffer instead of trailers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), greaterThan(0));
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
    }

    private void assertFlushOnEach() throws Exception {
        // headers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(1));
        // one chunk; chunk header payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(3));
        // one chunk; chunk header payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(3));
        // trailers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(1));
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
    }

    private void sendARequest(final boolean useAggregatedResp) throws Exception {
        sendARequest(useAggregatedResp, false);
    }

    private void sendARequest(boolean useAggregatedResp, boolean useEmptyRespBody) throws Exception {
        HttpHeaders headers = headersFactory.newHeaders();
        headers.set(TRANSFER_ENCODING, CHUNKED);
        if (useAggregatedResp) {
            headers.set(USE_AGGREGATED_RESP, "true");
        }
        if (useEmptyRespBody) {
            headers.set(USE_EMPTY_RESP_BODY, "true");
        }

        StreamingHttpRequest req = newTransportRequest(GET, "/", HTTP_1_1, headers, DEFAULT_ALLOCATOR,
                from(DEFAULT_ALLOCATOR.fromAscii("Hello"), headersFactory.newTrailers()), false,
                headersFactory);
        client.request(req.toRequest().toFuture().get());
    }

    static class OutboundWriteEventsInterceptor extends ChannelOutboundHandlerAdapter {

        private static final Object MSG = new Object();
        private static final Object FLUSH = new Object();

        private final BlockingQueue<Object> writeEvents = new LinkedBlockingDeque<>();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            writeEvents.add(MSG);
            ctx.write(msg, promise);
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) {
            writeEvents.add(FLUSH);
            ctx.flush();
        }

        int takeWritesTillFlush() throws Exception {
            int count = 0;
            for (;;) {
                Object evt = writeEvents.take();
                if (evt == FLUSH) {
                    return count;
                }

                count++;
            }
        }

        int pendingEvents() {
            return writeEvents.size();
        }
    }
}
