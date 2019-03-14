/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequestHandler;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.DelegatingBlockingStreamingHttpServerResponse;
import io.servicetalk.http.api.DelegatingHttpPayloadWriter;
import io.servicetalk.http.api.HttpOutputStream;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static io.servicetalk.http.api.HttpHeaderNames.TRAILER;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class BlockingStreamingHttpServiceTest {

    private static final String X_TOTAL_LENGTH = "x-total-length";
    private static final String HELLO_WORLD = "Hello\nWorld\n";
    private static final String HELLO_WORLD_LENGTH = String.valueOf(HELLO_WORLD.length());

    private ServerContext serverContext;
    private BlockingStreamingHttpClient client;

    @After
    public void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (serverContext != null) {
                serverContext.close();
            }
        }
    }

    private BlockingStreamingHttpClient context(BlockingStreamingHttpRequestHandler handler) throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0)).listenBlockingStreamingAndAwait(handler);

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlockingStreaming();
        return client;
    }

    @Test
    public void defaultResponseStatusNoPayload() throws Exception {
        BlockingStreamingHttpClient client = context((ctx, request, response) -> response.sendMetaData().close());
        BlockingStreamingHttpResponse response = client.request(client.get("/"));

        assertResponse(response);
        assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
    }

    @Test
    public void respondWithCustomMetaData() throws Exception {
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
    public void receivePayloadBody() throws Exception {
        StringBuilder receivedPayloadBody = new StringBuilder();

        BlockingStreamingHttpClient client = context((ctx, request, response) -> {
            request.payloadBody().forEach(chunk -> receivedPayloadBody.append(chunk.toString(US_ASCII)));
            response.sendMetaData().close();
        });

        BlockingStreamingHttpResponse response = client.request(client.post("/")
                .payloadBody(asList("Hello\n", "World\n"), textSerializer()));
        assertResponse(response);
        assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
        assertThat(receivedPayloadBody.toString(), is(HELLO_WORLD));
    }

    @Test
    public void respondWithPayloadBodyAndTrailersUsingPayloadWriter() throws Exception {
        respondWithPayloadBodyAndTrailers((ctx, request, response) -> {
            try (HttpPayloadWriter<Buffer> pw = response.sendMetaData()) {
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("Hello\n"));
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("World\n"));
            }
        }, false);
    }

    @Test
    public void respondWithPayloadBodyAndTrailersUsingPayloadWriterWithSerializer() throws Exception {
        respondWithPayloadBodyAndTrailers((ctx, request, response) -> {
            try (HttpPayloadWriter<String> pw = response.sendMetaData(textSerializer())) {
                pw.write("Hello\n");
                pw.write("World\n");
            }
        }, true);
    }

    @Test
    public void respondWithPayloadBodyAndTrailersUsingOutputStream() throws Exception {
        respondWithPayloadBodyAndTrailers((ctx, request, response) -> {
            try (HttpOutputStream out = response.sendMetaDataOutputStream()) {
                out.write("Hello\n".getBytes(US_ASCII));
                out.write("World\n".getBytes(US_ASCII));
            }
        }, false);
    }

    private void respondWithPayloadBodyAndTrailers(BlockingStreamingHttpRequestHandler delegate,
                                                   boolean useDeserializer) throws Exception {
        BlockingStreamingHttpRequestHandler trailersFilter = (ctx, request, response) -> delegate.handle(ctx, request,
                new DelegatingBlockingStreamingHttpServerResponse(response, ctx.executionContext().bufferAllocator()) {
            @Override
            public HttpPayloadWriter<Buffer> sendMetaData() {
                delegate().setHeader(TRAILER, X_TOTAL_LENGTH);

                return new DelegatingHttpPayloadWriter<Buffer>(delegate().sendMetaData()) {

                    private final AtomicInteger totalLength = new AtomicInteger();

                    @Override
                    public void write(final Buffer object) throws IOException {
                        totalLength.addAndGet(object.readableBytes());
                        delegate().write(object);
                    }

                    @Override
                    public void close() throws IOException {
                        setTrailer(X_TOTAL_LENGTH, totalLength.toString());
                        delegate().close();
                    }
                };
            }
        });

        BlockingStreamingHttpClient client = context(trailersFilter);

        BlockingStreamingHttpResponse response = client.request(client.get("/"));
        assertResponse(response);
        assertThat(response.headers().get(TRAILER).toString(), is(X_TOTAL_LENGTH));

        HttpResponse aggregated = response.toResponse().toFuture().get();
        if (useDeserializer) {
            assertThat(aggregated.payloadBody(textDeserializer()), is(HELLO_WORLD));
        } else {
            assertThat(aggregated.payloadBody().toString(US_ASCII), is(HELLO_WORLD));
        }
        assertThat(aggregated.trailers().get(X_TOTAL_LENGTH).toString(), is(HELLO_WORLD_LENGTH));
    }

    @Test
    public void echoServerUsingPayloadWriter() throws Exception {
        echoServer((ctx, request, response) -> {
            try (PayloadWriter<Buffer> pw = response.sendMetaData()) {
                request.payloadBody().forEach(chunk -> {
                    try {
                        pw.write(chunk);
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
            }
        }, false);
    }

    @Test
    public void echoServerUsingPayloadWriterWithSerializer() throws Exception {
        echoServer((ctx, request, response) -> {
            try (PayloadWriter<String> pw = response.sendMetaData(textSerializer())) {
                request.payloadBody(textDeserializer()).forEach(chunk -> {
                    try {
                        pw.write(chunk);
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
            }
        }, true);
    }

    @Test
    public void echoServerUsingInputOutputStream() throws Exception {
        echoServer((ctx, request, response) -> {
            try (OutputStream out = response.sendMetaDataOutputStream();
                 InputStream in = request.payloadBodyInputStream()) {
                int ch;
                while ((ch = in.read()) != -1) {
                    out.write(ch);
                }
            }
        }, false);
    }

    private void echoServer(BlockingStreamingHttpRequestHandler handler, boolean useDeserializer) throws Exception {
        BlockingStreamingHttpClient client = context(handler);

        BlockingStreamingHttpResponse response = client.request(client.post("/")
                .payloadBody(asList("Hello\n", "World\n"), textSerializer()));
        assertResponse(response, HELLO_WORLD, useDeserializer);
    }

    @Test
    public void payloadBodyFilteringPayloadWriter() throws Exception {
        payloadBodyFiltering((ctx, request, response) -> {
            try (HttpPayloadWriter<Buffer> pw = response.sendMetaData()) {
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("Hello"));
                pw.write(ctx.executionContext().bufferAllocator().fromAscii("World"));
            }
        });
    }

    @Test
    public void payloadBodyFilteringPayloadWriterWithSerializer() throws Exception {
        payloadBodyFiltering((ctx, request, response) -> {
            try (HttpPayloadWriter<String> pw = response.sendMetaData(textSerializer())) {
                pw.write("Hello");
                pw.write("World");
            }
        });
    }

    @Test
    public void payloadBodyFilteringOutputStream() throws Exception {
        payloadBodyFiltering((ctx, request, response) -> {
            try (HttpOutputStream out = response.sendMetaDataOutputStream()) {
                out.write("Hello".getBytes(US_ASCII));
                out.write("World".getBytes(US_ASCII));
            }
        });
    }

    private void payloadBodyFiltering(BlockingStreamingHttpRequestHandler delegate) throws Exception {
        BlockingStreamingHttpRequestHandler addNewLineFilter = (ctx, request, response) -> delegate.handle(ctx, request,
                new DelegatingBlockingStreamingHttpServerResponse(response, ctx.executionContext().bufferAllocator()) {
            @Override
            public HttpPayloadWriter<Buffer> sendMetaData() {
                return new DelegatingHttpPayloadWriter<Buffer>(delegate().sendMetaData()) {
                    @Override
                    public void write(final Buffer object) throws IOException {
                        if (object.tryEnsureWritable(1, true)) {
                            object.writeByte('\n');
                            delegate().write(object);
                        } else {
                            Buffer newBuffer = ctx.executionContext().bufferAllocator()
                                    .newBuffer(object.readableBytes() + 1);
                            newBuffer.writeBytes(object);
                            newBuffer.writeByte('\n');
                            delegate().write(newBuffer);
                        }
                    }
                };
            }
        });

        BlockingStreamingHttpClient client = context(addNewLineFilter);

        BlockingStreamingHttpResponse response = client.request(client.get("/"));
        assertResponse(response, HELLO_WORLD);
    }

    @Test
    public void sendMetaDataTwice() throws Exception {
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
            assertThat(response.toResponse().toFuture().get().payloadBody(), is(EMPTY_BUFFER));
            fail("Payload body should complete with an error");
        } catch (Exception e) {
            assertThat(serverException.get(), instanceOf(IllegalStateException.class));
        }
    }

    private static void assertResponse(BlockingStreamingHttpResponse response) throws Exception {
        assertThat(response.status(), is(OK));
        assertThat(response.version(), is(HTTP_1_1));
    }

    private static void assertResponse(BlockingStreamingHttpResponse response,
                                       String expectedPayloadBody) throws Exception {
        assertResponse(response, expectedPayloadBody, false);
    }

    private static void assertResponse(BlockingStreamingHttpResponse response,
                                       String expectedPayloadBody,
                                       boolean useDeserializer) throws Exception {
        assertResponse(response);

        HttpResponse aggregated = response.toResponse().toFuture().get();
        if (useDeserializer) {
            assertThat(aggregated.payloadBody(textDeserializer()), is(expectedPayloadBody));
        } else {
            assertThat(aggregated.payloadBody().toString(US_ASCII), is(expectedPayloadBody));
        }
    }
}
