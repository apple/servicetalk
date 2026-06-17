/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAggregatedPayloadSizeLimitTest {

    private static final int MAX_PAYLOAD = 16;

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor").setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor").setClassLevel(true);

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void serverWithinLimitSucceeds(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startEchoServer(protocol, MAX_PAYLOAD);
             StreamingHttpClient client = newStreamingClient(server, protocol, 0)) {
            HttpClient aggregated = client.asClient();
            String body = repeat('x', MAX_PAYLOAD);
            HttpResponse response = aggregated.request(
                    aggregated.post("/").payloadBody(alloc(client).fromAscii(body))).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(body));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void serverOverLimitRejected(HttpProtocol protocol) throws Exception {
        // The aggregating service reads the whole body then fails at aggregation; cancelling the body publisher
        // tears the transaction down, so the mapped 413 races with the connection teardown. Both outcomes mean
        // the oversized request was rejected. Accept either.
        try (HttpServerContext server = startEchoServer(protocol, MAX_PAYLOAD);
             StreamingHttpClient client = newStreamingClient(server, protocol, 0)) {
            HttpClient aggregated = client.asClient();
            final HttpResponse response;
            try {
                response = aggregated.request(aggregated.post("/")
                        .payloadBody(alloc(client).fromAscii(repeat('x', MAX_PAYLOAD + 1)))).toFuture().get();
            } catch (ExecutionException terminated) {
                return;
            }
            assertThat(response.status(), is(PAYLOAD_TOO_LARGE));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void serverDisabledLimitAllowsLargeAggregatedRequest(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startEchoServer(protocol, 0);
             StreamingHttpClient client = newStreamingClient(server, protocol, 0)) {
            HttpClient aggregated = client.asClient();
            String body = repeat('x', MAX_PAYLOAD * 4);
            HttpResponse response = aggregated.request(
                    aggregated.post("/").payloadBody(alloc(client).fromAscii(body))).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo(body));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void clientOverLimitResponseFails(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startFixedResponseServer(protocol, MAX_PAYLOAD + 1);
             StreamingHttpClient client = newStreamingClient(server, protocol, MAX_PAYLOAD)) {
            HttpClient aggregated = client.asClient();
            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> aggregated.request(aggregated.get("/")).toFuture().get());
            assertThat(e.getCause(), is(instanceOf(PayloadTooLargeException.class)));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void clientStreamingResponseNotAffected(HttpProtocol protocol) throws Exception {
        final int responseSize = MAX_PAYLOAD * 2;
        try (HttpServerContext server = startFixedResponseServer(protocol, responseSize);
             StreamingHttpClient client = newStreamingClient(server, protocol, MAX_PAYLOAD)) {
            // Consume the response as a stream (never aggregated) so the limit must not fire.
            StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
            assertThat(response.status(), is(OK));
            int received = response.payloadBody().map(Buffer::readableBytes)
                    .collect(() -> 0, Integer::sum).toFuture().get();
            assertThat(received, is(responseSize));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void negativeLimitRejected(HttpProtocol protocol) {
        assertThrows(IllegalArgumentException.class,
                () -> newServerBuilder(SERVER_CTX, protocol).maxAggregatedPayloadSize(-1));
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void serverExpandedBodyOverLimitRejected(HttpProtocol protocol) throws Exception {
        // Simulates automatic decompression: a tiny wire body is expanded by a filter into a payload larger than the
        // limit before the aggregating service reads it. The limit must apply to the post-transformation size, not the
        // number of bytes received on the wire. Like serverOverLimitRejected, the 413 may race with teardown.
        try (HttpServerContext server = startEchoServerExpandingBody(protocol, MAX_PAYLOAD, MAX_PAYLOAD + 1);
             StreamingHttpClient client = newStreamingClient(server, protocol, 0)) {
            HttpClient aggregated = client.asClient();
            final HttpResponse response;
            try {
                response = aggregated.request(aggregated.post("/").payloadBody(alloc(client).fromAscii("x")))
                        .toFuture().get();
            } catch (ExecutionException terminated) {
                return;
            }
            assertThat(response.status(), is(PAYLOAD_TOO_LARGE));
        }
    }

    private static HttpServerContext startEchoServer(HttpProtocol protocol, int maxAggregatedPayloadSize)
            throws Exception {
        // Aggregated service: forces aggregation of the incoming request on the server.
        return newServerBuilder(SERVER_CTX, protocol)
                .maxAggregatedPayloadSize(maxAggregatedPayloadSize)
                .listenAndAwait((ctx, request, factory) ->
                        succeeded(factory.ok().payloadBody(request.payloadBody())));
    }

    private static HttpServerContext startEchoServerExpandingBody(HttpProtocol protocol, int maxAggregatedPayloadSize,
                                                                  int expandedSize) throws Exception {
        // A filter that swaps the (tiny) wire body for a larger one before the aggregated service reads it, modelling
        // automatic decompression where the decoded payload is much larger than the bytes received on the wire.
        return newServerBuilder(SERVER_CTX, protocol)
                .maxAggregatedPayloadSize(maxAggregatedPayloadSize)
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory factory) {
                        final BufferAllocator alloc = ctx.executionContext().bufferAllocator();
                        final Buffer[] expanded = new Buffer[expandedSize];
                        for (int i = 0; i < expandedSize; i++) {
                            expanded[i] = alloc.fromAscii("x");
                        }
                        return delegate().handle(ctx, request.payloadBody(Publisher.from(expanded)), factory);
                    }
                })
                .listenAndAwait((ctx, request, factory) ->
                        succeeded(factory.ok().payloadBody(request.payloadBody())));
    }

    private static HttpServerContext startFixedResponseServer(HttpProtocol protocol, int responseSize)
            throws Exception {
        return newServerBuilder(SERVER_CTX, protocol).listenStreamingAndAwait((ctx, request, factory) -> {
            BufferAllocator alloc = ctx.executionContext().bufferAllocator();
            Buffer[] chunks = new Buffer[responseSize];
            for (int i = 0; i < responseSize; i++) {
                chunks[i] = alloc.fromAscii("x");
            }
            return succeeded(factory.ok().payloadBody(Publisher.from(chunks)));
        });
    }

    private static StreamingHttpClient newStreamingClient(HttpServerContext server, HttpProtocol protocol,
                                                          int maxAggregatedPayloadSize) {
        return newClientBuilder(server, CLIENT_CTX, protocol)
                .maxAggregatedPayloadSize(maxAggregatedPayloadSize)
                .buildStreaming();
    }

    private static BufferAllocator alloc(StreamingHttpClient client) {
        return client.executionContext().bufferAllocator();
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
