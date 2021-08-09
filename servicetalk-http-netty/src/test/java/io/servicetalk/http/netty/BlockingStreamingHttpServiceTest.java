/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.internal.BlockingIterables;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMessageBodyIterator;
import io.servicetalk.http.api.HttpOutputStream;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.TRAILER;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class BlockingStreamingHttpServiceTest {

    private static final String X_TOTAL_LENGTH = "x-total-length";
    private static final String HELLO_WORLD = "Hello\nWorld\n";
    private static final String HELLO_WORLD_LENGTH = String.valueOf(HELLO_WORLD.length());

    private ServerContext serverContext;
    private BlockingStreamingHttpClient client;

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (serverContext != null) {
                serverContext.closeAsync().toFuture().get();
            }
        }
    }

    private BlockingStreamingHttpClient context(BlockingStreamingHttpService handler) throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0)).listenBlockingStreamingAndAwait(handler);

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlockingStreaming();
        return client;
    }

    @Test
    void defaultResponseStatusNoPayload() throws Exception {
        BlockingStreamingHttpClient client = context((ctx, request, response) -> response.sendMetaData().close());
        BlockingStreamingHttpResponse response = client.request(client.get("/"));

        assertResponse(response);
        assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
    }

    @Test
    void respondWithCustomMetaData() throws Exception {
        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            response.status(ACCEPTED);

            CharSequence auth = request.headers().get("X-User-Header");
            if (auth != null) {
                response.addHeader("X-User-Header", auth);
            }
            response.addHeader("X-Server-Header", "X-Server-Value");

            response.sendMetaData().close();
        });

        BlockingStreamingHttpResponse response = client.request(client.get("/")
                .addHeader("X-User-Header", "X-User-Value"));
        assertThat(response.status(), is(ACCEPTED));
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.headers().get("X-User-Header").toString(), is("X-User-Value"));
        assertThat(response.headers().get("X-Server-Header").toString(), is("X-Server-Value"));
        assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
    }

    @Test
    void receivePayloadBody() throws Exception {
        StringBuilder receivedPayloadBody = new StringBuilder();

        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            request.payloadBody(appSerializerUtf8FixLen()).forEach(receivedPayloadBody::append);
            response.sendMetaData().close();
        });

        BlockingStreamingHttpResponse response = client.request(client.post("/")
                .payloadBody(asList("Hello\n", "World\n"), appSerializerUtf8FixLen()));
        assertResponse(response);
        assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
        assertThat(receivedPayloadBody.toString(), is(HELLO_WORLD));
    }

    @Test
    void clientRequestInputStreamPayloadBody() throws Exception {
        StringBuilder receivedPayloadBody = new StringBuilder();

        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            request.payloadBody().forEach(chunk -> receivedPayloadBody.append(chunk.toString(US_ASCII)));
            response.sendMetaData().close();
        });

        BlockingStreamingHttpResponse response = client.request(client.post("/")
                .payloadBody(new ByteArrayInputStream(HELLO_WORLD.getBytes(US_ASCII))));
        assertResponse(response);
        assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
        assertThat(receivedPayloadBody.toString(), is(HELLO_WORLD));
    }

    @Test
    void clientResponseInputStreamPayloadBody() throws Exception {
        StringBuilder receivedPayloadBody = new StringBuilder();

        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            request.payloadBody().forEach(chunk -> receivedPayloadBody.append(chunk.toString(US_ASCII)));
            response.sendMetaData().close();
        });

        String expectedBody = "overwritten";
        BlockingStreamingHttpResponse response = client.request(client.post("/")
                .payloadBody(new ByteArrayInputStream(HELLO_WORLD.getBytes(US_ASCII))));
        assertResponse(response);
        response.payloadBody(new ByteArrayInputStream(expectedBody.getBytes(US_ASCII)));
        assertThat(response.toResponse().toFuture().get().
                payloadBody().toString(US_ASCII), is(expectedBody));
        assertThat(receivedPayloadBody.toString(), is(HELLO_WORLD));
    }

    @Test
    void respondWithPayloadBodyAndTrailersUsingPayloadWriter() throws Exception {
        respondWithPayloadBodyAndTrailers((ctx, request, response) -> {
            response.setHeader(TRAILER, X_TOTAL_LENGTH);
            try (HttpPayloadWriter<Buffer> pw = response.sendMetaData()) {
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("Hello\n"));
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("World\n"));
                pw.setTrailer(X_TOTAL_LENGTH, String.valueOf(HELLO_WORLD.length()));
            }
        }, false);
    }

    @Test
    void respondWithPayloadBodyAndTrailersUsingPayloadWriterWithSerializer() throws Exception {
        respondWithPayloadBodyAndTrailers((ctx, request, response) -> {
            response.setHeader(TRAILER, X_TOTAL_LENGTH);
            try (HttpPayloadWriter<String> pw = response.sendMetaData(appSerializerUtf8FixLen())) {
                pw.write("Hello\n");
                pw.write("World\n");
                pw.setTrailer(X_TOTAL_LENGTH, String.valueOf(HELLO_WORLD.length()));
            }
        }, true);
    }

    @Test
    void respondWithPayloadBodyAndTrailersUsingOutputStream() throws Exception {
        respondWithPayloadBodyAndTrailers((ctx, request, response) -> {
            response.setHeader(TRAILER, X_TOTAL_LENGTH);
            try (HttpOutputStream out = response.sendMetaDataOutputStream()) {
                out.write("Hello\n".getBytes(US_ASCII));
                out.write("World\n".getBytes(US_ASCII));
                out.setTrailer(X_TOTAL_LENGTH, String.valueOf(HELLO_WORLD.length()));
            }
        }, false);
    }

    @Test
    void setRequestMessageBody() throws Exception {
        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            response.status(OK);
            try {
                HttpMessageBodyIterator<Buffer> reqItr = request.messageBody().iterator();
                StringBuilder sb = new StringBuilder();
                while (reqItr.hasNext()) {
                    sb.append(requireNonNull(reqItr.next()).toString(UTF_8));
                }
                assertThat(sb.toString(), is(HELLO_WORLD));
                HttpHeaders trailers = reqItr.trailers();
                assertThat(trailers, notNullValue());
                assertThat(trailers.get(X_TOTAL_LENGTH).toString(), is(HELLO_WORLD_LENGTH));
            } catch (Throwable cause) {
                HttpPayloadWriter<String> payloadWriter = response.sendMetaData(appSerializerUtf8FixLen());
                payloadWriter.write(cause.toString());
                payloadWriter.close();
                return;
            }
            response.sendMetaData(appSerializerUtf8FixLen()).close();
        });
        BufferAllocator alloc = client.executionContext().bufferAllocator();
        BlockingStreamingHttpRequest req = client.get("/");
        req.setHeader(TRAILER, X_TOTAL_LENGTH);
        int split = HELLO_WORLD.length() / 2;
        final BlockingIterable<Buffer> reqIterable =
                BlockingIterables.from(asList(
                        alloc.fromAscii(HELLO_WORLD.substring(0, split)),
                        alloc.fromAscii(HELLO_WORLD.substring(split))));
        req.messageBody(() -> new HttpMessageBodyIterator<Buffer>() {
            private final BlockingIterator<Buffer> iterator = reqIterable.iterator();
            @Nullable
            private HttpHeaders trailers;
            private int totalLength;

            @Nullable
            @Override
            public HttpHeaders trailers() {
                if (trailers == null) {
                    trailers = DefaultHttpHeadersFactory.INSTANCE.newTrailers();
                    trailers.set(X_TOTAL_LENGTH, String.valueOf(totalLength));
                }
                return trailers;
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                return iterator.hasNext(timeout, unit);
            }

            @Nullable
            @Override
            public Buffer next(final long timeout, final TimeUnit unit) throws TimeoutException {
                return addTotalLength(iterator.next(timeout, unit));
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Nullable
            @Override
            public Buffer next() {
                return addTotalLength(iterator.next());
            }

            @Override
            public void close() throws Exception {
                iterator.close();
            }

            @Nullable
            private Buffer addTotalLength(@Nullable Buffer buffer) {
                if (buffer != null) {
                    totalLength += buffer.readableBytes();
                }
                return buffer;
            }
        });

        BlockingStreamingHttpResponse response = client.request(req);
        assertThat(response.status(), is(OK));
        assertThat(stream(response.payloadBody(appSerializerUtf8FixLen()).spliterator(), false)
                .collect(Collectors.toList()), emptyIterable());
    }

    private void respondWithPayloadBodyAndTrailers(BlockingStreamingHttpService handler,
                                                   boolean useDeserializer) throws Exception {
        BlockingStreamingHttpClient client = context(handler);

        BlockingStreamingHttpResponse response = client.request(client.get("/"));
        assertResponse(response);
        assertThat(response.headers().get(TRAILER).toString(), is(X_TOTAL_LENGTH));

        final StringBuilder sb = new StringBuilder();
        final HttpHeaders trailers;
        if (useDeserializer) {
            HttpMessageBodyIterator<String> msgBody = response.messageBody(appSerializerUtf8FixLen()).iterator();
            while (msgBody.hasNext()) {
                sb.append(msgBody.next());
            }
            trailers = msgBody.trailers();
        } else {
            HttpMessageBodyIterator<Buffer> msgBody = response.messageBody().iterator();
            while (msgBody.hasNext()) {
                sb.append(requireNonNull(msgBody.next()).toString(UTF_8));
            }
            trailers = msgBody.trailers();
        }
        assertThat(sb.toString(), is(HELLO_WORLD));
        assertThat(trailers, notNullValue());
        assertThat(trailers.get(X_TOTAL_LENGTH).toString(), is(HELLO_WORLD_LENGTH));
    }

    @Test
    void echoServerUsingPayloadWriter() throws Exception {
        echoServer((ctx, request, response) -> {
            CharSequence contentType = request.headers().get(CONTENT_TYPE);
            if (contentType != null) {
                response.setHeader(CONTENT_TYPE, contentType);
            }
            try (PayloadWriter<Buffer> pw = response.sendMetaData()) {
                request.payloadBody().forEach(chunk -> {
                    try {
                        pw.write(chunk);
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
            }
        });
    }

    @Test
    void echoServerUsingPayloadWriterWithSerializer() throws Exception {
        echoServer((ctx, request, response) -> {
            try (PayloadWriter<String> pw = response.sendMetaData(appSerializerUtf8FixLen())) {
                request.payloadBody(appSerializerUtf8FixLen()).forEach(chunk -> {
                    try {
                        pw.write(chunk);
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
            }
        });
    }

    @Test
    void echoServerUsingInputOutputStream() throws Exception {
        echoServer((ctx, request, response) -> {
            CharSequence contentType = request.headers().get(CONTENT_TYPE);
            if (contentType != null) {
                response.setHeader(CONTENT_TYPE, contentType);
            }
            try (OutputStream out = response.sendMetaDataOutputStream();
                 InputStream in = request.payloadBodyInputStream()) {
                int ch;
                while ((ch = in.read()) != -1) {
                    out.write(ch);
                }
            }
        });
    }

    private void echoServer(BlockingStreamingHttpService handler) throws Exception {
        BlockingStreamingHttpClient client = context(handler);

        BlockingStreamingHttpResponse response = client.request(client.post("/")
                .payloadBody(asList("Hello\n", "World\n"), appSerializerUtf8FixLen()));
        assertResponse(response, HELLO_WORLD, true);
    }

    @Test
    void sendMetaDataTwice() throws Exception {
        AtomicReference<Throwable> serverException = new AtomicReference<>();

        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            try {
                response.sendMetaData();
                response.sendMetaData();
            } catch (Throwable t) {
                serverException.set(t);
                throw t;
            }
        });

        try {
            BlockingStreamingHttpResponse response = client.request(client.get("/"));
            assertResponse(response);
            assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
            fail("Payload body should complete with an error");
        } catch (Exception e) {
            assertThat(serverException.get(), instanceOf(IllegalStateException.class));
        }
    }

    @Test
    void doNotSendMetaData() throws Exception {
        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            // Noop
        });

        HttpClient asyncClient = client.asClient();
        final Future<HttpResponse> responseFuture = asyncClient.request(asyncClient.get("/")).toFuture();

        assertThrows(TimeoutException.class, () -> responseFuture.get(1, SECONDS));
    }

    @Test
    void doNotWriteTheLastChunk() throws Exception {
        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            response.sendMetaData();
            // Do not close()
        });

        BlockingStreamingHttpResponse response = client.request(client.get("/"));
        assertResponse(response);
        final BlockingIterator<Buffer> iterator = response.payloadBody().iterator();

        assertThrows(TimeoutException.class, () -> iterator.hasNext(1, SECONDS));
    }

    private static void assertResponse(BlockingStreamingHttpResponse response) {
        assertThat(response.status(), is(OK));
        assertThat(response.version(), is(HTTP_1_1));
    }

    private static void assertResponse(BlockingStreamingHttpResponse response,
                                       String expectedPayloadBody,
                                       boolean useDeserializer) throws Exception {
        assertResponse(response);
        if (useDeserializer) {
            StringBuilder sb = new StringBuilder();
            for (String s : response.payloadBody(appSerializerUtf8FixLen())) {
                sb.append(s);
            }
            assertThat(sb.toString(), is(expectedPayloadBody));
        } else {
            assertThat(response.toResponse().toFuture().get().payloadBody().toString(UTF_8), is(expectedPayloadBody));
        }
    }
}
