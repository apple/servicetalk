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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
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
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.DecoderException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.TRAILER;
import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.SET_COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.HttpTestExecutionStrategy.CACHED;
import static io.servicetalk.http.netty.HttpTestExecutionStrategy.NO_OFFLOAD;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createEventLoopGroup;
import static java.lang.String.valueOf;
import static java.lang.Thread.NORM_PRIORITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class H2PriorKnowledgeFeatureParityTest {
    private static final String EXPECT_FAIL_HEADER = "please_fail_expect";
    private static final AsyncContextMap.Key<String> K1 = AsyncContextMap.Key.newKey("k1");
    private static final AsyncContextMap.Key<String> K2 = AsyncContextMap.Key.newKey("k2");
    private static final AsyncContextMap.Key<String> K3 = AsyncContextMap.Key.newKey("k3");
    private EventLoopGroup serverEventLoopGroup;
    private HttpExecutionStrategy clientExecutionStrategy;
    private boolean h2PriorKnowledge;
    @Nullable
    private Channel serverAcceptorChannel;
    @Nullable
    private ServerContext h1ServerContext;

    private void setUp(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) {
        clientExecutionStrategy = strategy.executorSupplier.get();
        serverEventLoopGroup = createEventLoopGroup(2, new DefaultThreadFactory("server-io", true, NORM_PRIORITY));
        this.h2PriorKnowledge = h2PriorKnowledge;
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> clientExecutors() {
        return Stream.of(Arguments.of(NO_OFFLOAD, true),
                         Arguments.of(NO_OFFLOAD, false),
                         Arguments.of(CACHED, true),
                         Arguments.of(CACHED, false));
    }

    @AfterEach
    void teardown() throws Exception {
        if (serverAcceptorChannel != null) {
            serverAcceptorChannel.close().syncUninterruptibly();
        }
        if (h1ServerContext != null) {
            h1ServerContext.close();
        }
        serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        Executor executor = clientExecutionStrategy.executor();
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void multiplePostRequests(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        multipleRequests(false, 10);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void multipleGetRequests(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        multipleRequests(true, 10);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void queryParamsArePreservedForGet(HttpTestExecutionStrategy strategy,
                                       boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        queryParams(GET);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void queryParamsArePreservedForPost(HttpTestExecutionStrategy strategy,
                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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
        }, null);
        String responseBody = "hello world";
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpResponse response = client.request(client.newRequest(method, "/p")
                    .addQueryParameters(qpName, "bar"))
                    .payloadBody(responseBody, textSerializerUtf8());
            assertThat("Unexpected response status.", response.status(), equalTo(OK));
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
                        .payloadBody(responseBody, textSerializerUtf8()));
                assertEquals(responseBody, response.payloadBody(textSerializerUtf8()));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void cookiesRoundTrip(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverAllowDropTrailers(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverAllowDropTrailers(true, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverDoNotAllowDropTrailers(HttpTestExecutionStrategy strategy,
                                      boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverAllowDropTrailers(false, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverAllowDropTrailersClientTrailersHeader(HttpTestExecutionStrategy strategy,
                                                     boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverAllowDropTrailers(true, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverDoNotAllowDropTrailersClientTrailersHeader(HttpTestExecutionStrategy strategy,
                                                          boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverAllowDropTrailers(false, true);
    }

    private void serverAllowDropTrailers(boolean allowDrop, boolean clientAddTrailerHeader) throws Exception {
        String trailerName = "t1";
        String trailerValue = "v1";
        SingleSource.Processor<HttpHeaders, HttpHeaders> trailersProcessor = Processors.newSingleProcessor();
        h1ServerContext = HttpServers.forAddress(localAddress(0))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .allowDropRequestTrailers(allowDrop)
                .listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok().payloadBody(
                    request.transformPayloadBody(buf -> buf) // intermediate Buffer transform may drop trailers
                            .transform(new StatelessTrailersTransformer<Buffer>() {
                                @Override
                                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                    trailersProcessor.onSuccess(trailers);
                                    return trailers;
                                }
                            }).payloadBody())));
        InetSocketAddress serverAddress = (InetSocketAddress) h1ServerContext.listenAddress();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .allowDropResponseTrailers(allowDrop)
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            if (clientAddTrailerHeader) {
                request.headers().add(TRAILER, trailerName);
            }
            request.trailers().add(trailerName, trailerValue);
            client.request(request);
            HttpHeaders requestTrailers = fromSource(trailersProcessor).toFuture().get();
            if (allowDrop && !clientAddTrailerHeader) {
                assertFalse(requestTrailers.contains(trailerName));
            } else {
                assertHeaderValue(requestTrailers, trailerName, trailerValue);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientAllowDropTrailers(HttpTestExecutionStrategy strategy,
                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientAllowDropTrailers(true, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientDoNotAllowDropTrailers(HttpTestExecutionStrategy strategy,
                                      boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientAllowDropTrailers(false, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientAllowDropTrailersServerTrailersHeader(HttpTestExecutionStrategy strategy,
                                                     boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientAllowDropTrailers(true, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientDoNotAllowDropTrailersServerTrailersHeader(HttpTestExecutionStrategy strategy,
                                                          boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientAllowDropTrailers(false, true);
    }

    private void clientAllowDropTrailers(boolean allowDrop, boolean serverAddTrailerHeader) throws Exception {
        String trailerName = "t1";
        String trailerValue = "v1";
        InetSocketAddress serverAddress = serverAddTrailerHeader ?
                bindHttpEchoServerWithTrailer(trailerName) : bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .allowDropResponseTrailers(allowDrop)
                .executionStrategy(clientExecutionStrategy).buildStreaming()) {
            StreamingHttpResponse response = client.request(client.get("/")
                    .transform(new StatelessTrailersTransformer<Buffer>() {
                @Override
                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                    trailers.add(trailerName, trailerValue);
                    return trailers;
                }
            })).toFuture().get();
            SingleSource.Processor<HttpHeaders, HttpHeaders> trailersProcessor = Processors.newSingleProcessor();
            response.transformPayloadBody(buf -> buf) // intermediate Buffer transform may drop trailers
                    .transform(new StatelessTrailersTransformer<Buffer>() {
                        @Override
                        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                            trailersProcessor.onSuccess(trailers);
                            return trailers;
                        }
                    }).messageBody().ignoreElements().toFuture().get();
            HttpHeaders responseTrailers = fromSource(trailersProcessor).toFuture().get();
            if (allowDrop && !serverAddTrailerHeader) {
                assertFalse(responseTrailers.contains(trailerName));
            } else {
                assertHeaderValue(responseTrailers, trailerName, trailerValue);
            }
        }
    }

    private InetSocketAddress bindHttpEchoServerWithTrailer(String trailerName) throws Exception {
        return bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory)
                        .map(response -> {
                            response.headers().add(TRAILER, trailerName);
                            return response;
                        });
            }
        }, null);
    }

    private static void assertHeaderValue(HttpHeaders headers, String key, String value) {
        CharSequence v = headers.get(key);
        assertNotNull(v);
        assertThat(v.toString(), is(value));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverHeaderCookieRemovalAndIteration(HttpTestExecutionStrategy strategy,
                                               boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpSynchronousResponseServer(
                request -> headerCookieRemovalAndIteration(request.headers()));
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            assertThat(client.request(client.get("/").payloadBody("", textSerializerUtf8()))
                    .payloadBody(textSerializerUtf8()), isEmptyString());
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsLargerContentLength(HttpTestExecutionStrategy strategy,
                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        assumeTrue(h2PriorKnowledge, "HTTP/1.x will timeout waiting for more payload");
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength + 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsLargerContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        assumeTrue(h2PriorKnowledge, "HTTP/1.x will timeout waiting for more payload");
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength + 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsSmallerContentLength(HttpTestExecutionStrategy strategy,
                                         boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsSmallerContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsMultipleContentLengthHeaders(HttpTestExecutionStrategy strategy,
                                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength))
                        .add(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsMultipleContentLengthHeadersTrailers(HttpTestExecutionStrategy strategy,
                                                         boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength))
                        .add(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsMultipleContentLengthValues(HttpTestExecutionStrategy strategy,
                                                boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + ", " + (contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsMultipleContentLengthValuesTrailers(HttpTestExecutionStrategy strategy,
                                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + ", " + (contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsSignedContentLength(HttpTestExecutionStrategy strategy,
                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "+" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsSignedContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "+" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsNegativeContentLength(HttpTestExecutionStrategy strategy,
                                          boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "-" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsNegativeContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                  boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "-" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsMalformedContentLength(HttpTestExecutionStrategy strategy,
                                           boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + "_" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientSendsMalformedContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                   boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        clientSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + "_" + contentLength));
    }

    private void clientSendsInvalidContentLength(boolean addTrailers,
                                                 BiConsumer<HttpHeaders, Integer> headersModifier) throws Exception {
        assumeFalse(!h2PriorKnowledge && addTrailers, "HTTP/1.1 does not support Content-Length with trailers");
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy)
                .appendClientFilter(client1 -> new StreamingHttpClientFilter(client1) {
                    @Override
                    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                    final HttpExecutionStrategy strategy,
                                                                    final StreamingHttpRequest request) {
                        return request.toRequest().map(req -> {
                            req.headers().remove(TRANSFER_ENCODING);
                            headersModifier.accept(req.headers(), req.payloadBody().readableBytes());
                            return req.toStreamingRequest();
                        }).flatMap(req -> delegate.request(strategy, req));
                    }
                }).buildBlocking()) {
            HttpRequest request = client.get("/").payloadBody("a", textSerializerUtf8());
            if (addTrailers) {
                request.trailers().set("mytrailer", "myvalue");
            }
            if (h2PriorKnowledge) {
                assertThrows(Http2Exception.H2StreamResetException.class, () -> client.request(request));
            } else {
                try (ReservedBlockingHttpConnection reservedConn = client.reserveConnection(request)) {
                    assertThrows(IOException.class, () -> {
                        // Either the current request or the next one should fail
                        reservedConn.request(request);
                        reservedConn.request(client.get("/"));
                    });
                }
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsLargerContentLength(HttpTestExecutionStrategy strategy,
                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        assumeTrue(h2PriorKnowledge, "HTTP/1.x will timeout waiting for more payload");
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength + 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsLargerContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        assumeTrue(h2PriorKnowledge, "HTTP/1.x will timeout waiting for more payload");
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength + 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsSmallerContentLength(HttpTestExecutionStrategy strategy,
                                         boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsSmallerContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsMultipleContentLengthHeaders(HttpTestExecutionStrategy strategy,
                                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength))
                        .add(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsMultipleContentLengthHeadersTrailers(HttpTestExecutionStrategy strategy,
                                                         boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, valueOf(contentLength))
                        .add(CONTENT_LENGTH, valueOf(contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsMultipleContentLengthValues(HttpTestExecutionStrategy strategy,
                                                boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + ", " + (contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsMultipleContentLengthValuesTrailers(HttpTestExecutionStrategy strategy,
                                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + ", " + (contentLength - 1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsSignedContentLength(HttpTestExecutionStrategy strategy,
                                        boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "+" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsSignedContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "+" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsNegativeContentLength(HttpTestExecutionStrategy strategy,
                                          boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "-" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsNegativeContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                  boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, "-" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsMalformedContentLength(HttpTestExecutionStrategy strategy,
                                           boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(false, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + "_" + contentLength));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverSendsMalformedContentLengthTrailers(HttpTestExecutionStrategy strategy,
                                                   boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        serverSendsInvalidContentLength(true, (headers, contentLength) ->
                headers.set(CONTENT_LENGTH, contentLength + "_" + contentLength));
    }

    private void serverSendsInvalidContentLength(boolean addTrailers,
                                                 BiConsumer<HttpHeaders, Integer> headersModifier) throws Exception {
        assumeFalse(!h2PriorKnowledge && addTrailers, "HTTP/1.1 does not support Content-Length with trailers");
        InetSocketAddress serverAddress = bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory).flatMap(resp -> resp.transformMessageBody(
                        // Filter out trailers when we do not expect them. Because we echo the payload body publisher
                        // of the request that comes from network, it always has empty trailers. Presence of those is
                        // honored during "streaming -> aggregated -> streaming" conversion.
                        pub -> addTrailers ? pub : pub.filter(i -> i instanceof Buffer)).toResponse().map(aggResp -> {
                            aggResp.headers().remove(TRANSFER_ENCODING);
                            headersModifier.accept(aggResp.headers(), aggResp.payloadBody().readableBytes());
                            return aggResp.toStreamingResponse();
                        }));
            }
        }, null);
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy)
                .buildBlocking()) {
            HttpRequest request = client.get("/").payloadBody("a", textSerializerUtf8());
            if (addTrailers) {
                request.trailers().set("mytrailer", "myvalue");
            }
            if (h2PriorKnowledge) {
                assertThat(assertThrows(Throwable.class, () -> client.request(request)),
                        instanceOf(Http2Exception.class));
            } else {
                try (ReservedBlockingHttpConnection reservedConn = client.reserveConnection(request)) {
                    assertThrows(DecoderException.class, () -> {
                        // Either the current request or the next one should fail
                        reservedConn.request(request);
                        reservedConn.request(client.get("/"));
                    });
                }
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientHeaderCookieRemovalAndIteration(HttpTestExecutionStrategy strategy,
                                               boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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
        assertEquals(new DefaultHttpCookiePair("name1", "value1"), headers.getCookie("name1"));
        assertEquals(new DefaultHttpCookiePair("name2", "value2"), headers.getCookie("name2"));

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
            assertEquals(new DefaultHttpCookiePair("name1", "value3"), headers.getCookie("name1"));
        } else {
            assertEquals(new DefaultHttpCookiePair("name1", "value1"), headers.getCookie("name1"));
        }
        assertEquals(new DefaultHttpCookiePair("name2", "value2"), headers.getCookie("name2"));

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
            assertEquals(new DefaultHttpCookiePair("name1", "value5"), headers.getCookie("name1"));
            assertEquals(new DefaultHttpCookiePair("name2", "value4"), headers.getCookie("name2"));
            assertEquals(new DefaultHttpCookiePair("name3", "value6"), headers.getCookie("name3"));
        } else {
            assertEquals(new DefaultHttpCookiePair("name1", "value1"), headers.getCookie("name1"));
            assertEquals(new DefaultHttpCookiePair("name2", "value2"), headers.getCookie("name2"));
            assertEquals(new DefaultHttpCookiePair("name3", "value6"), headers.getCookie("name3"));
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

        assertEquals(new DefaultHttpCookiePair("foo", "bar"), headers.getCookie("foo"));
        assertNull(headers.getCookie("baz"));
        assertNull(headers.getCookie("foo="));
        assertNull(headers.getCookie("fo"));
        assertNull(headers.getCookie("f"));

        assertFalse(headers.removeCookies("foo="));
        assertFalse(headers.removeCookies("fo"));
        assertFalse(headers.removeCookies("f"));
        assertEquals(new DefaultHttpCookiePair("foo", "bar"), headers.getCookie("foo"));

        assertEmptyIterator(headers.getCookiesIterator("foo="));
        assertEmptyIterator(headers.getCookiesIterator("fo"));
        assertEmptyIterator(headers.getCookiesIterator("f"));

        assertTrue(headers.removeCookies("foo"));
        assertNull(headers.getCookie("foo"));
        assertEmptyIterator(headers.getCookiesIterator("foo"));
        assertEmptyIterator(headers.valuesIterator(COOKIE));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverHeaderSetCookieRemovalAndIteration(HttpTestExecutionStrategy strategy,
                                                  boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpSynchronousResponseServer(
                request -> headerSetCookieRemovalAndIteration(request.headers()));
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            assertThat(client.request(client.get("/").payloadBody("", textSerializerUtf8()))
                    .payloadBody(textSerializerUtf8()), isEmptyString());
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientHeaderSetCookieRemovalAndIteration(HttpTestExecutionStrategy strategy,
                                                  boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpRequest request = client.get("/");
            headerSetCookieRemovalAndIteration(request.headers());
        }
    }

    private static void headerSetCookieRemovalAndIteration(HttpHeaders headers) {
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

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientReserveConnectionMultipleRequests(HttpTestExecutionStrategy strategy,
                                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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
                HttpResponse response1 = client.request(request.payloadBody(responseBody1, textSerializerUtf8()));
                HttpResponse response2 = client.request(request.payloadBody(responseBody2, textSerializerUtf8()));
                assertEquals(responseBody1, response1.payloadBody(textSerializerUtf8()));
                assertEquals(responseBody2, response2.payloadBody(textSerializerUtf8()));
            } finally {
                reservedConnection.release();
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverWriteTrailers(HttpTestExecutionStrategy strategy,
                             boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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
            HttpRequest request = client.post("/").payloadBody(payloadBody, textSerializerUtf8());
            HttpResponse response = client.request(request);
            assertEquals(0, response.payloadBody().readableBytes());
            CharSequence responseTrailer = response.trailers().get(myTrailerName);
            assertNotNull(responseTrailer);
            assertEquals(payloadBody.length(), Integer.parseInt(responseTrailer.toString()));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientWriteTrailers(HttpTestExecutionStrategy strategy,
                             boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            String payloadBody = "foo";
            String myTrailerName = "mytrailer";
            String myTrailerValue = "myvalue";
            HttpRequest request = client.post("/").payloadBody(payloadBody, textSerializerUtf8());
            request.trailers().add(myTrailerName, myTrailerValue);
            HttpResponse response = client.request(request);
            assertEquals(payloadBody, response.payloadBody(textSerializerUtf8()));
            CharSequence responseTrailer = response.trailers().get(myTrailerName);
            assertNotNull(responseTrailer);
            assertEquals(0, responseTrailer.toString().compareToIgnoreCase(myTrailerValue));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientFilterAsyncContext(HttpTestExecutionStrategy strategy,
                                         boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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
                    .payloadBody(responseBody, textSerializerUtf8()));
            assertEquals(responseBody, response.payloadBody(textSerializerUtf8()));
            assertNoAsyncErrors(errorQueue);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientConnectionFilterAsyncContext(HttpTestExecutionStrategy strategy,
                                            boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
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
            HttpResponse response = client.request(client.post("/0").payloadBody(responseBody, textSerializerUtf8()));
            assertEquals(responseBody, response.payloadBody(textSerializerUtf8()));
            assertNoAsyncErrors(errorQueue);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverGracefulClose(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        CountDownLatch serverReceivedRequestLatch = new CountDownLatch(1);
        CountDownLatch connectionOnClosingLatch = new CountDownLatch(1);

        InetSocketAddress serverAddress = bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                serverReceivedRequestLatch.countDown();
                return delegate().handle(ctx, request, responseFactory);
            }
        }, connectionOnClosingLatch);
        StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming();
        Processor<Buffer, Buffer> requestBody = newProcessor();
        // We want to make a request, and intentionally not complete it. While the request is in process we invoke
        // closeAsyncGracefully and verify that we wait until the request has completed before the underlying
        // transport is closed.
        StreamingHttpRequest request = client.post("/").payloadBody(fromSource(requestBody));
        StreamingHttpResponse response = client.request(request).toFuture().get();

        // Wait for the server the process the request.
        serverReceivedRequestLatch.await();

        // Initiate graceful close on the server
        assertNotNull(h1ServerContext);
        CountDownLatch onServerCloseLatch = new CountDownLatch(1);
        h1ServerContext.onClose().subscribe(onServerCloseLatch::countDown);
        h1ServerContext.closeAsyncGracefully().subscribe();

        assertTrue(connectionOnClosingLatch.await(300, MILLISECONDS));

        try (BlockingHttpClient client2 = forSingleAddress(HostAndPort.of(serverAddress))
            .protocols(h2PriorKnowledge ? h2Default() : h1Default())
            .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            assertThrows(Throwable.class, () -> client2.request(client2.get("/")),
                         "server has initiated graceful close, subsequent connections/requests are expected to fail.");
        }

        // We expect this to timeout, because we have not completed the outstanding request.
        assertFalse(onServerCloseLatch.await(30, MILLISECONDS));

        requestBody.onComplete();

        HttpResponse fullResponse = response.toResponse().toFuture().get();
        assertEquals(0, fullResponse.payloadBody().readableBytes());
        onServerCloseLatch.await();
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientGracefulClose(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpEchoServer();
        StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming();
        CountDownLatch onCloseLatch = new CountDownLatch(1);
        Processor<Buffer, Buffer> requestBody = newProcessor();

        client.onClose().subscribe(onCloseLatch::countDown);

        // We want to make a request, and intentionally not complete it. While the request is in process we invoke
        // closeAsyncGracefully and verify that we wait until the request has completed before the underlying
        // transport is closed.
        StreamingHttpRequest request = client.post("/").payloadBody(fromSource(requestBody));
        StreamingHttpResponse response = client.request(request).toFuture().get();

        client.closeAsyncGracefully().subscribe();

        // We expect this to timeout, because we have not completed the outstanding request.
        assertFalse(onCloseLatch.await(300, MILLISECONDS));

        requestBody.onComplete();
        response.payloadBody().ignoreElements().toFuture();
        onCloseLatch.await();
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void fullDuplexMode(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming()) {
            Processor<Buffer, Buffer> requestBody1 = newProcessor();
            StreamingHttpResponse response1 = client.request(client.post("/0")
                    .payloadBody(fromSource(requestBody1)))
                    .toFuture().get();

            Processor<Buffer, Buffer> requestBody2 = newProcessor();
            StreamingHttpResponse response2 = client.request(client.post("/1")
                    .payloadBody(fromSource(requestBody2)))
                    .toFuture().get();

            Iterator<Buffer> response1Payload = response1.payloadBody().toIterable().iterator();
            Iterator<Buffer> response2Payload = response2.payloadBody().toIterable().iterator();

            fullDuplexModeWrite(client, requestBody1, "foo1", requestBody2, "bar1", response1Payload, response2Payload);
            fullDuplexModeWrite(client, requestBody1, "foo2", requestBody2, "bar2", response1Payload, response2Payload);
            requestBody1.onComplete();
            requestBody2.onComplete();
            assertFalse(response1Payload.hasNext());
            assertFalse(response2Payload.hasNext());
        }
    }

    private static void fullDuplexModeWrite(StreamingHttpClient client,
                                            Processor<Buffer, Buffer> requestBody1, String request1ToWrite,
                                            Processor<Buffer, Buffer> requestBody2, String request2ToWrite,
                                            Iterator<Buffer> response1Payload, Iterator<Buffer> response2Payload) {
        requestBody1.onNext(client.executionContext().bufferAllocator().fromAscii(request1ToWrite));
        requestBody2.onNext(client.executionContext().bufferAllocator().fromAscii(request2ToWrite));

        assertTrue(response1Payload.hasNext());
        Buffer next = response1Payload.next();
        assertNotNull(next);
        assertEquals(request1ToWrite, next.toString(UTF_8));
        assertTrue(response2Payload.hasNext());
        next = response2Payload.next();
        assertNotNull(next);
        assertEquals(request2ToWrite, next.toString(UTF_8));
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void clientRespectsSettingsFrame(HttpTestExecutionStrategy strategy,
                                     boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        assumeTrue(h2PriorKnowledge, "Only HTTP/2 supports SETTINGS frames");

        int expectedMaxConcurrent = 1;
        BlockingQueue<FilterableStreamingHttpConnection> connectionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Publisher<? extends ConsumableEvent<Integer>>> maxConcurrentPubQueue =
                new LinkedBlockingQueue<>();
        AtomicReference<Channel> serverParentChannelRef = new AtomicReference<>();
        CountDownLatch serverChannelLatch = new CountDownLatch(1);
        CountDownLatch serverSettingsAckLatch = new CountDownLatch(2);
        serverAcceptorChannel = bindH2Server(serverEventLoopGroup, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(new EchoHttp2Handler());
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
        }), identity());
        InetSocketAddress serverAddress = (InetSocketAddress) serverAcceptorChannel.localAddress();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy)
                .appendConnectionFilter(conn -> new TestConnectionFilter(conn, connectionQueue, maxConcurrentPubQueue))
                .buildStreaming()) {

            Processor<Buffer, Buffer> requestPayload = newProcessor();
            client.request(client.post("/0").payloadBody(fromSource(requestPayload))).toFuture().get();

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
            Processor<Buffer, Buffer> requestPayload2 = newProcessor();
            client.request(client.post("/1").payloadBody(fromSource(requestPayload2))).toFuture().get();

            // We expect 2 connections to be created.
            assertNotSame(connectionQueue.take(), connectionQueue.take());

            requestPayload.onComplete();
            requestPayload2.onComplete();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void serverThrowsFromHandler(HttpTestExecutionStrategy strategy,
                                 boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        InetSocketAddress serverAddress = bindHttpEchoServer(service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                throw DELIBERATE_EXCEPTION;
            }
        }, null);
        try (BlockingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
            assertThat(response.payloadBody(), equalTo(EMPTY_BUFFER));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void trailersWithContentLength(HttpTestExecutionStrategy strategy,
                                   boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        final String expectedPayload = "Hello World!";
        final String expectedPayloadLength = valueOf(expectedPayload.length());
        final String expectedTrailer = "foo";
        final String expectedTrailerValue = "bar";
        final AtomicReference<HttpRequest> requestReceived = new AtomicReference<>();
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    requestReceived.set(request);
                    return responseFactory.ok()
                            .addTrailer(expectedTrailer, expectedTrailerValue)
                            .addHeader(CONTENT_LENGTH, expectedPayloadLength)
                            .payloadBody(expectedPayload, textSerializerUtf8());
                });
             BlockingHttpClient client = forSingleAddress(HostAndPort.of(
                     (InetSocketAddress) serverContext.listenAddress()))
                     .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                     .executionStrategy(clientExecutionStrategy).buildBlocking()) {

            HttpResponse response = client.request(client.post("/")
                    .addTrailer(expectedTrailer, expectedTrailerValue)
                    .addHeader(CONTENT_LENGTH, expectedPayloadLength)
                    .payloadBody(expectedPayload, textSerializerUtf8()));
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody(textSerializerUtf8()), equalTo(expectedPayload));
            assertHeaders(h2PriorKnowledge, response.headers(), expectedPayloadLength);
            assertTrailers(response.trailers(), expectedTrailer, expectedTrailerValue);

            // Verify what server received:
            HttpRequest request = requestReceived.get();
            assertThat(request.payloadBody(textSerializerUtf8()), equalTo(expectedPayload));
            assertHeaders(h2PriorKnowledge, request.headers(), expectedPayloadLength);
            assertTrailers(request.trailers(), expectedTrailer, expectedTrailerValue);
        }
    }

    private static void assertHeaders(boolean h2PriorKnowledge, HttpHeaders headers, String expectedPayloadLength) {
        if (h2PriorKnowledge) {
            // http/2 doesn't support "chunked" encoding, it removes "transfer-encoding" header and preserves
            // content-length:
            assertThat(headers.get(CONTENT_LENGTH), contentEqualTo(expectedPayloadLength));
            assertThat(isTransferEncodingChunked(headers), is(false));
        } else {
            // http/1.x doesn't allow trailers with content-length, but it switches to "chunked" encoding when trailers
            // are present and removes content-length header:
            assertThat(headers.get(CONTENT_LENGTH), nullValue());
            assertThat(isTransferEncodingChunked(headers), is(true));
        }
    }

    private static void assertTrailers(HttpHeaders trailers, String expectedTrailer, String expectedTrailerValue) {
        CharSequence trailer = trailers.get(expectedTrailer);
        assertNotNull(trailer);
        assertThat(trailer.toString(), is(expectedTrailerValue));
    }

    @Disabled("100 continue is not yet supported")
    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void continue100(HttpTestExecutionStrategy strategy, boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        continue100(false);
    }

    @Disabled("100 continue is not yet supported")
    @ParameterizedTest(name = "{displayName} [{index}] client={0}, h2PriorKnowledge={1}")
    @MethodSource("clientExecutors")
    void continue100FailExpectation(HttpTestExecutionStrategy strategy,
                                    boolean h2PriorKnowledge) throws Exception {
        setUp(strategy, h2PriorKnowledge);
        continue100(true);
    }

    private void continue100(boolean failExpectation) throws Exception {
        InetSocketAddress serverAddress = bindHttpEchoServer();
        try (StreamingHttpClient client = forSingleAddress(HostAndPort.of(serverAddress))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default())
                .executionStrategy(clientExecutionStrategy).buildStreaming()) {
            Processor<Buffer, Buffer> requestBody1 = newProcessor();
            StreamingHttpRequest request = client.post("/").payloadBody(fromSource(requestBody1));
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
                requestBody1.onNext(client.executionContext().bufferAllocator().fromAscii(payloadBody));
                requestBody1.onComplete();
                Iterator<Buffer> responseBody = response.payloadBody().toIterable().iterator();
                assertTrue(responseBody.hasNext());
                Buffer next = responseBody.next();
                assertNotNull(next);
                assertEquals(payloadBody, next.toString(UTF_8));
                assertFalse(responseBody.hasNext());
            }
        }
    }

    private static Processor<Buffer, Buffer> newProcessor() {
        return newPublisherProcessor(16);
    }

    static Channel bindH2Server(EventLoopGroup serverEventLoopGroup, ChannelHandler childChannelHandler,
                                Consumer<ChannelPipeline> parentChannelInitializer,
                                UnaryOperator<Http2FrameCodecBuilder> configureH2Codec) {
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventLoopGroup);
        sb.channel(serverChannel(serverEventLoopGroup, InetSocketAddress.class));
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                ch.pipeline().addLast(configureH2Codec.apply(Http2FrameCodecBuilder.forServer()).build(),
                        new Http2MultiplexHandler(childChannelHandler));
                parentChannelInitializer.accept(ch.pipeline());
            }
        });
        return sb.bind(localAddress(0)).syncUninterruptibly().channel();
    }

    private InetSocketAddress bindHttpEchoServer() throws Exception {
        return bindHttpEchoServer(null, null);
    }

    private InetSocketAddress bindHttpEchoServer(@Nullable StreamingHttpServiceFilterFactory filterFactory,
                                                 @Nullable CountDownLatch connectionOnClosingLatch)
            throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                .protocols(h2PriorKnowledge ? h2Default() : h1Default());
        if (filterFactory != null) {
            serverBuilder.appendServiceFilter(filterFactory);
        }

        if (connectionOnClosingLatch != null) {
            serverBuilder.appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                @Override
                public Completable accept(final ConnectionContext context) {
                    ((NettyConnectionContext) context).onClosing()
                            .whenFinally(connectionOnClosingLatch::countDown).subscribe();
                    return completed();
                }
            });
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
                    resp = resp.transformMessageBody(pub -> pub.ignoreElements().merge(request.messageBody()))
                            // Apply empty transform operation only to inform internal PayloadHolder that the payload
                            // body may contain content and trailers
                            .transform(new StatelessTrailersTransformer<>());
                    CharSequence contentType = request.headers().get(CONTENT_TYPE);
                    if (contentType != null) {
                        resp.setHeader(CONTENT_TYPE, contentType);
                    }
                    CharSequence contentLength = request.headers().get(CONTENT_LENGTH);
                    if (contentLength != null) {
                        resp.setHeader(CONTENT_LENGTH, contentLength);
                    }
                    CharSequence transferEncoding = request.headers().get(TRANSFER_ENCODING);
                    if (transferEncoding != null) {
                        resp.setHeader(TRANSFER_ENCODING, transferEncoding);
                    }
                    resp.headers().set(COOKIE, request.headers().valuesIterator(COOKIE));
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
                                return responseFactory.internalServerError()
                                        .toResponse().map(resp ->
                                                resp.payloadBody(throwableToString(cause), textSerializerUtf8())
                                                        .toStreamingResponse());
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

    private static Single<StreamingHttpResponse> asyncContextTestRequest(Queue<Throwable> errorQueue,
                                                                         final StreamingHttpRequester delegate,
                                                                         final HttpExecutionStrategy strategy,
                                                                         final StreamingHttpRequest request) {
        final String v1 = "v1";
        final String v2 = "v2";
        final String v3 = "v3";
        AsyncContext.put(K1, v1);
        return delegate.request(strategy, request.transformMessageBody(pub -> {
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
            return resp.transformMessageBody(pub -> {
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

        TestConnectionFilter(final FilterableStreamingHttpConnection delegate,
                             Queue<FilterableStreamingHttpConnection> connectionQueue,
                             Queue<Publisher<? extends ConsumableEvent<Integer>>> maxConcurrentPubQueue) {
            super(delegate);
            maxConcurrent = delegate.transportEventStream(MAX_CONCURRENCY).multicast(2);
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

    static final class EchoHttp2Handler extends ChannelDuplexHandler {
        private boolean sentHeaders;

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

        private static void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) {
            ctx.write(new DefaultHttp2DataFrame(data.content().retainedDuplicate(), data.isEndStream()));
        }

        private void onHeadersRead(ChannelHandlerContext ctx, Http2HeadersFrame headers) {
            if (sentHeaders) {
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
                ctx.write(new DefaultHttp2HeadersFrame(outHeaders, headers.isEndStream()));
                sentHeaders = true;
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
