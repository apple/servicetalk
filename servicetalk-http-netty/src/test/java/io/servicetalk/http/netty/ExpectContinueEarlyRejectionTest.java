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
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Integer.toHexString;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMinutes;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * An early final response to an {@code Expect: 100-continue} request leaves the declared request body unsent.
 * HTTP/1.1 cannot skip a declared body, so the only valid outcomes are to consume it or to close the connection.
 */
class ExpectContinueEarlyRejectionTest {

    private static final String SMUGGLED = "GET /smuggled HTTP/1.1\r\nHost: localhost\r\n\r\n";
    private static final int SOCKET_TIMEOUT_MILLIS = (int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS);

    @ParameterizedTest(name = "{displayName} [{index}] chunked={0}")
    @ValueSource(booleans = {false, true})
    void serverClosesConnectionAfterEarlyRejection(boolean chunked) throws Exception {
        BlockingQueue<String> requestTargets = new LinkedBlockingQueue<>();
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    requestTargets.add(request.requestTarget());
                    return succeeded("/reject".equals(request.requestTarget()) ?
                            responseFactory.unauthorized().addHeader(CONTENT_LENGTH, ZERO) :
                            responseFactory.ok().addHeader(CONTENT_LENGTH, ZERO));
                });
             Socket socket = new Socket()) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            socket.connect(serverContext.listenAddress());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(("POST /reject HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Expect: 100-continue\r\n" +
                    (chunked ? "Transfer-Encoding: chunked" : "Content-Length: " + SMUGGLED.length()) +
                    "\r\n\r\n").getBytes(US_ASCII));
            out.flush();
            String responseHead = readHead(in);
            assertThat(responseHead, startsWith("HTTP/1.1 401"));
            assertThat(responseHead.toLowerCase(Locale.ROOT), containsString("connection: close"));

            // The declared body is a complete request: if the server resumes parsing requests, it executes it.
            out.write((chunked ? toHexString(SMUGGLED.length()) + "\r\n" + SMUGGLED + "\r\n0\r\n\r\n" :
                    SMUGGLED).getBytes(US_ASCII));
            out.flush();
            socket.shutdownOutput();

            assertThat("The server must neither respond to nor execute the declared body",
                    readToEndOfStream(in), is(""));
            assertThat(requestTargets, contains("/reject"));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] chunked={0}")
    @ValueSource(booleans = {false, true})
    void clientClosesConnectionAfterEarlyRejection(boolean chunked) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
             StreamingHttpClient client = HttpClients.forSingleAddress(
                     serverHostAndPort(serverSocket.getLocalSocketAddress()))
                     .expectContinueTimeout(ofMinutes(1)).buildStreaming()) {
            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            Future<StreamingHttpResponse> responseFuture = client.request(client.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(chunked ? TRANSFER_ENCODING : CONTENT_LENGTH,
                            chunked ? CHUNKED : String.valueOf(SMUGGLED.length()))
                    .payloadBody(payload)).toFuture();

            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                assertThat(readHead(in), startsWith("POST / HTTP/1.1"));

                // Reject without "Connection: close", as an RFC-compliant server that keeps the connection open would.
                out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n".getBytes(US_ASCII));
                out.flush();
                assertThat(responseFuture.get().status(), is(UNAUTHORIZED));

                // An RFC-compliant server reads the declared body as part of this request, so the client must not
                // leave it unsent on a connection that is later used for another request.
                assertThat("The client must close the connection or send the declared body",
                        readToEndOfStream(in), is(""));
            }
        }
    }

    @Test
    void clientKeepsConnectionAfterEarlyRejectionWithoutPayloadBody() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
             StreamingHttpClient client = HttpClients.forSingleAddress(
                     serverHostAndPort(serverSocket.getLocalSocketAddress()))
                     .expectContinueTimeout(ofMinutes(1)).buildStreaming();
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            // No payload body is declared, so nothing is left unsent when the server rejects the expectation.
            Future<StreamingHttpResponse> rejected = connection.request(connection.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(CONTENT_LENGTH, ZERO)).toFuture();

            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                assertThat(readHead(in), startsWith("POST / HTTP/1.1"));
                out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n".getBytes(US_ASCII));
                out.flush();
                assertThat(rejected.get().toResponse().toFuture().get().status(), is(UNAUTHORIZED));

                Future<StreamingHttpResponse> next = connection.request(connection.get("/")).toFuture();
                assertThat("The follow-up request must reuse the connection",
                        readHead(in), startsWith("GET / HTTP/1.1"));
                out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(US_ASCII));
                out.flush();
                assertThat(next.get().status(), is(OK));
            }
        }
    }

    private static String readHead(final InputStream in) throws IOException {
        final StringBuilder sb = new StringBuilder();
        while (!endsWith(sb, "\r\n\r\n")) {
            final int b = in.read();
            if (b < 0) {
                throw new IOException("Unexpected end of stream, read so far: " + sb);
            }
            sb.append((char) b);
        }
        return sb.toString();
    }

    private static String readToEndOfStream(final InputStream in) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            int b;
            while ((b = in.read()) >= 0) {
                bytes.write(b);
            }
        } catch (SocketException ignore) {
            // A reset is an acceptable way for the peer to close the connection. A read timeout is not a
            // SocketException, so it still fails the test.
        }
        return new String(bytes.toByteArray(), US_ASCII);
    }

    private static boolean endsWith(final StringBuilder sb, final String suffix) {
        final int offset = sb.length() - suffix.length();
        return offset >= 0 && sb.indexOf(suffix, offset) == offset;
    }
}
