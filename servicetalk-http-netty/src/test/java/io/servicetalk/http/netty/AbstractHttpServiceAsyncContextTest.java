/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.assertAsyncContext;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.currentThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    enum ConnectionAcceptorType {
        EARLY,
        LATE,
        DEPRECATED
    }

    protected static final ContextMap.Key<CharSequence> K1 = newKey("k1", CharSequence.class);
    protected static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    protected static final String IO_THREAD_PREFIX = "servicetalk-global-io-executor-";

    abstract ServerContext serverWithEmptyAsyncContextService(HttpServerBuilder serverBuilder,
                                                              boolean useImmediate) throws Exception;

    abstract ServerContext serverWithService(HttpServerBuilder serverBuilder,
                                             boolean useImmediate, boolean asyncService) throws Exception;

    @Test
    void newRequestsGetFreshContext() throws Exception {
        newRequestsGetFreshContext(false);
    }

    final void newRequestsGetFreshContext(boolean useImmediate) throws Exception {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final int concurrency = 10;
        final int numRequests = 10;
        final String k1Value = "value";
        // The service should get an empty AsyncContext regardless of what is done outside the service.
        // There are utilities that may be accessed in a static context or before service initialization that
        // shouldn't pollute the service's AsyncContext.
        AsyncContext.put(K1, k1Value);

        try (ServerContext ctx = serverWithEmptyAsyncContextService(HttpServers.forAddress(localAddress(0)),
                useImmediate)) {

            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            CountDownLatch latch = new CountDownLatch(concurrency);
            for (int i = 0; i < concurrency; ++i) {
                final int finalI = i;
                executorService.execute(() -> {
                    try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                            .protocols(h1().maxPipelinedRequests(numRequests).build()).buildBlocking()) {
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

    @ParameterizedTest(name = "{displayName} [{index}]: useImmediate=false initContextKeyPlace={0} asyncService=false")
    @EnumSource(InitContextKeyPlace.class)
    void contextPreservedOverFilterBoundariesOffloaded(InitContextKeyPlace place) throws Exception {
        contextPreservedOverFilterBoundaries(false, place, false);
    }

    final void contextPreservedOverFilterBoundaries(boolean useImmediate, InitContextKeyPlace place,
                                                    boolean asyncService) throws Exception {
        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));
        switch (place) {
            case LIFECYCLE_OBSERVER:
                builder.lifecycleObserver(new AsyncContextLifecycleObserver(errorQueue));
                break;
            case NON_OFFLOADING_LIFECYCLE_OBSERVER_FILTER:
                builder.appendNonOffloadingServiceFilter(new HttpLifecycleObserverServiceFilter(
                        new AsyncContextLifecycleObserver(errorQueue)));
                break;
            case NON_OFFLOADING_FILTER:
                builder.appendNonOffloadingServiceFilter(filterFactory(useImmediate, false, errorQueue));
                break;
            case NON_OFFLOADING_ASYNC_FILTER:
                builder.appendNonOffloadingServiceFilter(filterFactory(useImmediate, true, errorQueue));
                break;
            case LIFECYCLE_OBSERVER_FILTER:
                builder.appendServiceFilter(new HttpLifecycleObserverServiceFilter(
                        new AsyncContextLifecycleObserver(errorQueue)));
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
        try (ServerContext ctx = serverWithService(builder, useImmediate, asyncService);
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx)).buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            makeClientRequestWithId(connection, "1");
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

    @ParameterizedTest(name = "{displayName} [{index}]: connectionAcceptorType={0}")
    @EnumSource(ConnectionAcceptorType.class)
    void connectionAcceptorContextDoesNotLeakOffloaded(ConnectionAcceptorType type) throws Exception {
        connectionAcceptorContextDoesNotLeak(type, false);
    }

    @SuppressWarnings("deprecation")
    final void connectionAcceptorContextDoesNotLeak(ConnectionAcceptorType connectionAcceptorType,
                                                    boolean serverUseImmediate) throws Exception {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));
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
        try (ServerContext ctx = serverWithEmptyAsyncContextService(builder, serverUseImmediate);
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx)).buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            makeClientRequestWithId(connection, "1");
            makeClientRequestWithId(connection, "2");
        }
    }

    private static void makeClientRequestWithId(BlockingHttpConnection connection, String requestId) throws Exception {
        HttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        HttpResponse response = connection.request(request);
        assertEquals(OK, response.status());
        assertTrue(request.headers().contains(REQUEST_ID_HEADER, requestId));
    }

    private static final class AsyncContextLifecycleObserver implements HttpLifecycleObserver, HttpExchangeObserver,
                                                                        HttpRequestObserver, HttpResponseObserver {

        private final Queue<Throwable> errorQueue;
        @Nullable
        private CharSequence requestId;

        AsyncContextLifecycleObserver(Queue<Throwable> errorQueue) {
            this.errorQueue = errorQueue;
        }

        @Override
        public HttpExchangeObserver onNewExchange() {
            return this;
        }

        @Override
        public void onConnectionSelected(ConnectionInfo info) {
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
            errorQueue.add(new AssertionError("Unexpected onRequestCancel"));
        }

        @Override
        public HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
            assertAsyncContext();
            return this;
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
            assertAsyncContext();
        }

        @Override
        public void onResponseError(Throwable cause) {
            errorQueue.add(new AssertionError("Unexpected onResponseError", cause));
        }

        @Override
        public void onResponseCancel() {
            errorQueue.add(new AssertionError("Unexpected onResponseCancel"));
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
        }
    }
}
