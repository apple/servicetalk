/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpScPublisherProcessor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DefaultHttpCookiePair;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.H2ClientPriorKnowledgeFeatureParityTest.TestExecutionStrategy.CACHED;
import static io.servicetalk.http.netty.H2ClientPriorKnowledgeFeatureParityTest.TestExecutionStrategy.NO_OFFLOAD;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.lang.Thread.NORM_PRIORITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class H2ClientPriorKnowledgeFeatureParityTest {
    private static final String EXPECT_FAIL_HEADER = "please_fail_expect";
    private static final AsyncContextMap.Key<String> K1 = AsyncContextMap.Key.newKey("k1");
    private static final AsyncContextMap.Key<String> K2 = AsyncContextMap.Key.newKey("k2");
    private static final AsyncContextMap.Key<String> K3 = AsyncContextMap.Key.newKey("k3");

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private EventLoopGroup serverEventLoopGroup;
    @Nullable
    private Channel serverAcceptorChannel;
    @Nullable
    private ServerContext h1ServerContext;
    private HttpExecutionStrategy clientExecutionStrategy;
    private boolean h2PriorKnowledge;

    public H2ClientPriorKnowledgeFeatureParityTest(TestExecutionStrategy strategy, boolean h2PriorKnowledge) {
        clientExecutionStrategy = strategy.executorSupplier.get();
        serverEventLoopGroup = createEventLoopGroup(2, new DefaultThreadFactory("server-io", true, NORM_PRIORITY));
        this.h2PriorKnowledge = h2PriorKnowledge;
    }

    @Parameterized.Parameters(name = "client={0}, h2PriorKnowledge={1}")
    public static Collection<Object[]> clientExecutors() {
        return asList(new Object[]{NO_OFFLOAD, true},
                new Object[]{NO_OFFLOAD, false},
                new Object[]{CACHED, true},
                new Object[]{CACHED, false});
    }

    @After
    public void teardown() throws Exception {
        if (serverAcceptorChannel != null) {
            serverAcceptorChannel.close();
        }
        if (h1ServerContext != null) {
            h1ServerContext.close();
        }
        serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS);
        Executor executor = clientExecutionStrategy.executor();
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void multiplePostRequests() throws Exception {
        multipleRequests(false);
    }

    @Test
    public void multipleGetRequests() throws Exception {
        multipleRequests(true);
    }

    private void multipleRequests(boolean get) throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        String responseBody = "hello world";
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildBlocking()) {
            for (int i = 0; i < 5; ++i) {
                HttpResponse response = client.request((get ? client.get("/" + i) : client.post("/" + i))
                        .payloadBody(responseBody, textSerializer()));
                assertEquals(responseBody, response.payloadBody(textDeserializer()));
            }
        }
    }

    @Test
    public void cookiesRoundTrip() throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            String requestCookie = "name1=value1; name2=value2; name3=value3";
            request.addHeader(COOKIE, requestCookie);
            HttpResponse response = client.request(request);
            CharSequence responseCookie = response.headers().get(COOKIE);
            assertNotNull(responseCookie);
            HttpCookiePair cookie = response.headers().getCookie("name1");
            assertNotNull(cookie);
            assertEquals("value1", cookie.value());
            cookie = response.headers().getCookie("name2");
            assertNotNull(cookie);
            assertEquals("value2", cookie.value());
            cookie = response.headers().getCookie("name3");
            assertNotNull(cookie);
            assertEquals("value3", cookie.value());
        }
    }

    @Test
    public void headerCookieRemovalAndIteration() throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            HttpHeaders headers = request.headers();

            // Single COOKIE header entry with duplicate cookie names.
            headers.add(COOKIE, "name1=value1; name2=value2; name1=value3");
            assertEquals(headers.getCookie("name1"), new DefaultHttpCookiePair("name1", "value1"));
            assertEquals(headers.getCookie("name2"), new DefaultHttpCookiePair("name2", "value2"));

            assertIteratorHasItems(headers.getCookies(), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name2", "value2"), new DefaultHttpCookiePair("name1", "value3"));
            assertIteratorHasItems(headers.getCookies("name1"), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name1", "value3"));
            assertIteratorHasItems(headers.getCookies("name2"), new DefaultHttpCookiePair("name2", "value2"));

            assertTrue(headers.removeCookies("name1"));
            assertEmptyIterator(headers.getCookies("name1"));
            assertIteratorHasItems(headers.getCookies("name2"), new DefaultHttpCookiePair("name2", "value2"));

            assertTrue(headers.removeCookies("name2"));
            assertEmptyIterator(headers.getCookies("name1"));
            assertEmptyIterator(headers.getCookies("name2"));
            assertEmptyIterator(headers.values(COOKIE));

            // Simulate the same behavior as above, but with addCookie
            headers.addCookie("name1", "value1");
            headers.addCookie("name2", "value2");
            headers.addCookie("name1", "value3");
            // Netty's value iterator does not preserve insertion order. This is a limitation of Netty's header
            // data structure and will not be fixed for 4.1.
            if (h2PriorKnowledge) {
                assertEquals(headers.getCookie("name1"), new DefaultHttpCookiePair("name1", "value3"));
            } else {
                assertEquals(headers.getCookie("name1"), new DefaultHttpCookiePair("name1", "value1"));
            }
            assertEquals(headers.getCookie("name2"), new DefaultHttpCookiePair("name2", "value2"));

            assertIteratorHasItems(headers.getCookies(), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name2", "value2"), new DefaultHttpCookiePair("name1", "value3"));
            assertIteratorHasItems(headers.getCookies("name1"), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name1", "value3"));
            assertIteratorHasItems(headers.getCookies("name2"), new DefaultHttpCookiePair("name2", "value2"));

            assertTrue(headers.removeCookies("name1"));
            assertEmptyIterator(headers.getCookies("name1"));
            assertIteratorHasItems(headers.getCookies("name2"), new DefaultHttpCookiePair("name2", "value2"));

            assertTrue(headers.removeCookies("name2"));
            assertEmptyIterator(headers.getCookies("name1"));
            assertEmptyIterator(headers.getCookies("name2"));
            assertEmptyIterator(headers.values(COOKIE));

            // Split headers across 2 header entries, with duplicate cookie names.
            headers.add(COOKIE, "name1=value1; name2=value2; name1=value3");
            headers.add(COOKIE, "name2=value4; name1=value5; name3=value6");
            if (h2PriorKnowledge) {
                assertEquals(headers.getCookie("name1"), new DefaultHttpCookiePair("name1", "value5"));
                assertEquals(headers.getCookie("name2"), new DefaultHttpCookiePair("name2", "value4"));
                assertEquals(headers.getCookie("name3"), new DefaultHttpCookiePair("name3", "value6"));
            } else {
                assertEquals(headers.getCookie("name1"), new DefaultHttpCookiePair("name1", "value1"));
                assertEquals(headers.getCookie("name2"), new DefaultHttpCookiePair("name2", "value2"));
                assertEquals(headers.getCookie("name3"), new DefaultHttpCookiePair("name3", "value6"));
            }

            assertIteratorHasItems(headers.getCookies(), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name2", "value2"), new DefaultHttpCookiePair("name1", "value3"),
                    new DefaultHttpCookiePair("name2", "value4"), new DefaultHttpCookiePair("name1", "value5"),
                    new DefaultHttpCookiePair("name3", "value6"));
            assertIteratorHasItems(headers.getCookies("name1"), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name1", "value3"), new DefaultHttpCookiePair("name1", "value5"));
            assertIteratorHasItems(headers.getCookies("name2"), new DefaultHttpCookiePair("name2", "value2"),
                    new DefaultHttpCookiePair("name2", "value4"));
            assertIteratorHasItems(headers.getCookies("name3"), new DefaultHttpCookiePair("name3", "value6"));

            assertTrue(headers.removeCookies("name2"));
            assertIteratorHasItems(headers.getCookies("name1"), new DefaultHttpCookiePair("name1", "value1"),
                    new DefaultHttpCookiePair("name1", "value3"), new DefaultHttpCookiePair("name1", "value5"));
            assertEmptyIterator(headers.getCookies("name2"));
            assertIteratorHasItems(headers.getCookies("name3"), new DefaultHttpCookiePair("name3", "value6"));

            assertTrue(headers.removeCookies("name1"));
            assertEmptyIterator(headers.getCookies("name1"));
            assertEmptyIterator(headers.getCookies("name2"));
            assertIteratorHasItems(headers.getCookies("name3"), new DefaultHttpCookiePair("name3", "value6"));

            assertTrue(headers.removeCookies("name3"));
            assertEmptyIterator(headers.getCookies("name1"));
            assertEmptyIterator(headers.getCookies("name2"));
            assertEmptyIterator(headers.getCookies("name3"));
            assertEmptyIterator(headers.values(COOKIE));
        }
    }

    @Test
    public void reserveConnection() throws Exception {
        String responseBody = "hello world";
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            ReservedBlockingHttpConnection reservedConnection = client.reserveConnection(request);
            try {
                HttpResponse response = client.request(request.payloadBody(responseBody, textSerializer()));
                assertEquals(responseBody, response.payloadBody(textDeserializer()));
            } finally {
                reservedConnection.release();
            }
        }
    }

    @Test
    public void writeTrailers() throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildBlocking()) {
            String payloadBody = "foo";
            String myTrailerName = "mytrailer";
            String myTrailerValue = "myvalue";
            HttpRequest request = client.post("/").payloadBody(payloadBody, textSerializer());
            request.trailers().add(myTrailerName, myTrailerValue);
            HttpResponse response = client.request(request);
            assertEquals(payloadBody, response.payloadBody(textDeserializer()));
            CharSequence responseTrailer = response.trailers().get(myTrailerName);
            assertNotNull(responseTrailer);
            assertEquals(0, responseTrailer.toString().compareToIgnoreCase(myTrailerValue));
        }
    }

    @Test
    public void clientFilterAsyncContext() throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        final Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy)
                .appendClientFilter((client2, lbEvents) -> new StreamingHttpClientFilter(client2) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return asyncContextTestRequest(errorQueue, delegate, strategy, request);
                    }
                })
                .buildBlocking()) {

            final String responseBody = "foo";
            HttpResponse response = client.request(client.post("/0")
                    .payloadBody(responseBody, textSerializer()));
            assertEquals(responseBody, response.payloadBody(textDeserializer()));
            assertEmpty(errorQueue);
        }
    }

    @Test
    public void connectionFilterAsyncContext() throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        final Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge)
                .executionStrategy(clientExecutionStrategy)
                .appendConnectionFilter(connection -> new StreamingHttpConnectionFilter(connection) {
                    @Override
                    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                 final StreamingHttpRequest request) {
                        return asyncContextTestRequest(errorQueue, delegate(), strategy, request);
                    }
                })
                .buildBlocking()) {

            final String responseBody = "foo";
            HttpResponse response = client.request(client.post("/0").payloadBody(responseBody, textSerializer()));
            assertEquals(responseBody, response.payloadBody(textDeserializer()));
            assertEmpty(errorQueue);
        }
    }

    @Test
    public void gracefulClose() throws Exception {
        assumeFullDuplex();

        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildStreaming();
        try {
            CountDownLatch onCloseLatch = new CountDownLatch(1);
            SpScPublisherProcessor<Buffer> requestBody = new SpScPublisherProcessor<>(16);

            client.onClose().subscribe(onCloseLatch::countDown);

            // We want to make a request, and intentionally not complete it. While the request is in process we invoke
            // closeAsyncGracefully and verify that we wait until the request has completed before the underlying
            // transport is closed.
            StreamingHttpRequest request = client.post("/").payloadBody(requestBody);
            client.request(request).toFuture().get();

            client.closeAsyncGracefully().subscribe();

            // We expect this to timeout, because we have not completed the outstanding request.
            assertFalse(onCloseLatch.await(300, MILLISECONDS));

            requestBody.sendOnComplete();
            onCloseLatch.await();
        } finally {
            client.closeAsync().subscribe();
        }
    }

    @Test
    public void fullDuplexMode() throws Exception {
        assumeFullDuplex();

        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildStreaming()) {
            SpScPublisherProcessor<Buffer> requestBody1 = new SpScPublisherProcessor<>(16);
            StreamingHttpResponse response1 = client.request(client.post("/0").payloadBody(requestBody1))
                    .toFuture().get();

            SpScPublisherProcessor<Buffer> requestBody2 = new SpScPublisherProcessor<>(16);
            StreamingHttpResponse response2 = client.request(client.post("/1").payloadBody(requestBody2))
                    .toFuture().get();

            Iterator<Buffer> response1Payload = response1.payloadBody().toIterable().iterator();
            Iterator<Buffer> response2Payload = response2.payloadBody().toIterable().iterator();

            fullDuplexModeWrite(client, requestBody1, "foo1", requestBody2, "bar1", response1Payload, response2Payload);
            fullDuplexModeWrite(client, requestBody1, "foo2", requestBody2, "bar2", response1Payload, response2Payload);
            requestBody1.sendOnComplete();
            requestBody2.sendOnComplete();
            assertFalse(response1Payload.hasNext());
            assertFalse(response2Payload.hasNext());
        }
    }

    private void assumeFullDuplex() {
        // HTTP/1.x client builder currently forces pipelined connections are built to avoid incorrect behavior when
        // reserving connections, but DefaultNettyPipelinedConnection does not yet support full duplex.
        assumeTrue(h2PriorKnowledge);
    }

    private static void fullDuplexModeWrite(StreamingHttpClient client,
                                            SpScPublisherProcessor<Buffer> requestBody1, String request1ToWrite,
                                            SpScPublisherProcessor<Buffer> requestBody2, String request2ToWrite,
                                            Iterator<Buffer> response1Payload, Iterator<Buffer> response2Payload) {
        requestBody1.sendOnNext(client.executionContext().bufferAllocator().fromAscii(request1ToWrite));
        requestBody2.sendOnNext(client.executionContext().bufferAllocator().fromAscii(request2ToWrite));

        assertTrue(response1Payload.hasNext());
        Buffer next = response1Payload.next();
        assertNotNull(next);
        assertEquals(request1ToWrite, next.toString(UTF_8));
        assertTrue(response2Payload.hasNext());
        next = response2Payload.next();
        assertNotNull(next);
        assertEquals(request2ToWrite, next.toString(UTF_8));
    }

    @Test
    public void clientRespectsSettingsFrame() throws Exception {
        int expectedMaxConcurrent = 1;
        BlockingQueue<FilterableStreamingHttpConnection> connectionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Publisher<? extends ConsumableEvent<Integer>>> maxConcurrentPubQueue =
                new LinkedBlockingQueue<>();
        AtomicReference<Channel> serverParentChannelRef = new AtomicReference<>();
        CountDownLatch serverChannelLatch = new CountDownLatch(1);
        CountDownLatch serverSettingsAckLatch = new CountDownLatch(2);
        InetSocketAddress serverAddress = bindH2Server(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(EchoHttp2Handler.INSTANCE);
            }
        }, parentPipeline -> parentPipeline.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                if (serverParentChannelRef.compareAndSet(null, ctx.channel())) {
                    serverChannelLatch.countDown();
                }
                super.channelActive(ctx);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof Http2SettingsAckFrame) {
                    serverSettingsAckLatch.countDown();
                }
                super.channelRead(ctx, msg);
            }
        }));
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(true)
                .executionStrategy(clientExecutionStrategy)
                .appendConnectionFilter(conn -> new TestConnectionFilter(conn, connectionQueue, maxConcurrentPubQueue))
                .buildStreaming()) {

            SpScPublisherProcessor<Buffer> requestPayload = new SpScPublisherProcessor<>(16);
            client.request(client.post("/0").payloadBody(requestPayload)).toFuture().get();

            Channel serverParentChannel = serverParentChannelRef.get();
            serverParentChannel.writeAndFlush(new DefaultHttp2SettingsFrame(
                    new Http2Settings().maxConcurrentStreams(expectedMaxConcurrent))).syncUninterruptibly();

            Iterator<? extends ConsumableEvent<Integer>> maxItr = maxConcurrentPubQueue.take().toIterable().iterator();
            // We previously made a request, and intentionally didn't complete the request body. We want to verify
            // that we have received the SETTINGS frame reducing the total number of streams to 1. After this point
            // we want to issue a new request and verify it selects a new connection.
            if (maxItr.hasNext()) {
                ConsumableEvent<Integer> next = maxItr.next();
                assertNotNull(next);
                Integer nextValue = next.event();
                assertNotNull(nextValue);
                assertEquals(expectedMaxConcurrent, nextValue.intValue());
            }

            SpScPublisherProcessor<Buffer> requestPayload2 = new SpScPublisherProcessor<>(16);
            client.request(client.post("/1").payloadBody(requestPayload2)).toFuture().get();

            // We expect 2 connections to be created.
            assertNotSame(connectionQueue.take(), connectionQueue.take());

            requestPayload.sendOnComplete();
            requestPayload2.sendOnComplete();
        }
    }

    @Ignore("100 continue is not yet supported")
    @Test
    public void continue100() throws Exception {
        continue100(false);
    }

    @Ignore("100 continue is not yet supported")
    @Test
    public void continue100FailExpectation() throws Exception {
        continue100(true);
    }

    private void continue100(boolean failExpectation) throws Exception {
        InetSocketAddress serverAddress = h2PriorKnowledge ? bindH2EchoServer() : bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .h2PriorKnowledge(h2PriorKnowledge).executionStrategy(clientExecutionStrategy).buildStreaming()) {
            SpScPublisherProcessor<Buffer> requestBody1 = new SpScPublisherProcessor<>(16);
            StreamingHttpRequest request = client.post("/").payloadBody(requestBody1);
            request.addHeader(EXPECT, CONTINUE);
            if (failExpectation) {
                request.addHeader(EXPECT_FAIL_HEADER, "notused");
            }
            StreamingHttpResponse response = client.request(request).toFuture().get();
            if (failExpectation) {
                assertEquals(EXPECTATION_FAILED, response.status());
                assertFalse(response.payloadBody().toIterable().iterator().hasNext());
            } else {
                assertEquals(HttpResponseStatus.CONTINUE, response.status());
                String payloadBody = "foo";
                requestBody1.sendOnNext(client.executionContext().bufferAllocator().fromAscii(payloadBody));
                requestBody1.sendOnComplete();
                Iterator<Buffer> responseBody = response.payloadBody().toIterable().iterator();
                assertTrue(responseBody.hasNext());
                Buffer next = responseBody.next();
                assertNotNull(next);
                assertEquals(payloadBody, next.toString(UTF_8));
                assertFalse(responseBody.hasNext());
            }
        }
    }

    private InetSocketAddress bindH2Server(ChannelHandler childChannelHandler) {
        return bindH2Server(childChannelHandler, p -> { });
    }

    private InetSocketAddress bindH2Server(ChannelHandler childChannelHandler,
                                           Consumer<ChannelPipeline> parentChannelInitializer) {
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventLoopGroup);
        sb.channel(serverChannel(serverEventLoopGroup, InetSocketAddress.class));
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(Http2MultiplexCodecBuilder.forServer(childChannelHandler).build());
                parentChannelInitializer.accept(ch.pipeline());
            }
        });
        serverAcceptorChannel = sb.bind(localAddress(0)).syncUninterruptibly().channel();
        return (InetSocketAddress) serverAcceptorChannel.localAddress();
    }

    private InetSocketAddress bindH2EchoServer() {
        return bindH2Server(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(EchoHttp2Handler.INSTANCE);
            }
        });
    }

    private InetSocketAddress bindHttpEchoServer() throws ExecutionException, InterruptedException {
        h1ServerContext = HttpServers.forAddress(localAddress(0)).listenStreaming(
                (ctx, request, responseFactory) -> {
                    StreamingHttpResponse resp;
                    if (request.headers().contains(EXPECT, CONTINUE)) {
                        if (request.headers().contains(EXPECT_FAIL_HEADER)) {
                            return succeeded(responseFactory.expectationFailed());
                        } else {
                            resp = responseFactory.continueResponse();
                        }
                    } else {
                        resp = responseFactory.ok();
                    }
                    resp = resp.transformRawPayloadBody(pub -> request.payloadBodyAndTrailers());
                    CharSequence contentType = request.headers().get(CONTENT_TYPE);
                    if (contentType != null) {
                        resp.headers().add(CONTENT_TYPE, contentType);
                    }
                    resp.headers().add(COOKIE, request.headers().values(HttpHeaderNames.COOKIE));
                    return succeeded(resp);
                }).toFuture().get();
        return (InetSocketAddress) h1ServerContext.listenAddress();
    }

    private static Single<StreamingHttpResponse> asyncContextTestRequest(Queue<Throwable> errorQueue,
                                                                         final StreamingHttpRequester delegate,
                                                                         final HttpExecutionStrategy strategy,
                                                                         final StreamingHttpRequest request) {
        final String v1 = "v1";
        final String v2 = "v2";
        final String v3 = "v3";
        AsyncContext.put(K1, v1);
        return delegate.request(strategy, request.transformRawPayloadBody(pub -> {
            AsyncContext.put(K2, v2);
            assertAsyncContext(K1, v1, errorQueue);
            assertAsyncContext(K2, v2, errorQueue);
            return pub.beforeSubscriber(() -> new PublisherSource.Subscriber<Object>() {
                @Override
                public void onSubscribe(final PublisherSource.Subscription subscription) {
                    assertAsyncContext(K1, v1, errorQueue);
                    assertAsyncContext(K2, v2, errorQueue);
                }

                @Override
                public void onNext(@Nullable final Object o) {
                    assertAsyncContext(K1, v1, errorQueue);
                    assertAsyncContext(K2, v2, errorQueue);
                }

                @Override
                public void onError(final Throwable t) {
                    assertAsyncContext(K1, v1, errorQueue);
                    assertAsyncContext(K2, v2, errorQueue);
                }

                @Override
                public void onComplete() {
                    assertAsyncContext(K1, v1, errorQueue);
                    assertAsyncContext(K2, v2, errorQueue);
                }
            });
        })).map(resp -> {
            AsyncContext.put(K3, v3);
            assertAsyncContext(K1, v1, errorQueue);
            assertAsyncContext(K2, v2, errorQueue);
            assertAsyncContext(K3, v3, errorQueue);
            return resp.transformRawPayloadBody(pub -> {
                assertAsyncContext(K1, v1, errorQueue);
                assertAsyncContext(K2, v2, errorQueue);
                assertAsyncContext(K3, v3, errorQueue);
                return pub.beforeSubscriber(() -> new PublisherSource.Subscriber<Object>() {
                    @Override
                    public void onSubscribe(final PublisherSource.Subscription subscription) {
                        assertAsyncContext(K1, v1, errorQueue);
                        assertAsyncContext(K2, v2, errorQueue);
                        assertAsyncContext(K3, v3, errorQueue);
                    }

                    @Override
                    public void onNext(@Nullable final Object o) {
                        assertAsyncContext(K1, v1, errorQueue);
                        assertAsyncContext(K2, v2, errorQueue);
                        assertAsyncContext(K3, v3, errorQueue);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        assertAsyncContext(K1, v1, errorQueue);
                        assertAsyncContext(K2, v2, errorQueue);
                        assertAsyncContext(K3, v3, errorQueue);
                    }

                    @Override
                    public void onComplete() {
                        assertAsyncContext(K1, v1, errorQueue);
                        assertAsyncContext(K2, v2, errorQueue);
                        assertAsyncContext(K3, v3, errorQueue);
                    }
                });
            });
        });
    }

    private static void assertEmpty(Queue<Throwable> errorQueue) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, UTF_8.name())) {
            Throwable t;
            while ((t = errorQueue.poll()) != null) {
                t.printStackTrace(ps);
                ps.println(' ');
            }
            String data = new String(baos.toByteArray(), UTF_8);
            if (!data.isEmpty()) {
                throw new AssertionError(data);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> void assertAsyncContext(AsyncContextMap.Key<T> key, T expectedValue,
                                               Queue<Throwable> errorQueue) {
        T actualValue = AsyncContext.get(key);
        if (!expectedValue.equals(actualValue)) {
            AssertionError e = new AssertionError("unexpected value for " + key + ": " +
                    actualValue + " expected: " + expectedValue);
            errorQueue.add(e);
        }
    }

    private static final class TestConnectionFilter extends StreamingHttpConnectionFilter {
        private final Publisher<? extends ConsumableEvent<Integer>> maxConcurrent;

        protected TestConnectionFilter(final FilterableStreamingHttpConnection delegate,
                                       Queue<FilterableStreamingHttpConnection> connectionQueue,
                                       Queue<Publisher<? extends ConsumableEvent<Integer>>> maxConcurrentPubQueue) {
            super(delegate);
            maxConcurrent = delegate.transportEventStream(MAX_CONCURRENCY).multicastToExactly(2);
            connectionQueue.add(delegate);
            maxConcurrentPubQueue.add(maxConcurrent);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return eventKey == MAX_CONCURRENCY ? (Publisher<? extends T>) maxConcurrent :
                    super.transportEventStream(eventKey);
        }
    }

    @ChannelHandler.Sharable
    private static final class EchoHttp2Handler extends ChannelDuplexHandler {
        static final EchoHttp2Handler INSTANCE = new EchoHttp2Handler();

        private EchoHttp2Handler() {
            // singleton
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            cause.printStackTrace();
            ctx.close();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Http2HeadersFrame) {
                onHeadersRead(ctx, (Http2HeadersFrame) msg);
            } else if (msg instanceof Http2DataFrame) {
                onDataRead(ctx, (Http2DataFrame) msg);
            } else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        private void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) {
            ctx.write(new DefaultHttp2DataFrame(data.content().retainedDuplicate(), data.isEndStream()));
        }

        private void onHeadersRead(ChannelHandlerContext ctx, Http2HeadersFrame headers) {
            if (headers.isEndStream()) {
                ctx.write(new DefaultHttp2HeadersFrame(headers.headers(), true));
            } else {
                Http2Headers outHeaders = new DefaultHttp2Headers();
                if (headers.headers().contains(EXPECT, CONTINUE)) {
                    if (headers.headers().contains(EXPECT_FAIL_HEADER)) {
                        outHeaders.status(
                                io.netty.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED.codeAsText());
                        ctx.write(new DefaultHttp2HeadersFrame(outHeaders, true));
                        return;
                    } else {
                        outHeaders.status(io.netty.handler.codec.http.HttpResponseStatus.CONTINUE.codeAsText());
                    }
                } else {
                    outHeaders.status(OK.codeAsText());
                }

                CharSequence contentType = headers.headers().get(CONTENT_TYPE);
                if (contentType != null) {
                    outHeaders.add(CONTENT_TYPE, contentType);
                }
                outHeaders.add(HttpHeaderNames.COOKIE, headers.headers().getAll(HttpHeaderNames.COOKIE));
                ctx.write(new DefaultHttp2HeadersFrame(outHeaders));
            }
        }
    }

    enum TestExecutionStrategy {
        NO_OFFLOAD(HttpExecutionStrategies::noOffloadsStrategy),
        CACHED(() -> HttpExecutionStrategies.defaultStrategy(newCachedThreadExecutor(
                new DefaultThreadFactory("client-executor", true, NORM_PRIORITY))));

        final Supplier<HttpExecutionStrategy> executorSupplier;

        TestExecutionStrategy(final Supplier<HttpExecutionStrategy> executorSupplier) {
            this.executorSupplier = executorSupplier;
        }
    }

    private static <T> void assertEmptyIterator(Iterator<? extends T> itr) {
        List<T> list = new ArrayList<>();
        itr.forEachRemaining(list::add);
        assertThat(list, emptyIterable());
    }

    @SafeVarargs
    private static <T> void assertIteratorHasItems(Iterator<? extends T> itr, T... items) {
        List<T> list = new ArrayList<>();
        itr.forEachRemaining(list::add);
        assertThat(list, hasItems(items));
    }
}
