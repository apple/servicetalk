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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static java.lang.Integer.parseInt;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HttpConnectionEmptyPayloadTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutionContextRule executionContextRule = immediate();

    @Test
    public void headRequestContentEmpty() throws Exception {
        try (CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable()) {
            final int expectedContentLength = 128;
            byte[] expectedPayload = new byte[expectedContentLength];
            ThreadLocalRandom.current().nextBytes(expectedPayload);
            ServerContext serverContext = closeable.merge(HttpServers
                    .forAddress(localAddress(0))
                    .ioExecutor(executionContextRule.ioExecutor())
                    .executionStrategy(noOffloadsStrategy())
                    .listenStreamingAndAwait(
                            (ctx, req, factory) -> {
                                StreamingHttpResponse resp = factory.ok().payloadBody(from(
                                        HEAD.equals(req.method()) ? EMPTY_BUFFER :
                                                ctx.executionContext().bufferAllocator()
                                                        .newBuffer(expectedContentLength)
                                                        .writeBytes(expectedPayload)));
                                resp.addHeader(CONTENT_LENGTH, String.valueOf(expectedContentLength));
                                return succeeded(resp);
                            }));

            StreamingHttpClient client = closeable.merge(forResolvedAddress(serverHostAndPort(serverContext))
                    .ioExecutor(executionContextRule.ioExecutor())
                    .protocols(h1().maxPipelinedRequests(3).build())
                    .executionStrategy(defaultStrategy(executionContextRule.executor()))
                    .buildStreaming());
            StreamingHttpConnection connection = closeable.merge(
                    client.reserveConnection(client.get("/")).toFuture().get());

            // Request HEAD, GET, HEAD to verify that we can keep reading data despite a HEAD request providing a hint
            // about content-length (and not actually providing the content).
            Single<StreamingHttpResponse> response1Single = connection.request(connection.newRequest(HEAD, "/"));
            Single<StreamingHttpResponse> response2Single = connection.request(connection.get("/"));
            Single<StreamingHttpResponse> response3Single = connection.request(connection.newRequest(HEAD, "/"));

            StreamingHttpResponse response = awaitIndefinitelyNonNull(response1Single);
            assertEquals(OK, response.status());
            CharSequence contentLength = response.headers().get(CONTENT_LENGTH);
            assertNotNull(contentLength);
            assertEquals(expectedContentLength, parseInt(contentLength.toString()));
            // Drain the current response content so we will be able to read the next response.
            response.messageBody().ignoreElements().toFuture().get();

            response = awaitIndefinitelyNonNull(response2Single);
            assertEquals(OK, response.status());
            contentLength = response.headers().get(CONTENT_LENGTH);
            assertNotNull(contentLength);
            assertEquals(expectedContentLength, parseInt(contentLength.toString()));
            Buffer buffer = awaitIndefinitelyNonNull(response.payloadBody().collect(
                    () -> connection.connectionContext().executionContext().bufferAllocator().newBuffer(),
                    Buffer::writeBytes));
            byte[] actualBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(actualBytes);
            assertArrayEquals(expectedPayload, actualBytes);

            response = awaitIndefinitelyNonNull(response3Single);
            assertEquals(OK, response.status());
            contentLength = response.headers().get(CONTENT_LENGTH);
            assertNotNull(contentLength);
            assertEquals(expectedContentLength, parseInt(contentLength.toString()));
            response.messageBody().ignoreElements().toFuture().get();
        }
    }
}
