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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractHttpServiceAsyncContextTest {

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
                    SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                            forResolvedAddress(serverHostAndPort(ctx))
                                    .protocols(h1().maxPipelinedRequests(numRequests).build());
                    try (StreamingHttpClient client = (!useImmediate ? clientBuilder :
                            clientBuilder.executionStrategy(noOffloadsStrategy())).buildStreaming()) {
                        try (StreamingHttpConnection connection = client.reserveConnection(client.get("/"))
                                .toFuture().get()) {
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

    @Test
    void contextPreservedOverFilterBoundariesOffloaded() throws Exception {
        contextPreservedOverFilterBoundaries(false, false, false);
    }

    @Test
    void contextPreservedOverFilterBoundariesOffloadedAsyncFilter() throws Exception {
        contextPreservedOverFilterBoundaries(false, true, false);
    }

    final void contextPreservedOverFilterBoundaries(boolean useImmediate, boolean asyncFilter,
                                                    boolean asyncService) throws Exception {
        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();

        try (ServerContext ctx = serverWithService(HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(filterFactory(useImmediate, asyncFilter, errorQueue)),
                useImmediate, asyncService);

             StreamingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx)).buildStreaming();

             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            makeClientRequestWithId(connection, "1");
            assertThat("Error queue is not empty!", errorQueue, empty());
        }
    }

    private StreamingHttpServiceFilterFactory filterFactory(final boolean useImmediate,
                                                                   final boolean asyncFilter,
                                                                   final Queue<Throwable> errorQueue) {
        return service -> new StreamingHttpServiceFilter(service) {
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
                            assertAsyncContext(requestId, errorQueue);
                            return resp.transformMessageBody(pub ->
                                    pub.afterSubscriber(assertAsyncContextSubscriber(requestId, errorQueue)));
                        }
                );
            }
        };
    }

    private Supplier<PublisherSource.Subscriber<Object>> assertAsyncContextSubscriber(
            @Nullable final CharSequence requestId, final Queue<Throwable> errorQueue) {

        return () -> new PublisherSource.Subscriber<Object>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                assertAsyncContext(requestId, errorQueue);
            }

            @Override
            public void onNext(final Object o) {
                assertAsyncContext(requestId, errorQueue);
            }

            @Override
            public void onError(final Throwable throwable) {
                assertAsyncContext(requestId, errorQueue);
            }

            @Override
            public void onComplete() {
                assertAsyncContext(requestId, errorQueue);
            }
        };
    }

    private static void assertAsyncContext(@Nullable CharSequence requestId, Queue<Throwable> errorQueue) {
        CharSequence k1Value = AsyncContext.get(K1);
        if (requestId != null && !requestId.equals(k1Value)) {
            errorQueue.add(new AssertionError("AsyncContext[" + K1 + "]=[" + k1Value +
                    "], expected=[" + requestId + "]"));
        }
    }

    @Test
    void connectionAcceptorContextDoesNotLeakOffload() throws Exception {
        connectionAcceptorContextDoesNotLeak(false);
    }

    final void connectionAcceptorContextDoesNotLeak(boolean serverUseImmediate) throws Exception {
        try (ServerContext ctx = serverWithEmptyAsyncContextService(HttpServers.forAddress(localAddress(0))
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(context -> {
                    AsyncContext.put(K1, "v1");
                    return completed();
                })), serverUseImmediate);

             StreamingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx)).buildStreaming();

             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            makeClientRequestWithId(connection, "1");
            makeClientRequestWithId(connection, "2");
        }
    }

    private static void makeClientRequestWithId(StreamingHttpConnection connection, String requestId)
            throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertEquals(OK, response.status());
        assertTrue(request.headers().contains(REQUEST_ID_HEADER, requestId));
        response.messageBody().ignoreElements().toFuture().get();
    }
}
