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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.stringContainsInOrder;

/**
 * End-to-end check, with a raw {@link Socket}, of what an HTTP/1.1 server writes for a {@code HEAD} response.
 * <p>
 * A raw socket is used on purpose: ServiceTalk's own client decoder knows that a {@code HEAD} response has no payload
 * and may hide stray bytes (e.g. a {@code 0\r\n\r\n} last-chunk) written after the headers.
 */
class Http1HeadResponseTest {

    private static final String OK_STATUS_LINE = "HTTP/1.1 200 OK\r\n";
    private static final String END_OF_HEADERS = "\r\n\r\n";
    private static final String HEAD_REQUEST = "HEAD / HTTP/1.1\r\nHost: localhost\r\n\r\n";
    private static final String GET_REQUEST_AND_CLOSE =
            "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    private static final String BODY = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
    private static final String BODY_CHUNK = "34\r\n" + BODY + "\r\n";
    private static final String LAST_CHUNK = "0\r\n\r\n";
    private static final String LAST_CHUNK_WITH_TRAILER = "0\r\nx-trailer: tv\r\n\r\n";
    private static final String CHUNKED = "transfer-encoding: chunked";
    private static final String CONTENT_LENGTH_52 = "content-length: 52";
    private static final String CONTENT_LENGTH_0 = "content-length: 0";
    private static final int MAX_HEADERS_BYTES = 8192;

    @FunctionalInterface
    private interface ServerStarter {
        ServerContext start() throws Exception;
    }

    private static final class HeadCase {
        final ServerStarter server;
        /** Framing headers that the handler set and which therefore must be present on the HEAD response. */
        final List<String> expectedHeadFramingHeaders;
        /** Expectation for the complete response to the {@code GET} sent on the same connection. */
        final Matcher<String> getResponse;

        HeadCase(final ServerStarter server, final List<String> expectedHeadFramingHeaders,
                 final Matcher<String> getResponse) {
            this.server = server;
            this.expectedHeadFramingHeaders = expectedHeadFramingHeaders;
            this.getResponse = getResponse;
        }
    }

    static Stream<Arguments> headCases() {
        final List<String> none = Collections.emptyList();
        final List<String> chunked = Collections.singletonList(CHUNKED);
        return Stream.of(
                headCase("streaming body+trailer, no framing header", streaming(rf ->
                                withTrailer(rf.ok().payloadBody(Publisher.from(body())))), none,
                        allOf(containsStringIgnoringCase(CHUNKED), containsString(BODY_CHUNK),
                                endsWith(LAST_CHUNK_WITH_TRAILER))),
                headCase("streaming body+trailer, handler-set chunked", streaming(rf ->
                                withTrailer(rf.ok().payloadBody(Publisher.from(body()))
                                        .addHeader("transfer-encoding", "chunked"))), chunked,
                        allOf(containsStringIgnoringCase(CHUNKED), containsString(BODY_CHUNK),
                                endsWith(LAST_CHUNK_WITH_TRAILER))),
                headCase("streaming empty payload, handler-set chunked", streaming(rf ->
                                rf.ok().addHeader("transfer-encoding", "chunked")), chunked,
                        allOf(containsStringIgnoringCase(CHUNKED), endsWith(END_OF_HEADERS + LAST_CHUNK))),
                headCase("streaming body, handler-set content-length", streaming(rf ->
                                rf.ok().payloadBody(Publisher.from(body())).addHeader("content-length", "52")),
                        Collections.singletonList(CONTENT_LENGTH_52),
                        allOf(containsStringIgnoringCase(CONTENT_LENGTH_52), endsWith(END_OF_HEADERS + BODY))),
                headCase("streaming 3 buffers", streaming(rf ->
                                rf.ok().payloadBody(Publisher.from(body(), body(), body()))), none,
                        allOf(containsStringIgnoringCase(CHUNKED),
                                stringContainsInOrder(Arrays.asList(BODY_CHUNK, BODY_CHUNK, BODY_CHUNK)),
                                endsWith(LAST_CHUNK))),
                headCase("streaming one empty buffer", streaming(rf ->
                                rf.ok().payloadBody(Publisher.from(DEFAULT_ALLOCATOR.newBuffer(0)))), none,
                        allOf(containsStringIgnoringCase(CHUNKED), endsWith(END_OF_HEADERS + LAST_CHUNK))),
                headCase("streaming zero items", streaming(rf ->
                                rf.ok().payloadBody(Publisher.<Buffer>empty())), none,
                        allOf(containsStringIgnoringCase(CONTENT_LENGTH_0), endsWith(END_OF_HEADERS))),
                headCase("aggregated body", aggregated(rf -> rf.ok().payloadBody(body())), none,
                        allOf(containsStringIgnoringCase(CONTENT_LENGTH_52), endsWith(END_OF_HEADERS + BODY))),
                headCase("aggregated body, handler-set chunked", aggregated(rf ->
                                rf.ok().payloadBody(body()).addHeader("transfer-encoding", "chunked")), chunked,
                        allOf(containsStringIgnoringCase(CHUNKED), containsString(BODY_CHUNK),
                                endsWith(LAST_CHUNK))),
                headCase("blocking body", blocking(rf -> rf.ok().payloadBody(body())), none,
                        allOf(containsStringIgnoringCase(CONTENT_LENGTH_52), endsWith(END_OF_HEADERS + BODY))),
                headCase("streaming trailers only, handler-set chunked", streaming(rf ->
                                withTrailer(rf.ok().addHeader("transfer-encoding", "chunked"))), chunked,
                        allOf(containsStringIgnoringCase(CHUNKED), endsWith(LAST_CHUNK_WITH_TRAILER))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("headCases")
    void rawSocketHeadResponseEndsAfterHeaders(HeadCase headCase) throws Exception {
        try (ServerContext serverContext = headCase.server.start();
             Socket socket = new Socket()) {
            socket.connect(serverContext.listenAddress());
            // Only a safety net, the reads below never wait for silence: they end at the headers or at EOF.
            socket.setSoTimeout((int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS / 2));
            InputStream in = new BufferedInputStream(socket.getInputStream());

            socket.getOutputStream().write(HEAD_REQUEST.getBytes(ISO_8859_1));
            String headHeaders = readUntilEndOfHeaders(in);

            // The server closes the connection after the second response, so reading to EOF is deterministic.
            socket.getOutputStream().write(GET_REQUEST_AND_CLOSE.getBytes(ISO_8859_1));
            String afterHead = readToEof(in);

            assertThat("Unexpected HEAD response status", headHeaders, startsWith(OK_STATUS_LINE));
            assertThat("HEAD response must carry only the framing headers set by the handler",
                    framingHeaders(headHeaders), is(headCase.expectedHeadFramingHeaders));
            assertThat("Bytes were written after the HEAD response headers (e.g. a payload body or a last-chunk)",
                    afterHead, startsWith(OK_STATUS_LINE));
            assertThat("GET response on the same connection is unexpected: leftover state from HEAD?",
                    afterHead, headCase.getResponse);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}")
    @EnumSource(HttpTestExecutionStrategy.class)
    void reservedConnectionHeadThenGetHasNoLeftoverState(HttpTestExecutionStrategy strategy) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0)).protocols(h1Default())
                .listenStreamingAndAwait((ctx, request, responseFactory) -> Single.succeeded(
                        withTrailer(responseFactory.ok().payloadBody(Publisher.from(body())))));
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(h1Default()).executionStrategy(strategy.executorSupplier.get()).buildStreaming();
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            HttpResponse head = connection.request(connection.head("/")).toFuture().get().toResponse()
                    .toFuture().get();
            assertThat(head.payloadBody().readableBytes(), is(0));
            assertThat(head.trailers().isEmpty(), is(true));

            HttpResponse get = connection.request(connection.get("/")).toFuture().get().toResponse()
                    .toFuture().get();
            assertThat(get.payloadBody().toString(ISO_8859_1), is(BODY));
            assertThat(get.trailers().get("x-trailer").toString(), is("tv"));
        }
    }

    private static Buffer body() {
        return DEFAULT_ALLOCATOR.fromAscii(BODY);
    }

    private static StreamingHttpResponse withTrailer(StreamingHttpResponse response) {
        return response.transform(new StatelessTrailersTransformer<Buffer>() {
            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                return trailers.add("x-trailer", "tv");
            }
        });
    }

    private static Arguments headCase(String name, ServerStarter server, List<String> expectedHeadFramingHeaders,
                                      Matcher<String> getResponse) {
        return Arguments.of(Named.of(name, new HeadCase(server, expectedHeadFramingHeaders, getResponse)));
    }

    private static ServerStarter streaming(Function<StreamingHttpResponseFactory, StreamingHttpResponse> handler) {
        return () -> HttpServers.forAddress(localAddress(0)).protocols(h1Default())
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        Single.succeeded(handler.apply(responseFactory)));
    }

    private static ServerStarter aggregated(Function<HttpResponseFactory, HttpResponse> handler) {
        return () -> HttpServers.forAddress(localAddress(0)).protocols(h1Default())
                .listenAndAwait((ctx, request, responseFactory) -> Single.succeeded(handler.apply(responseFactory)));
    }

    private static ServerStarter blocking(Function<HttpResponseFactory, HttpResponse> handler) {
        return () -> HttpServers.forAddress(localAddress(0)).protocols(h1Default())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> handler.apply(responseFactory));
    }

    /** Reads, byte by byte, up to and including the first {@code \r\n\r\n}; never consumes bytes after it. */
    private static String readUntilEndOfHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        String text = "";
        while (!text.endsWith(END_OF_HEADERS) && headers.size() < MAX_HEADERS_BYTES) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            headers.write(b);
            text = new String(headers.toByteArray(), ISO_8859_1);
        }
        assertThat("End of the HEAD response headers not found", text, endsWith(END_OF_HEADERS));
        return text;
    }

    private static String readToEof(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), ISO_8859_1);
    }

    /** Lower-cased {@code content-length} / {@code transfer-encoding} header lines of a response header block. */
    private static List<String> framingHeaders(String headers) {
        List<String> framing = new ArrayList<>();
        for (String line : headers.split("\r\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-length:") || lower.startsWith("transfer-encoding:")) {
                framing.add(lower);
            }
        }
        return framing;
    }
}
