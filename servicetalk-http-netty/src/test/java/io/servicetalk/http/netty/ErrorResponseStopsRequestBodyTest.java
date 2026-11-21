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
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Integer.toHexString;
import static java.lang.String.valueOf;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * An HTTP/1.1 client stops sending a request payload body when the server rejects the request and closes the
 * connection (<a href="https://tools.ietf.org/html/rfc9112#section-9.5">RFC 9112, section 9.5</a>).
 */
class ErrorResponseStopsRequestBodyTest {

    private static final int SOCKET_TIMEOUT_MS = (int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS);
    private static final String CHUNK = "hello";
    private static final String REJECTION = "payload body is too large";
    private static final int LARGE_BODY_CHUNKS = 64;
    private static final int LARGE_BODY_CHUNK_SIZE = 1 << 20;

    @ParameterizedTest(name = "{displayName} [{index}] expectContinue={0} connectionClose={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void errorResponseWhilePayloadBodyIsSent(boolean expectContinue, boolean connectionClose) throws Exception {
        try (ServerSocket server = newServerSocket();
             StreamingHttpClient client = HttpClients.forSingleAddress(
                     serverHostAndPort(server.getLocalSocketAddress()))
                     .expectContinueTimeout(ofMillis(100))
                     .buildStreaming();
             ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
             Socket socket = accept(server)) {
            BufferAllocator alloc = connection.executionContext().bufferAllocator();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().disableAutoOnSubscribe().build();
            TestSubscription subscription = new TestSubscription();
            StreamingHttpRequest request = connection.post("/")
                    .setHeader(CONTENT_LENGTH, valueOf(CHUNK.length() * 2))
                    .payloadBody(payload);
            if (expectContinue) {
                request.setHeader(EXPECT, CONTINUE);
            }
            Future<StreamingHttpResponse> response = connection.request(request).toFuture();
            assertThat(readHeaders(in), startsWith("POST / "));

            // The server doesn't send 100 (Continue), so the timeout releases the payload body of Expect requests.
            payload.awaitSubscribed();
            payload.onSubscribe(subscription);
            subscription.awaitRequestN(1);
            payload.onNext(alloc.fromAscii(CHUNK));
            assertThat(readBody(in, CHUNK.length()), is(CHUNK));

            write(socket, "HTTP/1.1 413 Payload Too Large\r\n" + (connectionClose ? "connection: close\r\n" : "") +
                    "content-length: 0\r\n\r\n");
            StreamingHttpResponse rejection = response.get();
            assertThat(rejection.status(), is(PAYLOAD_TOO_LARGE));

            if (connectionClose) {
                subscription.awaitCancelled();
                rejection.toResponse().toFuture().get();
                connection.onClose().toFuture().get();
            } else {
                // The server keeps reading the payload body, so the client finishes it and reuses the connection.
                assertThat(subscription.isCancelled(), is(false));
                payload.onNext(alloc.fromAscii(CHUNK));
                payload.onComplete();
                assertThat(readBody(in, CHUNK.length()), is(CHUNK));
                rejection.toResponse().toFuture().get();

                Future<StreamingHttpResponse> next = connection.request(connection.get("/")).toFuture();
                assertThat(readHeaders(in), startsWith("GET / "));
                write(socket, "HTTP/1.1 200 OK\r\ncontent-length: 0\r\n\r\n");
                assertThat(next.get().toResponse().toFuture().get().status(), is(OK));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] chunkedResponse={0}")
    @ValueSource(booleans = {false, true})
    void errorResponseBodyIsDeliveredAfterPayloadBodyStops(boolean chunkedResponse) throws Exception {
        try (ServerSocket server = newServerSocket();
             StreamingHttpClient client = HttpClients.forSingleAddress(
                     serverHostAndPort(server.getLocalSocketAddress())).buildStreaming();
             ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
             Socket socket = accept(server)) {
            BufferAllocator alloc = connection.executionContext().bufferAllocator();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));

            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().disableAutoOnSubscribe().build();
            TestSubscription subscription = new TestSubscription();
            Future<StreamingHttpResponse> response = connection.request(connection.post("/")
                    .setHeader(CONTENT_LENGTH, valueOf(CHUNK.length() * 2))
                    .payloadBody(payload)).toFuture();
            assertThat(readHeaders(in), startsWith("POST / "));
            payload.awaitSubscribed();
            payload.onSubscribe(subscription);
            subscription.awaitRequestN(1);
            payload.onNext(alloc.fromAscii(CHUNK));
            assertThat(readBody(in, CHUNK.length()), is(CHUNK));

            // The payload body stops as soon as the response meta-data is read, before the response body arrives.
            write(socket, "HTTP/1.1 413 Payload Too Large\r\nconnection: close\r\n" +
                    (chunkedResponse ? "transfer-encoding: chunked" : "content-length: " + REJECTION.length()) +
                    "\r\n\r\n");
            StreamingHttpResponse rejection = response.get();
            assertThat(rejection.status(), is(PAYLOAD_TOO_LARGE));
            subscription.awaitCancelled();

            write(socket, chunkedResponse ?
                    toHexString(REJECTION.length()) + "\r\n" + REJECTION + "\r\n0\r\n\r\n" : REJECTION);
            assertThat(rejection.toResponse().toFuture().get().payloadBody().toString(US_ASCII), is(REJECTION));
            connection.onClose().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] chunked={0}")
    @ValueSource(booleans = {false, true})
    void errorResponseWhileWritesAreQueued(boolean chunked) throws Exception {
        try (ServerSocket server = newServerSocket();
             StreamingHttpClient client = HttpClients.forSingleAddress(
                     serverHostAndPort(server.getLocalSocketAddress())).buildStreaming();
             ReservedStreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
             Socket socket = accept(server)) {
            BufferAllocator alloc = connection.executionContext().bufferAllocator();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));

            byte[] chunk = new byte[LARGE_BODY_CHUNK_SIZE];
            StreamingHttpRequest request = connection.post("/").payloadBody(range(0, LARGE_BODY_CHUNKS)
                    .map(__ -> alloc.wrap(chunk)));
            if (chunked) {
                request.setHeader(TRANSFER_ENCODING, CHUNKED);
            } else {
                request.setHeader(CONTENT_LENGTH, valueOf((long) LARGE_BODY_CHUNKS * LARGE_BODY_CHUNK_SIZE));
            }
            Future<StreamingHttpResponse> response = connection.request(request).toFuture();
            assertThat(readHeaders(in), startsWith("POST / "));

            // The server doesn't read the payload body, so the client's writes are queued when the response arrives.
            write(socket, "HTTP/1.1 413 Payload Too Large\r\nconnection: close\r\ncontent-length: 0\r\n\r\n");
            StreamingHttpResponse rejection = response.get();
            assertThat(rejection.status(), is(PAYLOAD_TOO_LARGE));
            // Queued writes fail when the connection closes, which must not fail the response.
            rejection.toResponse().toFuture().get();
            connection.onClose().toFuture().get();
        }
    }

    private static ServerSocket newServerSocket() throws IOException {
        ServerSocket server = new ServerSocket(0, 50, getLoopbackAddress());
        server.setSoTimeout(SOCKET_TIMEOUT_MS);
        return server;
    }

    private static Socket accept(ServerSocket server) throws IOException {
        Socket socket = server.accept();
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        return socket;
    }

    private static String readHeaders(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                throw new IOException("Unexpected end of stream");
            }
        } while (!line.isEmpty());
        return requestLine;
    }

    private static String readBody(BufferedReader in, int length) throws IOException {
        char[] body = new char[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) {
                throw new IOException("Unexpected end of stream");
            }
            read += n;
        }
        return new String(body);
    }

    private static void write(Socket socket, String response) throws IOException {
        socket.getOutputStream().write(response.getBytes(US_ASCII));
        socket.getOutputStream().flush();
    }
}
