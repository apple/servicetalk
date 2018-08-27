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
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethods;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;
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
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.lang.Thread.NORM_PRIORITY;
import static java.net.InetAddress.getLoopbackAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpServerMultipleRequestsTest {
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    @Rule
    public final ExecutionContextRule serverExecution =
            cached(new DefaultThreadFactory("server-io", true, NORM_PRIORITY));
    @Rule
    public final ExecutionContextRule clientExecution =
            cached(new DefaultThreadFactory("client-io", true, NORM_PRIORITY));
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void consumeOfRequestBodyDoesNotCloseConnection() throws Exception {
        HttpService service = new HttpService() {
            @Override
            public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                 final HttpRequest<HttpPayloadChunk> request) {
                request.getPayloadBody().ignoreElements().subscribe();

                CharSequence requestId = request.getHeaders().get(REQUEST_ID_HEADER);
                if (requestId != null) {
                    HttpResponse<HttpPayloadChunk> response = newResponse(OK);
                    response.getHeaders().set(REQUEST_ID_HEADER, requestId);
                    return success(response);
                } else {
                    return success(newResponse(BAD_REQUEST));
                }
            }
        };
        final int concurrency = 10;
        final int numRequests = 10;
        CompositeCloseable compositeCloseable = AsyncCloseables.newCompositeCloseable();
        ServerContext ctx = compositeCloseable.append(new DefaultHttpServerStarter()
                .start(serverExecution, new InetSocketAddress(getLoopbackAddress(), 0), service).toFuture().get());
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(concurrency);
            CountDownLatch latch = new CountDownLatch(concurrency);
            for (int i = 0; i < concurrency; ++i) {
                final int finalI = i;
                executorService.execute(() -> {
                    try {
                        HttpConnection connection = compositeCloseable.append(
                                new DefaultHttpConnectionBuilder<SocketAddress>()
                                        .setMaxPipelinedRequests(numRequests)
                                        .build(clientExecution, ctx.getListenAddress()).toFuture().get());
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

    private static void makeClientRequestWithId(HttpRequester connection, String requestId)
            throws ExecutionException, InterruptedException {
        HttpRequest<HttpPayloadChunk> request = newRequest(HttpRequestMethods.GET, "/");
        request.getHeaders().set(REQUEST_ID_HEADER, requestId);
        HttpResponse<HttpPayloadChunk> response = connection.request(request).toFuture().get();
        assertEquals(OK, response.getStatus());
        assertTrue(request.getHeaders().contains(REQUEST_ID_HEADER, requestId));
        response.getPayloadBody().ignoreElements().subscribe();
    }
}
