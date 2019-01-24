/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionAcceptorFilter;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscription;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.deferShareContext;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.lang.Thread.currentThread;
import static java.net.InetAddress.getLoopbackAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpServiceAsyncContextTest {
    private static final Key<CharSequence> K1 = Key.newKey("k1");
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    private static final String IO_THREAD_PREFIX = "iothread-";
    private static final InetSocketAddress LOCAL_0 = new InetSocketAddress(getLoopbackAddress(), 0);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutionContextRule immediateExecutor = ExecutionContextRule.immediate(
            new IoThreadFactory(IO_THREAD_PREFIX));

    @Test
    public void newRequestsGetFreshContext() throws Exception {
        newRequestsGetFreshContext(false);
    }

    @Test
    public void newRequestsGetFreshContextImmediate() throws Exception {
        newRequestsGetFreshContext(true);
    }

    private void newRequestsGetFreshContext(boolean useImmediate) throws Exception {
        StreamingHttpService service = newEmptyAsyncContextService(useImmediate);
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        HttpServerBuilder serverBuilder = HttpServers.forAddress(LOCAL_0);
        ServerContext ctx = serverBuilder.listenStreamingAndAwait(service);

        ExecutorService executorService = Executors.newCachedThreadPool();
        final int concurrency = 10;
        final int numRequests = 10;
        try {
            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            CountDownLatch latch = new CountDownLatch(concurrency);
            for (int i = 0; i < concurrency; ++i) {
                final int finalI = i;
                executorService.execute(() -> {
                    try {
                        HttpConnectionBuilder<SocketAddress> connectionBuilder =
                                new DefaultHttpConnectionBuilder<SocketAddress>()
                                .maxPipelinedRequests(numRequests);
                        StreamingHttpConnection connection = (useImmediate ?
                                connectionBuilder.ioExecutor(immediateExecutor.ioExecutor())
                                .executor(immediateExecutor.executor())
                                .buildStreaming(ctx.listenAddress()) :
                                connectionBuilder.buildStreaming(ctx.listenAddress())).toFuture().get();
                        barrier.await();
                        for (int x = 0; x < numRequests; ++x) {
                            makeClientRequestWithId(connection, "thread=" + finalI + " request=" + x);
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
        } finally {
            executorService.shutdown();
            compositeCloseable.close();
        }
    }

    @Test
    public void contextPreservedOverFilterBoundariesOffloaded() throws Exception {
        contextPreservedOverFilterBoundaries(false, false, false);
        contextPreservedOverFilterBoundaries(false, false, true);
        contextPreservedOverFilterBoundaries(false, true, false);
        contextPreservedOverFilterBoundaries(false, true, true);
    }

    @Test
    public void contextPreservedOverFilterBoundariesNoOffload() throws Exception {
        contextPreservedOverFilterBoundaries(true, false, false);
        contextPreservedOverFilterBoundaries(true, false, true);
        contextPreservedOverFilterBoundaries(true, true, false);
        contextPreservedOverFilterBoundaries(true, true, true);
    }

    private void contextPreservedOverFilterBoundaries(boolean useImmediate,
                                                      boolean asyncFilter, boolean asyncService) throws Exception {
        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();
        StreamingHttpService service = new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return asyncService ? deferShareContext(() -> doHandle(ctx, request, responseFactory)) :
                        doHandle(ctx, request, responseFactory);
            }

            // TODO(scott): should only have to specify this once! On the filter or the service.
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return useImmediate ? HttpExecutionStrategies.noOffloadsStrategy() : super.executionStrategy();
            }

            private Single<StreamingHttpResponse> doHandle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory factory) {
                CharSequence requestId = AsyncContext.get(K1);
                // The test doesn't wait until the request body is consumed and only cares when the response is received
                // from the client. So we force the server to consume the entire request here which will make sure the
                // AsyncContext is as expected while processing the request data in the filter below.
                return request.payloadBody().ignoreElements()
                        .concatWith(Single.defer(() -> {
                            if (useImmediate && !currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                                // verify that if we expect to be offloaded, that we actually are
                                return success(factory.internalServerError());
                            }
                            CharSequence requestId2 = AsyncContext.get(K1);
                            if (requestId2 == requestId && requestId2 != null) {
                                StreamingHttpResponse response = factory.ok();
                                response.headers().set(REQUEST_ID_HEADER, requestId);
                                return success(response);
                            } else {
                                StreamingHttpResponse response = factory.internalServerError();
                                response.headers().set(REQUEST_ID_HEADER, String.valueOf(requestId));
                                response.headers().set(REQUEST_ID_HEADER + "2", String.valueOf(requestId2));
                                return success(response);
                            }
                        }));
            }
        };
        StreamingHttpService filter = new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory factory) {
                return asyncFilter ? deferShareContext(() -> doHandle(ctx, request, factory)) :
                        doHandle(ctx, request, factory);
            }

            // TODO(scott): should only have to specify this once! On the filter or the service.
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return useImmediate ? HttpExecutionStrategies.noOffloadsStrategy() : super.executionStrategy();
            }

            private Single<StreamingHttpResponse> doHandle(final HttpServiceContext ctx,
                                                           final StreamingHttpRequest request,
                                                           final StreamingHttpResponseFactory factory) {
                if (useImmediate && !currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                    // verify that if we expect to be offloaded, that we actually are
                    return success(factory.internalServerError());
                }
                CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                if (requestId != null) {
                    AsyncContext.put(K1, requestId);
                }
                final StreamingHttpRequest filteredRequest = request.transformRawPayloadBody(pub ->
                        pub.doAfterSubscriber(() -> new org.reactivestreams.Subscriber<Object>() {
                            @Override
                            public void onSubscribe(final Subscription subscription) {
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
                        }));
                return service.handle(ctx, filteredRequest, factory).map(resp -> {
                            assertAsyncContext(requestId, errorQueue);
                            return resp.transformRawPayloadBody(pub ->
                                    pub.doAfterSubscriber(() -> new org.reactivestreams.Subscriber<Object>() {
                                        @Override
                                        public void onSubscribe(final Subscription subscription) {
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
                                    }));
                        }
                );
            }
        };
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        HttpServerBuilder serverBuilder = HttpServers.forAddress(LOCAL_0);
        ServerContext ctx = compositeCloseable.append(serverBuilder
                .ioExecutor(immediateExecutor.ioExecutor())
                .listenStreamingAndAwait(filter));
        try {
            StreamingHttpConnection connection = compositeCloseable.append(
                    new DefaultHttpConnectionBuilder<SocketAddress>()
                    .buildStreaming(ctx.listenAddress()).toFuture().get());
            makeClientRequestWithId(connection, "1");
            assertThat("Error queue is not empty!", errorQueue, empty());
        } finally {
            compositeCloseable.close();
        }
    }

    @Test
    public void connectionContextFilterContextDoesNotLeakImmediate() throws Exception {
        connectionContextFilterContextDoesNotLeak(true);
    }

    @Test
    public void connectionContextFilterContextDoesNotLeakOffload() throws Exception {
        connectionContextFilterContextDoesNotLeak(false);
    }

    private void connectionContextFilterContextDoesNotLeak(boolean serverUseImmediate) throws Exception {
        StreamingHttpService service = newEmptyAsyncContextService(serverUseImmediate);
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        ServerContext ctx = compositeCloseable.append(HttpServers.forAddress(LOCAL_0)
                .appendConnectionAcceptorFilter(original -> new ConnectionAcceptorFilter(context -> {
                    AsyncContext.put(K1, "v1");
                    return success(true);
                }))
                .listenStreamingAndAwait(service));
        try {
            StreamingHttpConnection connection = compositeCloseable.append(
                    new DefaultHttpConnectionBuilder<SocketAddress>()
                            .buildStreaming(ctx.listenAddress())
                            .toFuture().get());
            makeClientRequestWithId(connection, "1");
            makeClientRequestWithId(connection, "2");
        } finally {
            compositeCloseable.close();
        }
    }

    private static void makeClientRequestWithId(StreamingHttpRequester connection, String requestId)
            throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertEquals(OK, response.status());
        assertTrue(request.headers().contains(REQUEST_ID_HEADER, requestId));
        response.payloadBodyAndTrailers().ignoreElements().toFuture().get();
    }

    private static void assertAsyncContext(@Nullable CharSequence requestId, Queue<Throwable> errorQueue) {
        Object k1Value = AsyncContext.get(K1);
        if (requestId != null && !requestId.equals(k1Value)) {
            errorQueue.add(new AssertionError("AsyncContext[" + K1 + "]=[" + k1Value +
                    "], expected=[" + requestId + "]"));
        }
    }

    private static StreamingHttpService newEmptyAsyncContextService(final boolean useImmediate) {
        HttpExecutionStrategy strategy = useImmediate ? noOffloadsStrategy() : defaultStrategy();
        return new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory factory) {
                request.payloadBody().ignoreElements().subscribe();

                if (!AsyncContext.isEmpty()) {
                    return success(factory.internalServerError());
                }
                CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                if (requestId != null) {
                    AsyncContext.put(K1, requestId);
                    StreamingHttpResponse response = factory.ok();
                    response.headers().set(REQUEST_ID_HEADER, requestId);
                    return success(response);
                } else {
                    return success(factory.newResponse(BAD_REQUEST));
                }
            }

            @Override
            public HttpExecutionStrategy executionStrategy() {
                return strategy;
            }
        };
    }
}
