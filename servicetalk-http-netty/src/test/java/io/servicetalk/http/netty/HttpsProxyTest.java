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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.api.SslConfigBuilder.forClientWithoutServerIdentity;
import static io.servicetalk.transport.api.SslConfigBuilder.forServer;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class HttpsProxyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpsProxyTest.class);

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    private static final String CONNECT_PREFIX = "CONNECT ";
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private int proxyPort;
    private int serverPort;
    private HttpClient client;
    private final AtomicInteger connectCount = new AtomicInteger();
    private ProxyRequestHandler handler = this::handleRequest;

    @Before
    public void setup() throws Exception {
        startProxy();
        startServer();
        createClient();
    }

    public void startProxy() throws Exception {
        serverSocket = new ServerSocket(0);
        proxyPort = serverSocket.getLocalPort();
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
            executor.submit(() -> copyStream(out, cin));
            copyStream(clientSocket.getOutputStream(), in);
        } else {
            throw new RuntimeException("Unrecognized initial line: " + initialLine);
        }
    }

    public void startServer() throws Exception {
        final ServerContext serverContext = HttpServers.forPort(0)
                .sslConfig(forServer(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build())
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializer())));
        serverPort = serverHostAndPort(serverContext).port();
    }

    public void createClient() {
        client = HttpClients.forSingleAddress("localhost", serverPort)
                .proxyAddress(HostAndPort.of("localhost", proxyPort))
                .sslConfig(forClientWithoutServerIdentity().trustManager(DefaultTestCerts::loadMutualAuthCaPem).build())
                .build();
    }

    @Test
    public void testRequest() throws Exception {
        final HttpResponse httpResponse = client.request(client.get("/path")).toFuture().get();
        assertThat(httpResponse.status(), is(OK));
        assertThat(connectCount.get(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: localhost:" + serverPort));
    }

    @Test
    public void testBadProxyResponse() throws Exception {
        handler = (socket, in, initialLine) -> {
            socket.getOutputStream().write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes(UTF_8));
            socket.getOutputStream().flush();
        };

        final Future<HttpResponse> httpResponseFuture = client.request(client.get("/path")).toFuture();

        expected.expect(ExecutionException.class);
        expected.expectCause(Matchers.instanceOf(ProxyResponseException.class));
        httpResponseFuture.get();
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
