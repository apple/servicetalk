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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpPayloadChunks;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatuses;
import io.servicetalk.http.api.HttpResponses;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.NettyIoExecutors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NettyHttpServerTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerTest.class);

    private static final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    private static final InetSocketAddress socketAddress = new InetSocketAddress(loopbackAddress, 0);
    private static final TestService service = new TestService();
    private static final Executor executor = Executors.newCachedThreadExecutor();
    private static final IoExecutor ioExecutor = NettyIoExecutors.createExecutor();

    private static ServerContext serverContext;
    private static int port;

    private Socket socket;
    private OutputStream writer;
    private DataInputStream reader;

    @BeforeClass
    public static void startServer() throws Exception {
        serverContext = requireNonNull(awaitIndefinitely(
                new DefaultHttpServerStarter(ioExecutor)
                        .start(socketAddress, executor, service)
                        .doBeforeSuccess(ctx -> LOGGER.info("Server started on {}.", ctx.getListenAddress()))
                        .doBeforeError(throwable -> LOGGER.error("Failed starting server on {}.", socketAddress))));
        final InetSocketAddress listenAddress = (InetSocketAddress) serverContext.getListenAddress();
        port = listenAddress.getPort();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        awaitIndefinitely(serverContext.closeAsync());
        awaitIndefinitely(ioExecutor.closeAsync());
    }

    @Before
    public void beforeTest() throws Exception {
        service.counter.set(1);

        socket = new Socket(loopbackAddress, port);
        writer = socket.getOutputStream();
        reader = new DataInputStream(socket.getInputStream());
    }

    @After
    public void afterTest() throws Exception {
        socket.close();
    }

    @Test
    public void testGetNoRequestPayload() throws Exception {
        send("GET /counterNoLastChunk HTTP/1.1\r\n\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing1\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testGetNoRequestPayloadResponseHasLastChunk() throws Exception {
        send("GET /counter HTTP/1.1\r\n\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing1\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testGetEchoPayloadContentLength() throws Exception {
        send("GET /echo HTTP/1.1\r\n");
        send("content-length: 5\r\n");
        send("\r\n");
        send("hello");

        expect("HTTP/1.1 200 OK\r\n");
        expect("content-length: 5\r\n");
        expect("\r\n");
        expect("hello");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testGetRot13Payload() throws Exception {
        send("GET /rot13 HTTP/1.1\r\n");
        send("content-length: 5\r\n");
        send("\r\n");
        send("hello");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("5\r\n");
        expect("uryyb");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testGetEchoPayloadChunked() throws Exception {
        send("GET /echo HTTP/1.1\r\n");
        send("transfer-encoding: chunked\r\n");
        send("\r\n");
        send("5\r\n");
        send("hello");
        send("\r\n");
        send("0\r\n");
        send("\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("5\r\n");
        expect("hello");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testGetIgnoreRequestPayload() throws Exception {
        send("GET /counterNoLastChunk HTTP/1.1\r\n");
        send("transfer-encoding: chunked\r\n");
        send("\r\n");
        send("5\r\n");
        send("hello");
        send("\r\n");
        send("0\r\n");
        send("\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing1\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testGetNoRequestPayloadNoResponsePayload() throws Exception {
        send("GET /nocontent HTTP/1.1\r\n");
        send("\r\n");

        expect("HTTP/1.1 204 No Content\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testMultipleGetsNoRequestPayload() throws Exception {
        send("GET /counterNoLastChunk HTTP/1.1\r\n\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing1\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        send("GET /counterNoLastChunk HTTP/1.1\r\n\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing2\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testMultipleGetsNoRequestPayloadResponseHasLastChunk() throws Exception {
        send("GET /counter HTTP/1.1\r\n\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing1\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        send("GET /counter HTTP/1.1\r\n\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing2\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testMultipleGetsEchoPayloadContentLength() throws Exception {
        send("GET /echo HTTP/1.1\r\n");
        send("content-length: 5\r\n");
        send("\r\n");
        send("hello");

        expect("HTTP/1.1 200 OK\r\n");
        expect("content-length: 5\r\n");
        expect("\r\n");
        expect("hello");

        send("GET /echo HTTP/1.1\r\n");
        send("content-length: 6\r\n");
        send("\r\n");
        send("world!");

        expect("HTTP/1.1 200 OK\r\n");
        expect("content-length: 6\r\n");
        expect("\r\n");
        expect("world!");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testMultipleGetsEchoPayloadChunked() throws Exception {
        send("GET /echo HTTP/1.1\r\n");
        send("transfer-encoding: chunked\r\n");
        send("\r\n");
        send("5\r\n");
        send("hello");
        send("\r\n");
        send("0\r\n");
        send("\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("5\r\n");
        expect("hello");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        send("GET /echo HTTP/1.1\r\n");
        send("transfer-encoding: chunked\r\n");
        send("\r\n");
        send("6\r\n");
        send("world!");
        send("\r\n");
        send("0\r\n");
        send("\r\n");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("6\r\n");
        expect("world!");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    @Test
    public void testMultipleGetsIgnoreRequestPayload() throws Exception {
        send("GET /counterNoLastChunk HTTP/1.1\r\n");
        send("content-length: 5\r\n");
        send("\r\n");
        send("hello");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing1\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        send("GET /counterNoLastChunk HTTP/1.1\r\n");
        send("content-length: 6\r\n");
        send("\r\n");
        send("world!");

        expect("HTTP/1.1 200 OK\r\n");
        expect("transfer-encoding: chunked\r\n");
        expect("\r\n");
        expect("9\r\n");
        expect("Testing2\n");
        expect("\r\n");
        expect("0\r\n");
        expect("\r\n");

        assertEquals(0, reader.available());
        assertFalse(socket.isClosed());
    }

    private void send(final String text) throws Exception {
        writer.write(text.getBytes(US_ASCII));
        writer.flush();
    }

    private void expect(final String expected) throws Exception {
        final byte[] actualBytes = new byte[expected.length()];
        reader.readFully(actualBytes);
        final String actual = new String(actualBytes, US_ASCII);
        assertEquals(expected, actual);
    }

    private static final class TestService extends HttpService<HttpPayloadChunk, HttpPayloadChunk> {
        private final AtomicInteger counter = new AtomicInteger(1);

        public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext context,
                                                             final HttpRequest<HttpPayloadChunk> req) {
            LOGGER.info("({}) Handling {}", counter, req.toString((a, b) -> b));
            final HttpResponse<HttpPayloadChunk> response;
            switch (req.getPath()) {
                case "/echo":
                    response = newEchoResponse(req);
                    break;
                case "/counterNoLastChunk":
                    response = newTestCounterResponse(context, req);
                    break;
                case "/counter":
                    response = newTestCounterResponseWithLastPayloadChunk(context, req);
                    break;
                case "/nocontent":
                    response = newNoContentResponse(req);
                    break;
                case "/rot13":
                    response = newRot13Response(req);
                    break;
                default:
                    response = newNotFoundResponse(req);
                    break;
            }
            return Single.success(response);
        }

        private HttpResponse<HttpPayloadChunk> newEchoResponse(final HttpRequest<HttpPayloadChunk> req) {
            final HttpResponse<HttpPayloadChunk> response = HttpResponses.newResponse(req.getVersion(), HttpResponseStatuses.OK, req.getPayloadBody());
            final CharSequence contentLength = req.getHeaders().get(CONTENT_LENGTH);
            if (contentLength != null) {
                response.getHeaders().set(CONTENT_LENGTH, contentLength);
            }
            return response;
        }

        private HttpResponse<HttpPayloadChunk> newTestCounterResponse(final ConnectionContext context, final HttpRequest<HttpPayloadChunk> req) {
            final Buffer responseContent = context.getBufferAllocator().fromUtf8("Testing" + counter.getAndIncrement() + "\n");
            final HttpPayloadChunk responseBody = HttpPayloadChunks.newPayloadChunk(responseContent);
            return HttpResponses.newResponse(req.getVersion(), HttpResponseStatuses.OK, responseBody, executor);
        }

        private HttpResponse<HttpPayloadChunk> newTestCounterResponseWithLastPayloadChunk(final ConnectionContext context, final HttpRequest<HttpPayloadChunk> req) {
            final Buffer responseContent = context.getBufferAllocator().fromUtf8("Testing" + counter.getAndIncrement() + "\n");
            final HttpPayloadChunk responseBody = HttpPayloadChunks.newLastPayloadChunk(responseContent, DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers());
            return HttpResponses.newResponse(req.getVersion(), HttpResponseStatuses.OK, responseBody, executor);
        }

        private HttpResponse<HttpPayloadChunk> newNoContentResponse(final HttpRequest<HttpPayloadChunk> req) {
            return HttpResponses.newResponse(req.getVersion(), HttpResponseStatuses.NO_CONTENT, executor);
        }

        private HttpResponse<HttpPayloadChunk> newRot13Response(final HttpRequest<HttpPayloadChunk> req) {
            final Publisher<HttpPayloadChunk> responseBody = req.getPayloadBody().map(chunk -> {
                final Buffer buffer = chunk.getContent();
                // Do an ASCII-only ROT13
                for (int i = buffer.getReaderIndex(); i < buffer.getWriterIndex(); i++) {
                    final byte c = buffer.getByte(i);
                    if (c >= 'a' && c <= 'm' || c >= 'A' && c <= 'M') {
                        buffer.setByte(i, c + 13);
                    } else if (c >= 'n' && c <= 'z' || c >= 'N' && c <= 'Z') {
                        buffer.setByte(i, c - 13);
                    }
                }
                return chunk;
            });
            return HttpResponses.newResponse(req.getVersion(), HttpResponseStatuses.OK, responseBody);
        }

        private HttpResponse<HttpPayloadChunk> newNotFoundResponse(final HttpRequest<HttpPayloadChunk> req) {
            return HttpResponses.newResponse(req.getVersion(), HttpResponseStatuses.NOT_FOUND, executor);
        }
    }
}
