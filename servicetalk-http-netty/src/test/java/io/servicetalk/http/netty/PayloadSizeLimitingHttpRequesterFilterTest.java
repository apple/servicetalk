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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.PayloadSizeLimitingHttpRequesterFilter;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PayloadSizeLimitingHttpRequesterFilterTest {

    private static final int MAX_PAYLOAD = 16;

    // Paths control how the server frames the response.
    private static final String PATH_SMALL = "/small";
    private static final String PATH_AT_MAX_WITH_CL = "/at-max-cl";
    private static final String PATH_LARGE_WITH_CL = "/large-cl";
    private static final String PATH_LARGE_CHUNKED = "/large-chunked";
    private static final String PATH_HEAD_LARGE_CL = "/head-large";
    private static final String PATH_NO_CONTENT_LARGE_CL = "/204-large";
    private static final String PATH_NOT_MODIFIED_LARGE_CL = "/304-large";

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor").setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor").setClassLevel(true);

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void withinLimitSucceeds(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            HttpResponse response = aggregated.request(aggregated.get(PATH_SMALL)).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().toString(US_ASCII), equalTo("hello"));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void contentLengthOverLimitRejectedEarly(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> aggregated.request(aggregated.get(PATH_LARGE_WITH_CL)).toFuture().get());
            assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void chunkedResponseOverLimitRejectedByStreamingLimiter(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> aggregated.request(aggregated.get(PATH_LARGE_CHUNKED)).toFuture().get());
            assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void headResponseWithOversizedContentLengthIsAllowed(HttpProtocol protocol) throws Exception {
        // HEAD responses carry Content-Length describing what GET would return (RFC 9110 §9.3.2).
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            HttpResponse response = aggregated.request(
                    aggregated.newRequest(HEAD, PATH_HEAD_LARGE_CL)).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().readableBytes(), is(0));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void contentLengthAtMaxAllowed(HttpProtocol protocol) throws Exception {
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            HttpResponse response = aggregated.request(aggregated.get(PATH_AT_MAX_WITH_CL)).toFuture().get();
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody().readableBytes(), is(MAX_PAYLOAD));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void noContentResponseWithOversizedContentLengthIsAllowed(HttpProtocol protocol) throws Exception {
        // H2 codec rejects "204 + Content-Length" at the protocol layer, so the filter's exclusion
        // is only reachable on H1.
        assumeTrue(protocol == HTTP_1);
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            HttpResponse response = aggregated.request(
                    aggregated.get(PATH_NO_CONTENT_LARGE_CL)).toFuture().get();
            assertThat(response.status(), is(NO_CONTENT));
            assertThat(response.payloadBody().readableBytes(), is(0));
        }
    }

    @ParameterizedTest
    @EnumSource(HttpProtocol.class)
    void notModifiedResponseWithOversizedContentLengthIsAllowed(HttpProtocol protocol) throws Exception {
        // H1-only for the same reason as the 204 case above.
        assumeTrue(protocol == HTTP_1);
        try (HttpServerContext server = startServer(protocol);
             StreamingHttpClient client = newClient(server, protocol)) {

            HttpClient aggregated = client.asClient();
            HttpResponse response = aggregated.request(
                    aggregated.get(PATH_NOT_MODIFIED_LARGE_CL)).toFuture().get();
            assertThat(response.status(), is(NOT_MODIFIED));
            assertThat(response.payloadBody().readableBytes(), is(0));
        }
    }

    private static HttpServerContext startServer(HttpProtocol protocol) throws Exception {
        return newServerBuilder(SERVER_CTX, protocol)
                .listenStreamingAndAwait((ctx, request, factory) -> {
                    final BufferAllocator alloc = ctx.executionContext().bufferAllocator();
                    final StreamingHttpResponse response;
                    switch (request.requestTarget()) {
                        case PATH_SMALL:
                            response = factory.ok().payloadBody(Publisher.from(alloc.fromAscii("hello")));
                            response.headers().set(CONTENT_LENGTH, valueOf(5));
                            break;
                        case PATH_LARGE_WITH_CL:
                            response = factory.ok().payloadBody(largeBody(alloc, MAX_PAYLOAD + 1));
                            response.headers().set(CONTENT_LENGTH, valueOf(MAX_PAYLOAD + 1));
                            break;
                        case PATH_AT_MAX_WITH_CL:
                            response = factory.ok().payloadBody(largeBody(alloc, MAX_PAYLOAD));
                            response.headers().set(CONTENT_LENGTH, valueOf(MAX_PAYLOAD));
                            break;
                        case PATH_LARGE_CHUNKED:
                            // No Content-Length: H1 sends chunked, H2 omits the header.
                            response = factory.ok().payloadBody(largeBody(alloc, MAX_PAYLOAD + 1));
                            break;
                        case PATH_HEAD_LARGE_CL:
                            response = factory.ok();
                            response.headers().set(CONTENT_LENGTH, valueOf(MAX_PAYLOAD * 1000L));
                            break;
                        case PATH_NO_CONTENT_LARGE_CL:
                            response = factory.newResponse(NO_CONTENT);
                            response.headers().set(CONTENT_LENGTH, valueOf(MAX_PAYLOAD * 1000L));
                            break;
                        case PATH_NOT_MODIFIED_LARGE_CL:
                            response = factory.newResponse(NOT_MODIFIED);
                            response.headers().set(CONTENT_LENGTH, valueOf(MAX_PAYLOAD * 1000L));
                            break;
                        default:
                            response = factory.ok();
                            break;
                    }
                    return Single.<StreamingHttpResponse>succeeded(response).shareContextOnSubscribe();
                });
    }

    private static StreamingHttpClient newClient(HttpServerContext server, HttpProtocol protocol) {
        return newClientBuilder(server, CLIENT_CTX, protocol)
                .appendClientFilter(new PayloadSizeLimitingHttpRequesterFilter(MAX_PAYLOAD))
                .buildStreaming();
    }

    private static Publisher<Buffer> largeBody(BufferAllocator alloc, int size) {
        Buffer[] chunks = new Buffer[size];
        Arrays.setAll(chunks, i -> alloc.fromAscii("x"));
        return Publisher.from(chunks);
    }
}
