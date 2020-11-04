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
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.NettyHttpServer.NettyHttpServerConnection;
import io.servicetalk.tcp.netty.internal.TcpServerChannelInitializer;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.netty.internal.EmbeddedDuplexChannel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.ExecutorRule.newRule;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.ContentCodings.identity;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static io.servicetalk.http.netty.NettyHttpServer.initChannel;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class FlushStrategyOnServerTest {

    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = newRule();

    private final OutboundWriteEventsInterceptor interceptor;
    private final EmbeddedDuplexChannel channel;
    private final AtomicBoolean useAggregatedResponse;
    private final NettyHttpServerConnection serverConnection;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final HttpHeadersFactory headersFactory;

    private enum Param {
        NO_OFFLOAD(noOffloadsStrategy()),
        DEFAULT(defaultStrategy()),
        OFFLOAD_ALL(customStrategyBuilder().offloadAll().build());
        private final HttpExecutionStrategy executionStrategy;
        Param(HttpExecutionStrategy executionStrategy) {
            this.executionStrategy = executionStrategy;
        }
    }

    public FlushStrategyOnServerTest(final Param param) throws Exception {
        interceptor = new OutboundWriteEventsInterceptor();
        channel = new EmbeddedDuplexChannel(false, interceptor);
        useAggregatedResponse = new AtomicBoolean();
        StreamingHttpService service = (ctx, request, responseFactory) -> {
            StreamingHttpResponse resp = responseFactory.ok().payloadBody(from("Hello", "World"), textSerializer());
            if (useAggregatedResponse.get()) {
                return resp.toResponse().map(HttpResponse::toStreamingResponse);
            }
            return succeeded(resp);
        };

        DefaultHttpExecutionContext httpExecutionContext = new DefaultHttpExecutionContext(DEFAULT_ALLOCATOR,
                fromNettyEventLoop(channel.eventLoop()), EXECUTOR_RULE.executor(), param.executionStrategy);

        final ReadOnlyHttpServerConfig config = new HttpServerConfig().asReadOnly();
        final ConnectionObserver connectionObserver = config.tcpConfig().transportObserver().onNewConnection();
        serverConnection = initChannel(channel, httpExecutionContext, config,
                new TcpServerChannelInitializer(config.tcpConfig(), connectionObserver), service, true,
                connectionObserver, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER)
                .toFuture().get();
        serverConnection.process(true);
        headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    }

    @Parameters(name = "{index}: strategy = {0}")
    public static Param[][] data() {
        return Arrays.stream(Param.values()).map(s -> new Param[]{s}).toArray(Param[][]::new);
    }

    @After
    public void tearDown() throws Exception {
        try {
            serverConnection.closeAsyncGracefully().toFuture().get();
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    public void aggregatedResponsesFlushOnEnd() throws Exception {
        useAggregatedResponse.set(true);
        sendARequest();
        assertAggregatedResponseWrite();
    }

    @Test
    public void twoAggregatedResponsesFlushOnEnd() throws Exception {
        useAggregatedResponse.set(true);
        sendARequest();
        assertAggregatedResponseWrite();

        useAggregatedResponse.set(true);
        sendARequest();
        assertAggregatedResponseWrite();
    }

    @Test
    public void twoStreamingResponsesFlushOnEach() throws Exception {
        useAggregatedResponse.set(false);
        sendARequest();
        verifyStreamingResponseWrite();

        useAggregatedResponse.set(false);
        sendARequest();
        verifyStreamingResponseWrite();
    }

    @Test
    public void streamingResponsesFlushOnEach() throws Exception {
        useAggregatedResponse.set(false);
        sendARequest();
        verifyStreamingResponseWrite();
    }

    @Test
    public void aggregatedAndThenStreamingResponse() throws Exception {
        useAggregatedResponse.set(true);
        sendARequest();
        assertAggregatedResponseWrite();

        useAggregatedResponse.set(false);
        sendARequest();
        verifyStreamingResponseWrite();
    }

    @Test
    public void streamingAndThenAggregatedResponse() throws Exception {
        useAggregatedResponse.set(false);
        sendARequest();
        verifyStreamingResponseWrite();

        useAggregatedResponse.set(true);
        sendARequest();
        assertAggregatedResponseWrite();
    }

    private void assertAggregatedResponseWrite() throws Exception {
        // aggregated response; headers, single payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3));
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
    }

    private void verifyStreamingResponseWrite() throws Exception {
        // headers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(1));
        // one chunk; chunk header payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3));
        // one chunk; chunk header payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(3));
        // trailers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), hasSize(1));
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
    }

    private void sendARequest() throws Exception {
        StreamingHttpRequest req = newTransportRequest(GET, "/", HTTP_1_1,
                headersFactory.newHeaders().set(TRANSFER_ENCODING, CHUNKED), identity(), DEFAULT_ALLOCATOR,
                from(DEFAULT_ALLOCATOR.fromAscii("Hello"), headersFactory.newTrailers()),
                headersFactory);
        channel.writeInbound(req);
        for (Object item : req.payloadBodyAndTrailers().toFuture().get()) {
            channel.writeInbound(item);
        }
    }

    static class OutboundWriteEventsInterceptor extends ChannelOutboundHandlerAdapter {

        private static final Object FLUSH = new Object();

        private final BlockingQueue<Object> writeEvents = new LinkedBlockingDeque<>();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            writeEvents.add(msg);
            ctx.write(msg, promise);
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) {
            writeEvents.add(FLUSH);
            ctx.flush();
        }

        Collection<Object> takeWritesTillFlush() throws Exception {
            List<Object> writes = new ArrayList<>();
            for (;;) {
                Object evt = writeEvents.take();
                if (evt == FLUSH) {
                    return writes;
                }
                writes.add(evt);
            }
        }

        int pendingEvents() {
            return writeEvents.size();
        }
    }
}
