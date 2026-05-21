/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.PayloadSizeLimitingHttpServiceFilter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class PayloadSizeLimitingHttpServiceFilterTest {

    private static final int MAX_PAYLOAD = 16;

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor").setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor").setClassLevel(true);

    @Test
    void verifyAsyncContext() throws Exception {
        verifyServerFilterAsyncContextVisibility(new PayloadSizeLimitingHttpServiceFilter(Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void withinLimitSucceeds(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            BufferAllocator alloc = client.executionContext().bufferAllocator();
            HttpClient aggregated = client.asClient();
            HttpResponse response = aggregated.request(
                    aggregated.post("/").payloadBody(alloc.fromAscii("hello"))).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void contentLengthAtMaxAllowed(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            BufferAllocator alloc = client.executionContext().bufferAllocator();
            HttpClient aggregated = client.asClient();
            String body = repeat('x', MAX_PAYLOAD);
            HttpResponse response = aggregated.request(
                    aggregated.post("/").payloadBody(alloc.fromAscii(body))).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(body));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void contentLengthOverLimitTerminatesTransaction(HttpProtocol protocol) throws Exception {
        // The filter cancels (rather than drains) the oversized body, so the close handlers tear down
        // the transaction before the response is delivered. Whether the client sees the connection
        // teardown or the 413 first is a race — under macOS we typically see the teardown
        // (ExecutionException), under Linux CI we sometimes see the clean 413. Either outcome is
        // correct: the transaction was rejected. Accept both.
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            BufferAllocator alloc = client.executionContext().bufferAllocator();
            HttpClient aggregated = client.asClient();
            final HttpResponse response;
            try {
                response = aggregated.request(
                        aggregated.post("/").payloadBody(alloc.fromAscii(repeat('x', MAX_PAYLOAD + 1))))
                        .toFuture().get();
            } catch (ExecutionException expected) {
                // Connection/stream torn down before the response surfaced. Also acceptable.
                return;
            }
            assertThat(response.status(), is(PAYLOAD_TOO_LARGE));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void streamingBodyOverLimitRejectedWith413(HttpProtocol protocol) throws Exception {
        // No Content-Length: the streaming limiter must still fire once accumulated bytes exceed max.
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            BufferAllocator alloc = client.executionContext().bufferAllocator();
            Buffer[] chunks = new Buffer[MAX_PAYLOAD + 1];
            for (int i = 0; i < chunks.length; i++) {
                chunks[i] = alloc.fromAscii("x");
            }
            StreamingHttpRequest request = client.post("/").payloadBody(Publisher.from(chunks));
            request.headers().remove(CONTENT_LENGTH);

            StreamingHttpResponse response = client.request(request).toFuture().get();
            assertThat(response.status(), is(PAYLOAD_TOO_LARGE));
        }
    }

    @Test
    void expectContinueWithOversizedContentLengthRejectsWithoutSendingBody() throws Exception {
        // Filter must fail without subscribing to the body so the client never receives 100 Continue.
        // Connection survives because the body was never sent.
        try (HttpServerContext server = startServer(HTTP_1);
             StreamingHttpClient client = newClient(server, HTTP_1);
             StreamingHttpConnection conn = client.reserveConnection(client.get("/")).toFuture().get()) {

            BufferAllocator alloc = conn.executionContext().bufferAllocator();
            TestPublisher<Buffer> body = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            StreamingHttpRequest request = conn.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(CONTENT_LENGTH, valueOf(MAX_PAYLOAD + 100))
                    .payloadBody(body);

            BlockingQueue<StreamingHttpResponse> responses = new LinkedBlockingDeque<>();
            conn.request(request).subscribe(responses::add);

            StreamingHttpResponse response = responses.take();
            assertThat(response.status(), is(PAYLOAD_TOO_LARGE));
            assertThat("body was subscribed", body.isSubscribed(), is(false));

            HttpConnection aggregated = conn.asConnection();
            HttpResponse follow = aggregated.request(
                    aggregated.post("/").payloadBody(alloc.fromAscii("ok"))).toFuture().get();
            assertThat(follow.status(), is(OK));
            assertThat(follow.payloadBody().toString(US_ASCII), equalTo("ok"));
        }
    }

    private static HttpServerContext startServer(HttpProtocol protocol) throws Exception {
        // Aggregated handler so an oversized streaming body fails before any response metadata is sent.
        return newServerBuilder(SERVER_CTX, protocol)
                .appendServiceFilter(new PayloadSizeLimitingHttpServiceFilter(MAX_PAYLOAD))
                .listenAndAwait((ctx, request, factory) ->
                        succeeded(factory.ok().payloadBody(request.payloadBody())));
    }

    private static StreamingHttpClient newClient(HttpServerContext server, HttpProtocol protocol) {
        return newClientBuilder(server, CLIENT_CTX, protocol).buildStreaming();
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
