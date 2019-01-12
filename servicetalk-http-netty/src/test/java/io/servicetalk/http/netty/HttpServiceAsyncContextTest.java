/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.net.InetAddress.getLoopbackAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpServiceAsyncContextTest {
    private static final Key<CharSequence> K1 = Key.newKey("k1");
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    private static final InetSocketAddress LOCAL_0 = new InetSocketAddress(getLoopbackAddress(), 0);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutionContextRule immediateExecutor = ExecutionContextRule.immediate();

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
                                .setMaxPipelinedRequests(numRequests);
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
    public void contextPreservedOverFilterBoundaries() throws Exception {
        StreamingHttpService service = new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory factory) {
                request.payloadBody().ignoreElements().subscribe();
                CharSequence requestId = AsyncContext.get(K1);
                if (requestId != null) {
                    StreamingHttpResponse response = factory.ok();
                    response.headers().set(REQUEST_ID_HEADER, requestId);
                    return success(response);
                } else {
                    return success(factory.newResponse(INTERNAL_SERVER_ERROR));
                }
            }
        };
        StreamingHttpService filter = new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory factory) {
                CharSequence requestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
                if (requestId != null) {
                    AsyncContext.put(K1, requestId);
                }
                return service.handle(ctx, request, factory);
            }
        };
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        ServerContext ctx = compositeCloseable.append(HttpServers.forAddress(LOCAL_0)
                .listenStreamingAndAwait(filter));
        try {
            StreamingHttpConnection connection = compositeCloseable.append(new DefaultHttpConnectionBuilder<SocketAddress>()
                    .buildStreaming(ctx.listenAddress()).toFuture().get());
            makeClientRequestWithId(connection, "1");
        } finally {
            compositeCloseable.close();
        }
    }

    @Test
    public void connectionContextFilterContextDoesNotLeak() throws Exception {
        StreamingHttpService service = newEmptyAsyncContextService(false);
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        ServerContext ctx = compositeCloseable.append(HttpServers.forAddress(LOCAL_0)
                .appendConnectionAcceptorFilter(original -> new ConnectionAcceptorFilter(context -> {
                    AsyncContext.put(K1, "v1");
                    return success(true);
                }))
                .listenStreamingAndAwait(service));
        try {
            StreamingHttpConnection connection = compositeCloseable.append(
                    new DefaultHttpConnectionBuilder<SocketAddress>().buildStreaming(ctx.listenAddress())
                            .toFuture().get());
            makeClientRequestWithId(connection, "1");
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
        response.payloadBody().ignoreElements().subscribe();
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
