/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.ExecutorRule.newRule;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.NettyHttpServer.initChannel;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class FlushStrategyOnServerTest {

    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = newRule();
    public static final String USE_AGGREGATED_RESP = "aggregated-resp";
    public static final String USE_EMPTY_RESP_BODY = "empty-resp-body";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final Param param;
    private final OutboundWriteEventsInterceptor interceptor;
    private final HttpHeadersFactory headersFactory;

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

    public FlushStrategyOnServerTest(final Param param) {
        this.param = param;
        this.interceptor = new OutboundWriteEventsInterceptor();
        this.headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    }

    @Parameters(name = "{index}: strategy = {0}")
    public static Param[][] data() {
        return Arrays.stream(Param.values()).map(s -> new Param[]{s}).toArray(Param[][]::new);
    }

    @Before
    public void setup() throws Exception {
        final StreamingHttpService service = (ctx, request, responseFactory) -> {
            StreamingHttpResponse resp = responseFactory.ok();
            if (request.headers().get(USE_EMPTY_RESP_BODY) == null) {
                resp.payloadBody(from("Hello", "World"), textSerializer());
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

        serverContext = TcpServerBinder.bind(localAddress(0), tcpReadOnly, true,
                httpExecutionContext, null,
                (channel, observer) -> initChannel(channel, httpExecutionContext, config,
                        new TcpServerChannelInitializer(tcpReadOnly, connectionObserver)
                                .andThen((channel1 -> channel1.pipeline().addLast(interceptor))), service,
                        true, connectionObserver),
                connection -> connection.process(true))
                .map(delegate -> new NettyHttpServer.NettyHttpServerContext(delegate, service)).toFuture().get();

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(h1Default())
                .buildBlocking();
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @Test
    public void aggregatedResponsesFlushOnEnd() throws Exception {
        sendARequest(true);
        assertFlushOnEnd();
    }

    @Test
    public void twoAggregatedResponsesFlushOnEnd() throws Exception {
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(true);
        assertFlushOnEnd();
    }

    @Test
    public void aggregatedEmptyResponsesFlushOnEnd() throws Exception {
        sendARequest(true, true);
        assertFlushOnEnd();
    }

    @Test
    public void twoAggregatedEmptyResponsesFlushOnEnd() throws Exception {
        sendARequest(true, true);
        assertFlushOnEnd();

        sendARequest(true, true);
        assertFlushOnEnd();
    }

    @Test
    public void streamingResponsesFlushOnEach() throws Exception {
        sendARequest(false);
        assertFlushOnEach();
    }

    @Test
    public void twoStreamingResponsesFlushOnEach() throws Exception {
        sendARequest(false);
        assertFlushOnEach();

        sendARequest(false);
        assertFlushOnEach();
    }

    @Test
    public void streamingEmptyResponsesFlushOnEnd() throws Exception {
        sendARequest(false, true);
        assertFlushOnEnd();
    }

    @Test
    public void twoStreamingEmptyResponsesFlushOnEnd() throws Exception {
        sendARequest(false, true);
        assertFlushOnEnd();

        sendARequest(false, true);
        assertFlushOnEnd();
    }

    @Test
    public void aggregatedAndThenStreamingResponse() throws Exception {
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(false);
        assertFlushOnEach();
    }

    @Test
    public void streamingAndThenAggregatedResponse() throws Exception {
        sendARequest(false);
        assertFlushOnEach();

        sendARequest(true);
        assertFlushOnEnd();
    }

    @Test
    public void aggregatedStreamingEmptyResponse() throws Exception {
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
