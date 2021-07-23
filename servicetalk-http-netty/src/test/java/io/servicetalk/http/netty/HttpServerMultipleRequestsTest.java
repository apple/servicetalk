/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyIoThreadFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.cached;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerMultipleRequestsTest {
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");

    @RegisterExtension
    final ExecutionContextExtension serverContext = cached(new NettyIoThreadFactory("server-io"));
    @RegisterExtension
    final ExecutionContextExtension clientContext = cached(new NettyIoThreadFactory("client-io"));

    @Disabled("https://github.com/apple/servicetalk/issues/981")
    @Test
    void consumeOfRequestBodyDoesNotCloseConnection() throws Exception {
        StreamingHttpService service = (ctx, request, responseFactory) -> {
            request.messageBody().ignoreElements().subscribe();

            CharSequence requestId = request.headers().get(REQUEST_ID_HEADER);
            if (requestId != null) {
                StreamingHttpResponse response = responseFactory.ok();
                response.headers().set(REQUEST_ID_HEADER, requestId);
                return succeeded(response);
            } else {
                return succeeded(responseFactory.newResponse(BAD_REQUEST));
            }
        };
        final int concurrency = 10;
        final int numRequests = 10;
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        ServerContext ctx = compositeCloseable.append(HttpServers.forAddress(localAddress(0))
                .ioExecutor(serverContext.ioExecutor())
                .executor(serverContext.executor())
                .executionStrategy(defaultStrategy())
                .listenStreamingAndAwait(service));
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            CountDownLatch latch = new CountDownLatch(concurrency);
            for (int i = 0; i < concurrency; ++i) {
                final int finalI = i;
                executorService.execute(() -> {
                    try {
                        StreamingHttpClient client = compositeCloseable.append(
                                HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                                        .protocols(h1().maxPipelinedRequests(numRequests).build())
                                        .ioExecutor(clientContext.ioExecutor())
                                        .executor(clientContext.executor())
                                        .executionStrategy(defaultStrategy())
                                        .buildStreaming());
                        ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/"))
                                .toFuture().get();
                        compositeCloseable.append(connection);
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

    private static void makeClientRequestWithId(StreamingHttpConnection connection, String requestId)
            throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertEquals(OK, response.status());
        assertTrue(request.headers().contains(REQUEST_ID_HEADER, requestId));
        response.messageBody().ignoreElements().subscribe();
    }
}
