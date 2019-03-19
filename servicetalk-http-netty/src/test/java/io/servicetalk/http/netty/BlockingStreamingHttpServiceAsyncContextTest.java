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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpServerResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpConnectionBuilder;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.HttpServiceFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.SocketAddress;
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

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlockingStreamingHttpServiceAsyncContextTest {
    private static final Key<CharSequence> K1 = Key.newKey("k1");
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    private static final String IO_THREAD_PREFIX = "servicetalk-global-io-executor-";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void newRequestsGetFreshContext() throws Exception {
        newRequestsGetFreshContext(false);
    }

    private static void newRequestsGetFreshContext(boolean useImmediate) throws Exception {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final int concurrency = 10;
        final int numRequests = 10;
        try (ServerContext ctx = HttpServers.forAddress(localAddress(0))
                .listenBlockingStreamingAndAwait(newEmptyAsyncContextService(useImmediate))) {

            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            CountDownLatch latch = new CountDownLatch(concurrency);
            for (int i = 0; i < concurrency; ++i) {
                final int finalI = i;
                executorService.execute(() -> {
                    HttpConnectionBuilder<SocketAddress> connectionBuilder =
                            new DefaultHttpConnectionBuilder<SocketAddress>()
                                    .maxPipelinedRequests(numRequests);
                    try (StreamingHttpConnection connection = (!useImmediate ? connectionBuilder :
                            connectionBuilder.executionStrategy(noOffloadsStrategy()))
                            .buildStreaming(ctx.listenAddress()).toFuture().get()) {

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
        }
    }

    @Test
    public void contextPreservedOverFilterBoundariesOffloaded() throws Exception {
        contextPreservedOverFilterBoundaries(false, false, false);
    }

    @Test
    public void contextPreservedOverFilterBoundariesOffloadedAsyncFilter() throws Exception {
        contextPreservedOverFilterBoundaries(false, true, false);
    }

    private static void contextPreservedOverFilterBoundaries(boolean useImmediate, boolean asyncFilter,
                                                             boolean asyncService) throws Exception {
        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();

        try (ServerContext ctx = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(filterFactory(useImmediate, asyncFilter, errorQueue))
                .listenBlockingStreamingAndAwait(service(useImmediate, asyncService));

             StreamingHttpConnection connection = new DefaultHttpConnectionBuilder<SocketAddress>()
                     .buildStreaming(ctx.listenAddress()).toFuture().get()) {

            makeClientRequestWithId(connection, "1");
            assertThat("Error queue is not empty!", errorQueue, empty());
        }
    }

    private static HttpServiceFilterFactory filterFactory(final boolean useImmediate,
                                                          final boolean asyncFilter,
                                                          final Queue<Throwable> errorQueue) {
        return service -> new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory factory) {
                return asyncFilter ? defer(() -> doHandle(ctx, request, factory).subscribeShareContext()) :
                        doHandle(ctx, request, factory);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Do not alter the effective strategy
                return mergeWith;
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
                        pub.doAfterSubscriber(assertAsyncContextSubscriber(requestId, errorQueue)));
                return delegate().handle(ctx, filteredRequest, factory).map(resp -> {
                            assertAsyncContext(requestId, errorQueue);
                            return resp.transformRawPayloadBody(pub ->
                                    pub.doAfterSubscriber(assertAsyncContextSubscriber(requestId, errorQueue)));
                        }
                );
            }
        };
    }

    private static Supplier<Subscriber<Object>> assertAsyncContextSubscriber(@Nullable final CharSequence requestId,
                                                                             final Queue<Throwable> errorQueue) {
        return () -> new Subscriber<Object>() {
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
        };
    }

    private static BlockingStreamingHttpService service(final boolean useImmediate, final boolean asyncService) {
        return new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx,
                               final BlockingStreamingHttpRequest request,
                               final BlockingStreamingHttpServerResponse response) throws Exception {
                doHandle(ctx, request, response);
            }

            // TODO(scott): should only have to specify this once! On the filter or the service.
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return useImmediate ? noOffloadsStrategy() : super.executionStrategy();
            }

            private void doHandle(final HttpServiceContext ctx,
                                  final BlockingStreamingHttpRequest request,
                                  final BlockingStreamingHttpServerResponse response) throws Exception {
                CharSequence requestId = AsyncContext.get(K1);
                // The test forces the server to consume the entire request here which will make sure the
                // AsyncContext is as expected while processing the request data in the filter.
                request.payloadBody().forEach(__ -> { });

                if (currentThread().getName().startsWith(IO_THREAD_PREFIX)) {
                    // verify that we actually are offloaded
                    response.status(INTERNAL_SERVER_ERROR).sendMetaData().close();
                    return;
                }

                CharSequence requestId2 = AsyncContext.get(K1);
                if (requestId2 == requestId && requestId2 != null) {
                    response.setHeader(REQUEST_ID_HEADER, requestId);
                } else {
                    response.status(INTERNAL_SERVER_ERROR)
                            .setHeader(REQUEST_ID_HEADER, String.valueOf(requestId))
                            .setHeader(REQUEST_ID_HEADER + "2", String.valueOf(requestId2));
                }
                response.sendMetaData().close();
            }
        };
    }

    @Test
    public void connectionAcceptorContextDoesNotLeakOffload() throws Exception {
        connectionAcceptorContextDoesNotLeak(false);
    }

    private static void connectionAcceptorContextDoesNotLeak(boolean serverUseImmediate) throws Exception {
        try (ServerContext ctx = HttpServers.forAddress(localAddress(0))
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(context -> {
                    AsyncContext.put(K1, "v1");
                    return completed();
                }))
                .listenBlockingStreamingAndAwait(newEmptyAsyncContextService(serverUseImmediate));

             StreamingHttpConnection connection = new DefaultHttpConnectionBuilder<SocketAddress>()
                     .buildStreaming(ctx.listenAddress()).toFuture().get()) {

            makeClientRequestWithId(connection, "1");
            makeClientRequestWithId(connection, "2");
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

    private static BlockingStreamingHttpService newEmptyAsyncContextService(final boolean noOffloads) {
        return new BlockingStreamingHttpService() {
            @Override
            public void handle(final HttpServiceContext ctx, final BlockingStreamingHttpRequest request,
                    final BlockingStreamingHttpServerResponse response) throws Exception {
                request.payloadBody().forEach(__ -> { });

                if (!AsyncContext.isEmpty()) {
                    response.status(INTERNAL_SERVER_ERROR).sendMetaData().close();
                    return;
                }
                CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                if (requestId != null) {
                    AsyncContext.put(K1, requestId);
                    response.setHeader(REQUEST_ID_HEADER, requestId);
                } else {
                    response.status(BAD_REQUEST);
                }
                response.sendMetaData().close();
            }

            @Override
            public HttpExecutionStrategy executionStrategy() {
                return noOffloads ? noOffloadsStrategy() : defaultStrategy();
            }
        };
    }
}
