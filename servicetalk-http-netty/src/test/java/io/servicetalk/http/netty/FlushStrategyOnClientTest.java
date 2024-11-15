/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.FlushStrategyOnServerTest.OutboundWriteEventsInterceptor;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.FlushStrategyOnServerTest.assertFlushOnEach;
import static io.servicetalk.http.netty.FlushStrategyOnServerTest.assertFlushOnEnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FlushStrategyOnClientTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @Test
    void sequentialPipelinedRequestsHaveTheirOwnStrategy() throws Exception {
        OutboundWriteEventsInterceptor interceptor = new OutboundWriteEventsInterceptor();
        BlockingQueue<HttpRequest> receivedRequests = new LinkedBlockingQueue<>();
        CountDownLatch canReturnResponse = new CountDownLatch(1);
        try (HttpServerContext serverContext = BuilderUtils.newServerBuilder(SERVER_CTX)
                .listenBlockingAndAwait(((ctx, request, responseFactory) -> {
                    receivedRequests.add(request);
                    canReturnResponse.await();
                    return responseFactory.ok();
                }));
             StreamingHttpClient client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX)
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(interceptor))
                     .buildStreaming();
             ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            // First request goes through aggregation to enforce "flushOnEnd()"
            Future<StreamingHttpResponse> firstResponse = connection.request(newRequest(connection, "/first")
                    .toRequest().toFuture().get().toStreamingRequest()).toFuture();
            assertThat(receivedRequests.take().requestTarget(), is("/first"));
            assertFlushOnEnd(interceptor);

            // Second request is sent as streaming and expected to use "flushOnEach()" strategy
            Future<StreamingHttpResponse> secondResponse = connection.request(newRequest(connection, "/second"))
                    .toFuture();
            assertFlushOnEach(interceptor);
            canReturnResponse.countDown();
            assertThat(receivedRequests.take().requestTarget(), is("/second"));

            assertResponse(firstResponse);
            assertResponse(secondResponse);
        }
    }

    private static StreamingHttpRequest newRequest(StreamingHttpConnection connection, String path) {
        BufferAllocator alloc = connection.executionContext().bufferAllocator();
        return connection.post(path)
                .payloadBody(Publisher.from(alloc.fromAscii("foo"), alloc.fromAscii("bar")));
    }

    private static void assertResponse(Future<StreamingHttpResponse> responseFuture) throws Exception {
        StreamingHttpResponse response = responseFuture.get();
        assertThat(response.status(), is(OK));
        Buffer payload = response.toResponse().toFuture().get().payloadBody();
        assertThat(payload.readableBytes(), is(0));
    }
}
