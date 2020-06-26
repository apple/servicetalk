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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.SecurityConfigurator;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.AutoClosableUtils.closeAndReThrowUnchecked;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.Matchers.contentEqualTo;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.String.valueOf;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class HttpsProxyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpsProxyTest.class);

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private static final String CONNECT_PREFIX = "CONNECT ";
    @Nullable
    private ServerSocket serverSocket;
    @Nullable
    private ExecutorService executor;
    @Nullable
    private HostAndPort proxyAddress;
    @Nullable
    private IoExecutor serverIoExecutor;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private HostAndPort serverAddress;
    @Nullable
    private BlockingHttpClient client;
    private final AtomicInteger connectCount = new AtomicInteger();
    private ProxyRequestHandler handler = this::handleRequest;
    private final CountDownLatch responseReceived = new CountDownLatch(1);
    private final AtomicInteger requestPayloadSize = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        startProxy();
        startServer();
        createClient();
    }

    @After
    public void tearDown() throws Exception {
        safeClose(client);
        safeClose(serverContext);
        safeClose(serverSocket);
        try {
            if (executor != null) {
                executor.shutdown();
                executor.awaitTermination(100, MILLISECONDS);
            }
        } finally {
            if (serverIoExecutor != null) {
                serverIoExecutor.closeAsync().toFuture().get();
            }
        }
    }

    static void safeClose(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            closeAndReThrowUnchecked(closeable);
        }
    }

    public void startProxy() throws Exception {
        serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
        proxyAddress = HostAndPort.of((InetSocketAddress) serverSocket.getLocalSocketAddress());
        executor = Executors.newCachedThreadPool();

        executor.submit(() -> {
            while (!executor.isShutdown()) {
                final Socket socket = serverSocket.accept();
                executor.submit(() -> {
                    try {
                        final InputStream in = socket.getInputStream();
                        final String initialLine = readLine(in);
                        while (readLine(in).length() > 0) {
                            // ignore headers
                        }

                        handler.handle(socket, in, initialLine);
                    } catch (Exception e) {
                        LOGGER.error("Error from proxy", e);
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface ProxyRequestHandler {
        void handle(Socket socket, InputStream in, String initialLine) throws IOException;
    }

    private void handleRequest(final Socket socket, final InputStream in, final String initialLine) throws IOException {
        if (initialLine.startsWith(CONNECT_PREFIX)) {
            final int end = initialLine.indexOf(' ', CONNECT_PREFIX.length());
            final String authority = initialLine.substring(CONNECT_PREFIX.length(), end);
            final String protocol = initialLine.substring(end + 1);
            final int colon = authority.indexOf(':');
            final String host = authority.substring(0, colon);
            final int port = Integer.parseInt(authority.substring(colon + 1));

            final Socket clientSocket = new Socket(host, port);
            connectCount.incrementAndGet();
            final OutputStream out = socket.getOutputStream();
            out.write((protocol + " 200 Connection established\r\n\r\n").getBytes(UTF_8));
            out.flush();

            final InputStream cin = clientSocket.getInputStream();
            assert executor != null;
            executor.submit(() -> copyStream(out, cin));
            copyStream(clientSocket.getOutputStream(), in);
        } else {
            throw new RuntimeException("Unrecognized initial line: " + initialLine);
        }
    }

    public void startServer() throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(serverIoExecutor = createIoExecutor(new IoThreadFactory("server-io-executor")))
                .secure()
                .provider(SecurityConfigurator.SslProvider.JDK)
                .commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    final String content = "host: " + request.headers().get(HOST);
                    response.addHeader(CONTENT_LENGTH, valueOf(content.length()));
                    if ("/close".equals(request.path())) {
                        response.addHeader(CONNECTION, CLOSE);
                    }

                    try (HttpPayloadWriter<String> writer = response.sendMetaData(textSerializer())) {
                        if ("/close".equals(request.path())) {
                            // Defer payload body to see how client processes "Connection: close" header
                            request.payloadBody().forEach(chunk -> requestPayloadSize.addAndGet(chunk.readableBytes()));
                            responseReceived.await();
                        }
                        writer.write(content);
                    }
                });
        serverAddress = serverHostAndPort(serverContext);
    }

    public void createClient() {
        assert serverAddress != null && proxyAddress != null;
        client = HttpClients
                .forSingleAddressViaProxy(serverAddress, proxyAddress)
                .secure().disableHostnameVerification().trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                .provider(SecurityConfigurator.SslProvider.JDK).commit()
                .buildBlocking();
    }

    @Test
    public void testRequest() throws Exception {
        assert client != null;
        final HttpResponse httpResponse = client.request(client.get("/path"));
        assertThat(httpResponse.status(), is(OK));
        assertThat(connectCount.get(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
    }

    @Test
    public void testBadProxyResponse() {
        handler = (socket, in, initialLine) -> {
            socket.getOutputStream().write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes(UTF_8));
            socket.getOutputStream().flush();
        };
        assert client != null;
        assertThrows(ProxyResponseException.class, () -> client.request(client.get("/path")));
    }

    @Test
    public void testResponseWithConnectionCloseHeaderAndDelayedResponsePayloadBody() throws Exception {
        assert client != null;
        StreamingHttpClient client = this.client.asStreamingClient();
        final StreamingHttpRequest request = client.get("/close")
                .addHeader(CONTENT_LENGTH, ZERO);
        StreamingHttpResponse response = client.request(request).toFuture().get();
        assertResponse(request, response);
    }

    @Test
    public void testResponseWithConnectionCloseHeaderAndDelayedRequestPayloadBody() throws Exception {
        assert client != null;
        StreamingHttpClient client = this.client.asStreamingClient();
        String content = "hello";
        final StreamingHttpRequest request = client.post("/close")
                .addHeader(CONTENT_LENGTH, valueOf(content.length()))
                .payloadBody(client.executionContext().executor().submit(() -> {
                    try {
                        responseReceived.await();
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                }).concat(from(content)), textSerializer());
        StreamingHttpResponse response = client.request(request).toFuture().get();
        assertResponse(request, response);
    }

    private void assertResponse(StreamingHttpRequest request, StreamingHttpResponse response) throws Exception {
        assertThat(response.status(), is(OK));
        assertThat(connectCount.get(), is(1));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        responseReceived.countDown();

        int actualContentLength = response.payloadBody().map(Buffer::readableBytes)
                .collect(AtomicInteger::new, (total, current) -> {
                    total.addAndGet(current);
                    return total;
                }).toFuture().get().get();
        assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(actualContentLength)));
        assertThat(request.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(requestPayloadSize.get())));
    }

    private static String readLine(final InputStream in) throws IOException {
        byte[] bytes = new byte[1024];
        int i = 0;
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                bytes[i++] = (byte) b;
            }
        }
        return new String(bytes, 0, i, UTF_8);
    }

    private static void copyStream(final OutputStream out, final InputStream cin) {
        try {
            int b;
            while ((b = cin.read()) >= 0) {
                out.write(b);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
