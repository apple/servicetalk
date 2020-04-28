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
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.HttpSetCookie;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
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
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.SET_COOKIE;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.HttpTestExecutionStrategy.CACHED;
import static io.servicetalk.http.netty.HttpTestExecutionStrategy.NO_OFFLOAD;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.CONNECT_TIMEOUT;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.lang.Thread.NORM_PRIORITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class H2PriorKnowledgeFeatureParityTest {
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

    public H2PriorKnowledgeFeatureParityTest(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) {
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
        multipleRequests(false, 10);
    }

    @Test
    public void multipleGetRequests() throws Exception {
        multipleRequests(true, 10);
    }

    @Test
    public void queryParamsArePreservedForGet() throws Exception {
        queryParams(GET);
    }

    @Test
    public void queryParamsArePreservedForPost() throws Exception {
        queryParams(POST);
    }

    private void queryParams(final HttpRequestMethod method) throws Exception {
        final String qpName = "foo";
        InetSocketAddress serverAddress = bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return request.queryParameter(qpName) == null ?
                        succeeded(responseFactory.badRequest()) :
                        super.handle(ctx, request, responseFactory);
            }
        });
        String responseBody = "hello world";
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpResponse response = client.request(client.newRequest(method, "/p")
                    .addQueryParameters(qpName, "bar"))
                    .payloadBody(responseBody, textSerializer());
            assertThat("Unexpexcted response status.", response.status(), equalTo(OK));
        }
    }

    private void multipleRequests(boolean get, int numberRequests) throws Exception {
        assert numberRequests > 0;
        InetSocketAddress serverAddress = bindHttpEchoServer();
        String responseBody = "hello world";
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            for (int i = 0; i < numberRequests; ++i) {
                HttpResponse response = client.request((get ? client.get("/" + i) : client.post("/" + i))
                        .payloadBody(responseBody, textSerializer()));
                assertEquals(responseBody, response.payloadBody(textDeserializer()));
            }
        }
    }

    @Test
    public void cookiesRoundTrip() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
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
    public void serverHeaderCookieRemovalAndIteration() throws Exception {
        InetSocketAddress serverAddress = bindHttpSynchronousResponseServer(
                request -> headerCookieRemovalAndIteration(request.headers()));
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            assertThat(client.request(client.get("/").payloadBody("", textSerializer()))
                    .payloadBody(textDeserializer()), isEmptyString());
        }
    }

    @Test
    public void clientHeaderCookieRemovalAndIteration() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            headerCookieRemovalAndIteration(request.headers());
        }
    }

    private void headerCookieRemovalAndIteration(HttpHeaders headers) {
        // Single COOKIE header entry with duplicate cookie names.
        headers.add(COOKIE, "name1=value1; name2=value2; name1=value3");
        assertEquals(headers.getCookie("name1"), new DefaultHttpCookiePair("name1", "value1"));
        assertEquals(headers.getCookie("name2"), new DefaultHttpCookiePair("name2", "value2"));

        assertIteratorHasItems(headers.getCookiesIterator(), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name2", "value2"), new DefaultHttpCookiePair("name1", "value3"));
        assertIteratorHasItems(headers.getCookiesIterator("name1"), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name1", "value3"));
        assertIteratorHasItems(headers.getCookiesIterator("name2"), new DefaultHttpCookiePair("name2", "value2"));

        assertTrue(headers.removeCookies("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name1"));
        assertIteratorHasItems(headers.getCookiesIterator("name2"), new DefaultHttpCookiePair("name2", "value2"));

        assertTrue(headers.removeCookies("name2"));
        assertEmptyIterator(headers.getCookiesIterator("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name2"));
        assertEmptyIterator(headers.valuesIterator(COOKIE));

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

        assertIteratorHasItems(headers.getCookiesIterator(), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name2", "value2"), new DefaultHttpCookiePair("name1", "value3"));
        assertIteratorHasItems(headers.getCookiesIterator("name1"), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name1", "value3"));
        assertIteratorHasItems(headers.getCookiesIterator("name2"), new DefaultHttpCookiePair("name2", "value2"));

        assertTrue(headers.removeCookies("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name1"));
        assertIteratorHasItems(headers.getCookiesIterator("name2"), new DefaultHttpCookiePair("name2", "value2"));

        assertTrue(headers.removeCookies("name2"));
        assertEmptyIterator(headers.getCookiesIterator("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name2"));
        assertEmptyIterator(headers.valuesIterator(COOKIE));

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

        assertIteratorHasItems(headers.getCookiesIterator(), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name2", "value2"), new DefaultHttpCookiePair("name1", "value3"),
                new DefaultHttpCookiePair("name2", "value4"), new DefaultHttpCookiePair("name1", "value5"),
                new DefaultHttpCookiePair("name3", "value6"));
        assertIteratorHasItems(headers.getCookiesIterator("name1"), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name1", "value3"), new DefaultHttpCookiePair("name1", "value5"));
        assertIteratorHasItems(headers.getCookiesIterator("name2"), new DefaultHttpCookiePair("name2", "value2"),
                new DefaultHttpCookiePair("name2", "value4"));
        assertIteratorHasItems(headers.getCookiesIterator("name3"), new DefaultHttpCookiePair("name3", "value6"));

        assertTrue(headers.removeCookies("name2"));
        assertIteratorHasItems(headers.getCookiesIterator("name1"), new DefaultHttpCookiePair("name1", "value1"),
                new DefaultHttpCookiePair("name1", "value3"), new DefaultHttpCookiePair("name1", "value5"));
        assertEmptyIterator(headers.getCookiesIterator("name2"));
        assertIteratorHasItems(headers.getCookiesIterator("name3"), new DefaultHttpCookiePair("name3", "value6"));

        assertTrue(headers.removeCookies("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name2"));
        assertIteratorHasItems(headers.getCookiesIterator("name3"), new DefaultHttpCookiePair("name3", "value6"));

        assertTrue(headers.removeCookies("name3"));
        assertEmptyIterator(headers.getCookiesIterator("name1"));
        assertEmptyIterator(headers.getCookiesIterator("name2"));
        assertEmptyIterator(headers.getCookiesIterator("name3"));
        assertEmptyIterator(headers.valuesIterator(COOKIE));

        // Test partial name matches don't inadvertently match.
        headers.add(COOKIE, "foo=bar");

        assertEquals(headers.getCookie("foo"), new DefaultHttpCookiePair("foo", "bar"));
        assertNull(headers.getCookie("baz"));
        assertNull(headers.getCookie("foo="));
        assertNull(headers.getCookie("fo"));
        assertNull(headers.getCookie("f"));

        assertFalse(headers.removeCookies("foo="));
        assertFalse(headers.removeCookies("fo"));
        assertFalse(headers.removeCookies("f"));
        assertEquals(headers.getCookie("foo"), new DefaultHttpCookiePair("foo", "bar"));

        assertEmptyIterator(headers.getCookiesIterator("foo="));
        assertEmptyIterator(headers.getCookiesIterator("fo"));
        assertEmptyIterator(headers.getCookiesIterator("f"));

        assertTrue(headers.removeCookies("foo"));
        assertNull(headers.getCookie("foo"));
        assertEmptyIterator(headers.getCookiesIterator("foo"));
        assertEmptyIterator(headers.valuesIterator(COOKIE));
    }

    @Test
    public void serverHeaderSetCookieRemovalAndIteration() throws Exception {
        InetSocketAddress serverAddress = bindHttpSynchronousResponseServer(
                request -> headerSetCookieRemovalAndIteration(request.headers()));
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            assertThat(client.request(client.get("/").payloadBody("", textSerializer()))
                    .payloadBody(textDeserializer()), isEmptyString());
        }
    }

    @Test
    public void clientHeaderSetCookieRemovalAndIteration() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            headerSetCookieRemovalAndIteration(request.headers());
        }
    }

    private void headerSetCookieRemovalAndIteration(HttpHeaders headers) {
        headers.add(SET_COOKIE, "qwerty=12345; Domain=somecompany.co.uk; Path=/1; " +
                "Expires=Wed, 30 Aug 2019 00:00:00 GMT");

        assertFalse(headers.removeCookies("qwerty="));
        assertFalse(headers.removeCookies("qwert"));
        assertNull(headers.getSetCookie("qwerty="));
        assertNull(headers.getSetCookie("qwert"));
        assertFalse(headers.getSetCookiesIterator("qwerty=").hasNext());
        assertFalse(headers.getSetCookiesIterator("qwert").hasNext());
        assertEmptyIterator(headers.getSetCookiesIterator("qwerty=", "somecompany.co.uk", "/1"));
        assertEmptyIterator(headers.getSetCookiesIterator("qwert", "somecompany.co.uk", "/1"));

        assertSetCookie1(headers.getSetCookie("qwerty"));
        Iterator<? extends HttpSetCookie> itr = headers.getSetCookiesIterator("qwerty");
        assertTrue(itr.hasNext());
        assertSetCookie1(itr.next());
        assertFalse(itr.hasNext());

        itr = headers.getSetCookiesIterator("qwerty", "somecompany.co.uk", "/1");
        assertTrue(itr.hasNext());
        assertSetCookie1(itr.next());
        assertFalse(itr.hasNext());

        assertTrue(headers.removeSetCookies("qwerty", "somecompany.co.uk", "/1"));
        assertNull(headers.getSetCookie("qwerty"));

        headers.add(SET_COOKIE, "qwerty=12345; Domain=somecompany.co.uk; Path=/1; " +
                "Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        assertTrue(headers.removeSetCookies("qwerty"));
        assertNull(headers.getSetCookie("qwerty"));
    }

    private static void assertSetCookie1(@Nullable HttpSetCookie setCookie) {
        assertNotNull(setCookie);
        assertEquals("qwerty", setCookie.name());
        assertEquals("12345", setCookie.value());
        assertEquals("somecompany.co.uk", setCookie.domain());
        assertEquals("/1", setCookie.path());
        assertEquals("Wed, 30 Aug 2019 00:00:00 GMT", setCookie.expires());
    }

    @Test
    public void clientReserveConnectionMultipleRequests() throws Exception {
        String responseBody1 = "1.hello world.1";
        String responseBody2 = "2.hello world.2";
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            ReservedBlockingHttpConnection reservedConnection = client.reserveConnection(request);
            try {
                // We interleave the requests intentionally to make sure the internal transport sequences the
                // reads and writes correctly.
                HttpResponse response1 = client.request(request.payloadBody(responseBody1, textSerializer()));
                HttpResponse response2 = client.request(request.payloadBody(responseBody2, textSerializer()));
                assertEquals(responseBody1, response1.payloadBody(textDeserializer()));
                assertEquals(responseBody2, response2.payloadBody(textDeserializer()));
            } finally {
                reservedConnection.release();
            }
        }
    }

    @Test
    public void serverWriteTrailers() throws Exception {
        String payloadBody = "foo";
        String myTrailerName = "mytrailer";
        h1ServerContext = HttpServers.forAddress(localAddress(0))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default()).listenStreaming(
                        (ctx, request, responseFactory) ->
                                request.payloadBody()
                                .map(Buffer::readableBytes)
                                .collect(AtomicInteger::new, (contentSize, bufferSize) -> {
                                    contentSize.addAndGet(bufferSize);
                                    return contentSize;
                                })
                                .flatMap(contentSize ->
                                        succeeded(responseFactory.ok()
                                                .transform(new ContentSizeTrailersTransformer(myTrailerName,
                                                        contentSize)))))
                .toFuture().get();

        InetSocketAddress serverAddress = (InetSocketAddress) h1ServerContext.listenAddress();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.post("/").payloadBody(payloadBody, textSerializer());
            HttpResponse response = client.request(request);
            assertEquals(0, response.payloadBody().readableBytes());
            CharSequence responseTrailer = response.trailers().get(myTrailerName);
            assertNotNull(responseTrailer);
            assertEquals(payloadBody.length(), Integer.parseInt(responseTrailer.toString()));
        }
    }

    @Test
    public void clientWriteTrailers() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
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
    public void serverFilterAsyncContext() throws Exception {
        final Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        InetSocketAddress serverAddress = bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return asyncContextTestRequest(errorQueue, delegate(), ctx, request, responseFactory);
            }
        });
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            final String responseBody = "foo";
            HttpResponse response = client.request(client.post("/0")
                    .payloadBody(responseBody, textSerializer()));
            assertEquals(responseBody, response.payloadBody(textDeserializer()));
            assertEmpty(errorQueue);
        }
    }

    @Test
    public void clientFilterAsyncContext() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        final Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy)
                .appendClientFilter(client2 -> new StreamingHttpClientFilter(client2) {
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
    public void clientConnectionFilterAsyncContext() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        final Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
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
    public void serverGracefulClose() throws Exception {
        CountDownLatch serverReceivedRequestLatch = new CountDownLatch(1);
        InetSocketAddress serverAddress = bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                serverReceivedRequestLatch.countDown();
                return delegate().handle(ctx, request, responseFactory);
            }
        });
        StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming();
        SpScPublisherProcessor<Buffer> requestBody = new SpScPublisherProcessor<>(16);
        // We want to make a request, and intentionally not complete it. While the request is in process we invoke
        // closeAsyncGracefully and verify that we wait until the request has completed before the underlying
        // transport is closed.
        StreamingHttpRequest request = client.post("/").payloadBody(requestBody);
        StreamingHttpResponse response = client.request(request).toFuture().get();

        // Wait for the server the process the request.
        serverReceivedRequestLatch.await();

        // Initiate graceful close on the server
        assertNotNull(h1ServerContext);
        CountDownLatch onServerCloseLatch = new CountDownLatch(1);
        h1ServerContext.onClose().subscribe(onServerCloseLatch::countDown);
        h1ServerContext.closeAsyncGracefully().subscribe();

        try (BlockingHttpClient client2 = forSingleAddress(HostAndPort.of(serverAddress))
                .socketOption(CONNECT_TIMEOUT, 1) // windows default connect timeout is seconds, we want to fail fast.
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            try {
                client2.request(client2.get("/"));
                fail("server has initiated graceful close, subsequent connections/requests are expected to fail.");
            } catch (Throwable cause) {
                // expected
            }
        }

        // We expect this to timeout, because we have not completed the outstanding request.
        assertFalse(onServerCloseLatch.await(300, MILLISECONDS));

        requestBody.sendOnComplete();

        HttpResponse fullResponse = response.toResponse().toFuture().get();
        assertEquals(0, fullResponse.payloadBody().readableBytes());
        onServerCloseLatch.await();
    }

    @Test
    public void clientGracefulClose() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming();
        CountDownLatch onCloseLatch = new CountDownLatch(1);
        SpScPublisherProcessor<Buffer> requestBody = new SpScPublisherProcessor<>(16);

        client.onClose().subscribe(onCloseLatch::countDown);

        // We want to make a request, and intentionally not complete it. While the request is in process we invoke
        // closeAsyncGracefully and verify that we wait until the request has completed before the underlying
        // transport is closed.
        StreamingHttpRequest request = client.post("/").payloadBody(requestBody);
        StreamingHttpResponse response = client.request(request).toFuture().get();

        client.closeAsyncGracefully().subscribe();

        // We expect this to timeout, because we have not completed the outstanding request.
        assertFalse(onCloseLatch.await(300, MILLISECONDS));

        requestBody.sendOnComplete();
        response.payloadBody().ignoreElements().toFuture();
        onCloseLatch.await();
    }

    @Test
    public void fullDuplexMode() throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming()) {
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
        assumeTrue(h2PriorKnowledge);   // only h2 supports settings frames

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
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy)
                .appendConnectionFilter(conn -> new TestConnectionFilter(conn, connectionQueue, maxConcurrentPubQueue))
                .buildStreaming()) {

            SpScPublisherProcessor<Buffer> requestPayload = new SpScPublisherProcessor<>(16);
            client.request(client.post("/0").payloadBody(requestPayload)).toFuture().get();

            serverChannelLatch.await();
            Channel serverParentChannel = serverParentChannelRef.get();
            serverParentChannel.writeAndFlush(new DefaultHttp2SettingsFrame(
                    new Http2Settings().maxConcurrentStreams(expectedMaxConcurrent))).syncUninterruptibly();

            Iterator<? extends ConsumableEvent<Integer>> maxItr = maxConcurrentPubQueue.take().toIterable().iterator();
            // Verify that the initial maxConcurrency value is the default number
            assertThat("No initial maxConcurrency value", maxItr.hasNext(), is(true));
            ConsumableEvent<Integer> next = maxItr.next();
            assertThat(next, is(notNullValue()));
            assertThat("First event is not the default", next.event(), is(SMALLEST_MAX_CONCURRENT_STREAMS));
            // We previously made a request, and intentionally didn't complete the request body. We want to verify
            // that we have received the SETTINGS frame reducing the total number of streams to 1.
            assertThat("No maxConcurrency value received", maxItr.hasNext(), is(true));
            next = maxItr.next();
            assertThat(next, is(notNullValue()));
            assertThat("maxConcurrency did not change to the expected value", next.event(), is(expectedMaxConcurrent));

            // Wait for a server to receive a settings ack
            serverSettingsAckLatch.await();

            // After this point we want to issue a new request and verify that client selects a new connection.
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
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming()) {
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

    private InetSocketAddress bindH2Server(ChannelHandler childChannelHandler,
                                           Consumer<ChannelPipeline> parentChannelInitializer) {
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventLoopGroup);
        sb.channel(serverChannel(serverEventLoopGroup, InetSocketAddress.class));
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(
                        Http2FrameCodecBuilder.forServer().build(),
                        new Http2MultiplexHandler(childChannelHandler));
                parentChannelInitializer.accept(ch.pipeline());
            }
        });
        serverAcceptorChannel = sb.bind(localAddress(0)).syncUninterruptibly().channel();
        return (InetSocketAddress) serverAcceptorChannel.localAddress();
    }

    private InetSocketAddress bindHttpEchoServer() throws Exception {
        return bindHttpEchoServer(null);
    }

    private InetSocketAddress bindHttpEchoServer(@Nullable StreamingHttpServiceFilterFactory filterFactory)
            throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default());
        if (filterFactory != null) {
            serverBuilder.appendServiceFilter(filterFactory);
        }
        h1ServerContext = serverBuilder.listenStreaming(
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
                    resp.headers().add(COOKIE, request.headers().valuesIterator(HttpHeaderNames.COOKIE));
                    return succeeded(resp);
                }).toFuture().get();
        return (InetSocketAddress) h1ServerContext.listenAddress();
    }

    private InetSocketAddress bindHttpSynchronousResponseServer(Consumer<StreamingHttpRequest> headerConsumer)
            throws Exception {
        h1ServerContext = HttpServers.forAddress(localAddress(0))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default()).listenStreaming(
                        (ctx, request, responseFactory) -> {
                            try {
                                headerConsumer.accept(request);
                            } catch (Throwable cause) {
                                return succeeded(responseFactory.internalServerError()
                                        .payloadBody(from(throwableToString(cause)), textSerializer()));
                            }
                            StreamingHttpResponse resp = responseFactory.ok();
                            CharSequence contentType = request.headers().get(CONTENT_TYPE);
                            if (contentType != null) {
                                resp.headers().add(CONTENT_TYPE, contentType);
                            }
                            return succeeded(resp);
                        }).toFuture().get();
        return (InetSocketAddress) h1ServerContext.listenAddress();
    }

    private static String throwableToString(Throwable aThrowable) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return result.toString();
    }

    private static Single<StreamingHttpResponse> asyncContextTestRequest(
            Queue<Throwable> errorQueue, final StreamingHttpService delegate,
            final HttpServiceContext ctx, final StreamingHttpRequest request,
            final StreamingHttpResponseFactory responseFactory) {
        final String v1 = "v1";
        final String v2 = "v2";
        AsyncContext.put(K1, v1);
        return delegate.handle(ctx, request, responseFactory).map(streamingHttpResponse -> {
            AsyncContext.put(K2, v2);
            assertAsyncContext(K1, v1, errorQueue);
            assertAsyncContext(K2, v2, errorQueue);
            return streamingHttpResponse.transformRawPayloadBody(pub -> {
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
            });
        });
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
                    outHeaders.status(io.netty.handler.codec.http.HttpResponseStatus.OK.codeAsText());
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

    private static final class ContentSizeTrailersTransformer extends StatelessTrailersTransformer<Buffer> {
        private final String trailerName;
        private final AtomicInteger contentSize;

        ContentSizeTrailersTransformer(final String trailerName, final AtomicInteger contentSize) {
            this.trailerName = trailerName;
            this.contentSize = contentSize;
        }

        @Override
        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
            trailers.add(trailerName, Integer.toString(contentSize.get()));
            return trailers;
        }
    }
}
