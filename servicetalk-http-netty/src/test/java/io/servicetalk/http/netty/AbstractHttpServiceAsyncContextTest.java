/*
 * Copyright © 2019-2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpExchangeObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpRequestObserver;
import io.servicetalk.http.api.HttpLifecycleObserver.HttpResponseObserver;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.assertAsyncContext;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractHttpServiceAsyncContextTest {
    enum InitContextKeyPlace {
        LIFECYCLE_OBSERVER,
        NON_OFFLOADING_LIFECYCLE_OBSERVER_FILTER,
        NON_OFFLOADING_FILTER,
        NON_OFFLOADING_ASYNC_FILTER,
        LIFECYCLE_OBSERVER_FILTER,
        FILTER,
        ASYNC_FILTER
    }

    enum ResponseType {
        SUCCESS,
        CANCEL_ON_RESPONSE,
        ERROR_ON_RESPONSE,
        CANCEL_ON_RESPONSE_BODY,
        ERROR_ON_RESPONSE_BODY,
    }

    enum ConnectionAcceptorType {
        EARLY,
        LATE,
        DEPRECATED
    }

    protected static final ContextMap.Key<CharSequence> K1 = newKey("k1", CharSequence.class);
    protected static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    protected static final String IO_THREAD_PREFIX = "servicetalk-global-io-executor-";

    abstract boolean isBlocking();

    abstract ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                              boolean useImmediate) throws Exception;

    abstract ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                             boolean useImmediate, boolean asyncService) throws Exception;

    private static List<Arguments> newRequestsGetFreshContextArguments() {
        List<Arguments> params = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            for (boolean useImmediate : Arrays.asList(false, true)) {
                params.add(Arguments.of(protocol, useImmediate));
            }
        }
        return params;
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0} useImmediate={1}")
    @MethodSource("newRequestsGetFreshContextArguments")
    final void newRequestsGetFreshContext(HttpProtocol protocol, boolean useImmediate) throws Exception {
        Assumptions.assumeFalse(isBlocking() && useImmediate, "Blocking service can only run with offloading");

        final ExecutorService executorService = Executors.newCachedThreadPool();
        final int concurrency = 10;
        final int numRequests = 10;
        final String k1Value = "value";
        // The service should get an empty AsyncContext regardless of what is done outside the service.
        // There are utilities that may be accessed in a static context or before service initialization that
        // shouldn't pollute the service's AsyncContext.
        AsyncContext.put(K1, k1Value);

        try (ServerContext ctx = serverWithEmptyAsyncContextService(HttpServers.forAddress(localAddress(0))
                        .protocols(protocol.config), useImmediate)) {

            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            CountDownLatch latch = new CountDownLatch(concurrency);
            for (int i = 0; i < concurrency; ++i) {
                final int finalI = i;
                executorService.execute(() -> {
                    try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                            .protocols(protocol == HttpProtocol.HTTP_1 ?
                                    h1().maxPipelinedRequests(numRequests).build() : protocol.config)
                            .buildBlocking()) {
                        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
                            barrier.await();
                            for (int x = 0; x < numRequests; ++x) {
                                makeClientRequestWithId(connection, "thread=" + finalI + " request=" + x);
                            }
                        }
                    } catch (Throwable cause) {
                        causeRef.compareAndSet(null, cause);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            assertNull(causeRef.get());
            assertEquals(k1Value, AsyncContext.get(K1));
        } finally {
            executorService.shutdown();
        }
    }

    private static List<Arguments> contextPreservedOverFilterBoundariesArguments() {
        List<Arguments> params = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            for (boolean useImmediate : Arrays.asList(false, true)) {
                for (boolean asyncService : Arrays.asList(false, true)) {
                    for (InitContextKeyPlace place : InitContextKeyPlace.values()) {
                        for (ResponseType responseType : ResponseType.values()) {
                            params.add(Arguments.of(protocol, useImmediate, asyncService, place, responseType));
                        }
                    }
                }
            }
        }
        return params;
    }

    @ParameterizedTest(name = "{displayName} [{index}]: " +
            "protocol={0} useImmediate={1} asyncService={2} initContextKeyPlace={3}, responseType={4}")
    @MethodSource("contextPreservedOverFilterBoundariesArguments")
    final void contextPreservedOverFilterBoundaries(HttpProtocol protocol, boolean useImmediate,
                      boolean asyncService, InitContextKeyPlace place, ResponseType responseType) throws Exception {
        Assumptions.assumeFalse(isBlocking() && useImmediate, "Blocking service can only run with offloading");
        Assumptions.assumeFalse(isBlocking() && asyncService, "Blocking service can not be async");

        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        AtomicReference<ContextMap> currentContext = new AtomicReference<>();
        HttpServerBuilder builder = new CaptureRequestContextHttpServerBuilder(localAddress(0), currentContext)
                .protocols(protocol.config);
        CountDownLatch latch = new CountDownLatch(1);
        switch (place) {
            case LIFECYCLE_OBSERVER:
                builder.lifecycleObserver(new AsyncContextLifecycleObserver(currentContext, errorQueue, responseType));
                break;
            case NON_OFFLOADING_LIFECYCLE_OBSERVER_FILTER:
                builder.appendNonOffloadingServiceFilter(new HttpLifecycleObserverServiceFilter(
                        new AsyncContextLifecycleObserver(currentContext, errorQueue, responseType)));
                break;
            case NON_OFFLOADING_FILTER:
                builder.appendNonOffloadingServiceFilter(filterFactory(useImmediate, false, errorQueue));
                break;
            case NON_OFFLOADING_ASYNC_FILTER:
                builder.appendNonOffloadingServiceFilter(filterFactory(useImmediate, true, errorQueue));
                break;
            case LIFECYCLE_OBSERVER_FILTER:
                builder.appendServiceFilter(new HttpLifecycleObserverServiceFilter(
                        new AsyncContextLifecycleObserver(currentContext, errorQueue, responseType)));
                break;
            case FILTER:
                builder.appendServiceFilter(filterFactory(useImmediate, false, errorQueue));
                break;
            case ASYNC_FILTER:
                builder.appendServiceFilter(filterFactory(useImmediate, true, errorQueue));
                break;
            default:
                throw new IllegalArgumentException("Unknown InitContextKeyPlace: " + place);
        }
        if (ResponseType.SUCCESS != responseType) {
            // Add a filter to intercept and return the right response type.
            builder.appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                            StreamingHttpResponseFactory responseFactory) {
                    latch.countDown();
                    switch (responseType) {
                        case CANCEL_ON_RESPONSE:
                            return Single.never();
                        case ERROR_ON_RESPONSE:
                            return Single.failed(DELIBERATE_EXCEPTION);
                        default:
                            return delegate().handle(ctx, request, responseFactory)
                                    .map(response -> response.transformPayloadBody(body ->
                                            body.concat(ResponseType.ERROR_ON_RESPONSE_BODY == responseType ?
                                                    Publisher.failed(DELIBERATE_EXCEPTION) : Publisher.never())));
                    }
                }
            });
        }
        try (ServerContext ctx = serverWithService(builder, useImmediate, asyncService);
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                     .protocols(protocol.config).buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            switch (responseType) {
                case CANCEL_ON_RESPONSE:
                case CANCEL_ON_RESPONSE_BODY:
                    makeClientRequestWithIdExpectCancel(connection, "1", latch);
                    break;
                case ERROR_ON_RESPONSE:
                    HttpRequest request = connection.get("/");
                    request.headers().set(REQUEST_ID_HEADER, "1");
                    HttpResponse response = connection.request(request);
                    assertEquals(INTERNAL_SERVER_ERROR, response.status());
                    assertTrue(request.headers().contains(REQUEST_ID_HEADER, "1"));
                    break;
                case ERROR_ON_RESPONSE_BODY:
                    makeClientRequestWithIdExpectError(connection, "1");
                    break;
                case SUCCESS:
                    makeClientRequestWithId(connection, "1");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown ResponseType: " + responseType);
            }
            assertNoAsyncErrors(errorQueue);
        }
    }

    private StreamingHttpServiceFilterFactory filterFactory(final boolean useImmediate,
                                                            final boolean asyncFilter,
                                                            final Queue<Throwable> errorQueue) {
        return new StreamingHttpServiceFilterFactory() {
            @Override
            public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                return new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory factory) {
                        return asyncFilter ? defer(() -> doHandle(ctx, request, factory).shareContextOnSubscribe()) :
                                doHandle(ctx, request, factory);
                    }

                    private Single<StreamingHttpResponse> doHandle(final HttpServiceContext ctx,
                                                                   final StreamingHttpRequest request,
                                                                   final StreamingHttpResponseFactory factory) {
                        if (useImmediate && !currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                            // verify that if we expect to be offloaded, that we actually are
                            return succeeded(factory.internalServerError());
                        }
                        CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                        if (requestId != null) {
                            AsyncContext.put(K1, requestId);
                        }
                        final StreamingHttpRequest filteredRequest = request.transformMessageBody(pub ->
                                pub.afterSubscriber(assertAsyncContextSubscriber(requestId, errorQueue)));
                        return delegate().handle(ctx, filteredRequest, factory).map(resp -> {
                                    assertAsyncContext(K1, requestId, errorQueue);
                                    return resp.transformMessageBody(pub ->
                                            pub.afterSubscriber(assertAsyncContextSubscriber(requestId, errorQueue)));
                                }
                        );
                    }
                };
            }

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return HttpExecutionStrategies.offloadNone();
            }
        };
    }

    private Supplier<PublisherSource.Subscriber<Object>> assertAsyncContextSubscriber(
            @Nullable final CharSequence requestId, final Queue<Throwable> errorQueue) {

        return () -> new PublisherSource.Subscriber<Object>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                assertAsyncContext(K1, requestId, errorQueue);
            }

            @Override
            public void onNext(final Object o) {
                assertAsyncContext(K1, requestId, errorQueue);
            }

            @Override
            public void onError(final Throwable throwable) {
                assertAsyncContext(K1, requestId, errorQueue);
            }

            @Override
            public void onComplete() {
                assertAsyncContext(K1, requestId, errorQueue);
            }
        };
    }

    private static List<Arguments> connectionAcceptorContextDoesNotLeakArguments() {
        List<Arguments> params = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            for (boolean useImmediate : Arrays.asList(false, true)) {
                for (ConnectionAcceptorType acceptorType : ConnectionAcceptorType.values()) {
                    params.add(Arguments.of(protocol, useImmediate, acceptorType));
                }
            }
        }
        return params;
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0} useImmediate={1} connectionAcceptorType={2}")
    @MethodSource("connectionAcceptorContextDoesNotLeakArguments")
    @SuppressWarnings("deprecation")
    final void connectionAcceptorContextDoesNotLeak(HttpProtocol protocol, boolean useImmediate,
                                                    ConnectionAcceptorType connectionAcceptorType) throws Exception {
        Assumptions.assumeFalse(isBlocking() && useImmediate, "Blocking service can only run with offloading");

        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0)).protocols(protocol.config);
        switch (connectionAcceptorType) {
            case EARLY:
                builder.appendEarlyConnectionAcceptor(conn -> {
                    AsyncContext.put(K1, "v1");
                    return completed();
                });
                break;
            case LATE:
                builder.appendLateConnectionAcceptor(conn -> {
                    AsyncContext.put(K1, "v1");
                    return completed();
                });
                break;
            case DEPRECATED:
                builder.appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(context -> {
                    AsyncContext.put(K1, "v1");
                    return completed();
                }));
                break;
            default:
                throw new IllegalArgumentException("Unknown ConnectionAcceptorType: " + connectionAcceptorType);
        }
        try (ServerContext ctx = serverWithEmptyAsyncContextService(builder, useImmediate);
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                     .protocols(protocol.config).buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            String ctx1 = makeClientRequestWithId(connection, "1");
            String ctx2 = makeClientRequestWithId(connection, "2");
            assertThat("Server must have difference context for each request", ctx1, is(not(equalTo(ctx2))));
        }
    }

    private static void makeClientRequestWithIdExpectError(BlockingHttpConnection connection,
                                                  String requestId) {
        HttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        assertThrows(Exception.class, () -> connection.request(request));
    }

    private static void makeClientRequestWithIdExpectCancel(BlockingHttpConnection connection,
                                                           String requestId, CountDownLatch latch) throws Exception {
        HttpConnection streamingConnection = connection.asConnection();
        HttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        Future<HttpResponse> responseFuture = streamingConnection.request(request)
                .toFuture();
        latch.await();
        responseFuture.cancel(true);
        assertThrows(Exception.class, () -> responseFuture.get());
    }

    private static String makeClientRequestWithId(BlockingHttpConnection connection,
                                                  String requestId) throws Exception {
        HttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        HttpResponse response = connection.request(request);
        assertEquals(OK, response.status());
        assertTrue(request.headers().contains(REQUEST_ID_HEADER, requestId));
        return response.payloadBody().toString(UTF_8);
    }

    private static final class AsyncContextLifecycleObserver implements HttpLifecycleObserver, HttpExchangeObserver,
                                                                        HttpRequestObserver, HttpResponseObserver {

        private final AtomicReference<ContextMap> currentContext;
        private final Queue<Throwable> errorQueue;
        private final ResponseType responseType;

        @Nullable
        private CharSequence requestId;

        AsyncContextLifecycleObserver(AtomicReference<ContextMap> currentContext, Queue<Throwable> errorQueue,
                                      ResponseType responseType) {
            this.currentContext = currentContext;
            this.errorQueue = errorQueue;
            this.responseType = responseType;
        }

        @Override
        public HttpExchangeObserver onNewExchange() {
            AsyncContextHttpFilterVerifier.assertSameContext(currentContext.get(), errorQueue);
            return this;
        }

        @Override
        public void onConnectionSelected(ConnectionInfo info) {
            AsyncContextHttpFilterVerifier.assertSameContext(currentContext.get(), errorQueue);
        }

        @Override
        public HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
            requestId = requestMetaData.headers().getAndRemove(REQUEST_ID_HEADER);
            if (requestId != null) {
                AsyncContext.put(K1, requestId);
            }
            return this;
        }

        @Override
        public void onRequestDataRequested(final long n) {
            assertAsyncContext();
        }

        @Override
        public void onRequestData(Buffer data) {
            assertAsyncContext();
        }

        @Override
        public void onRequestTrailers(HttpHeaders trailers) {
            assertAsyncContext();
        }

        @Override
        public void onRequestComplete() {
            assertAsyncContext();
        }

        @Override
        public void onRequestError(Throwable cause) {
            errorQueue.add(new AssertionError("Unexpected onRequestError", cause));
        }

        @Override
        public void onRequestCancel() {
             if (ResponseType.ERROR_ON_RESPONSE_BODY == responseType ||
                    ResponseType.CANCEL_ON_RESPONSE_BODY == responseType) {
                assertAsyncContext();
            } else {
                errorQueue.add(new AssertionError("Unexpected onRequestCancel"));
            }
        }

        @Override
        public HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
            if (ResponseType.CANCEL_ON_RESPONSE == responseType) {
                errorQueue.add(new AssertionError("Unexpected onResponse"));
            } else {
                assertAsyncContext();
            }
            return this;
        }

        @Override
        public void onResponseDataRequested(final long n) {
            assertAsyncContext();
        }

        @Override
        public void onResponseData(final Buffer data) {
            assertAsyncContext();
        }

        @Override
        public void onResponseTrailers(final HttpHeaders trailers) {
            assertAsyncContext();
        }

        @Override
        public void onResponseComplete() {
            // in the whole error response case a filter converts the error to a 5xx so we do get a body.
            if (ResponseType.SUCCESS == responseType || ResponseType.ERROR_ON_RESPONSE == responseType) {
                assertAsyncContext();
            } else {
                errorQueue.add(new AssertionError("Unexpected onResponseComplete"));
            }
        }

        @Override
        public void onResponseError(Throwable cause) {
            // used for both response head and response body cancellation
            if (ResponseType.ERROR_ON_RESPONSE == responseType ||
                    ResponseType.ERROR_ON_RESPONSE_BODY == responseType) {
                assertAsyncContext();
            } else {
                errorQueue.add(new AssertionError("Unexpected onResponseError", cause));
            }
        }

        @Override
        public void onResponseCancel() {
            // used for both response head and response body cancellation
            if (ResponseType.CANCEL_ON_RESPONSE_BODY == responseType ||
                    ResponseType.CANCEL_ON_RESPONSE == responseType) {
                assertAsyncContext();
            } else {
                errorQueue.add(new AssertionError("Unexpected onResponseCancel"));
            }
        }

        @Override
        public void onExchangeFinally() {
            assertAsyncContext();
        }

        private void assertAsyncContext() {
            if (requestId == null) {
                errorQueue.add(new AssertionError("Unexpected requestId == null"));
                return;
            }
            AsyncContextHttpFilterVerifier.assertAsyncContext(K1, requestId, errorQueue);
            AsyncContextHttpFilterVerifier.assertSameContext(currentContext.get(), errorQueue);
        }
    }

    private static final class CaptureRequestContextHttpServerBuilder extends DefaultHttpServerBuilder {

        private final AtomicReference<ContextMap> currentContext;

        CaptureRequestContextHttpServerBuilder(SocketAddress address, AtomicReference<ContextMap> currentContext) {
            super(address);
            this.currentContext = currentContext;
        }

        @Override
        Stream<StreamingHttpServiceFilterFactory> alterFilters(Stream<StreamingHttpServiceFilterFactory> filters) {
            return Stream.concat(Stream.of(new CaptureRequestHttpContextServiceFilter()), super.alterFilters(filters));
        }

        private final class CaptureRequestHttpContextServiceFilter implements StreamingHttpServiceFilterFactory {

            @Override
            public StreamingHttpServiceFilter create(StreamingHttpService service) {
                return new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                                StreamingHttpResponseFactory responseFactory) {
                        // Captures the very first AsyncContext state in the filter chain.
                        currentContext.set(AsyncContext.context());
                        return delegate().handle(ctx, request, responseFactory);
                    }
                };
            }
        }
    }
}
