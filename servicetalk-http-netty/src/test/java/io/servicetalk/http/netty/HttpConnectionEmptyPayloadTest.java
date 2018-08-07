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
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequests;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
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
            ServerContext serverContext = closeable.merge(awaitIndefinitelyNonNull(new DefaultHttpServerStarter()
                    .start(executionContextRule, new InetSocketAddress(0), HttpService.fromAsync((ctx, req) ->
                            success(newResponse(OK, req.getMethod() == HEAD ? EMPTY_BUFFER :
                                            ctx.getExecutionContext().getBufferAllocator()
                                                    .newBuffer(expectedContentLength).writeBytes(expectedPayload),
                                    INSTANCE.newHeaders()
                                            .add(CONTENT_LENGTH, String.valueOf(expectedContentLength))))))));

            HttpConnection connection = closeable.merge(awaitIndefinitelyNonNull(new DefaultHttpConnectionBuilder<>()
                    .setMaxPipelinedRequests(3)
                    .build(executionContextRule, serverContext.getListenAddress())));

            // Request HEAD, GET, HEAD to verify that we can keep reading data despite a HEAD request providing a hint
            // about content-length (and not actually providing the content).
            Single<HttpResponse<HttpPayloadChunk>> response1Single = connection.request(HttpRequests.newRequest(HEAD, "/"));
            Single<HttpResponse<HttpPayloadChunk>> response2Single = connection.request(HttpRequests.newRequest(GET, "/"));
            Single<HttpResponse<HttpPayloadChunk>> response3Single = connection.request(HttpRequests.newRequest(HEAD, "/"));

            HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(response1Single);
            assertEquals(OK, response.getStatus());
            CharSequence contentLength = response.getHeaders().get(CONTENT_LENGTH);
            assertNotNull(contentLength);
            assertEquals(expectedContentLength, parseInt(contentLength.toString()));
            // Drain the current response content so we will be able to read the next response.
            awaitIndefinitely(response.getPayloadBody().ignoreElements());

            response = awaitIndefinitelyNonNull(response2Single);
            assertEquals(OK, response.getStatus());
            contentLength = response.getHeaders().get(CONTENT_LENGTH);
            assertNotNull(contentLength);
            assertEquals(expectedContentLength, parseInt(contentLength.toString()));
            Buffer buffer = awaitIndefinitelyNonNull(response.getPayloadBody().reduce(
                    () -> connection.getConnectionContext().getExecutionContext().getBufferAllocator().newBuffer(),
                    (buf, chunk) -> buf.writeBytes(chunk.getContent())));
            byte[] actualBytes = new byte[buffer.getReadableBytes()];
            buffer.readBytes(actualBytes);
            assertArrayEquals(expectedPayload, actualBytes);

            response = awaitIndefinitelyNonNull(response3Single);
            assertEquals(OK, response.getStatus());
            contentLength = response.getHeaders().get(CONTENT_LENGTH);
            assertNotNull(contentLength);
            assertEquals(expectedContentLength, parseInt(contentLength.toString()));
            awaitIndefinitely(response.getPayloadBody().ignoreElements());
        }
    }
}
