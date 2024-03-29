/*
 * Copyright © 2020-2021, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Imitates behavior of a secure HTTP CONNECT proxy.
 */
public final class ProxyTunnel implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyTunnel.class);
    private static final String CONNECT_PREFIX = CONNECT + " ";

    private final ExecutorService executor = newCachedThreadPool(new DefaultThreadFactory("proxy-tunnel"));
    private final AtomicInteger connectCount = new AtomicInteger();

    @Nullable
    private ServerSocket serverSocket;
    @Nullable
    private volatile String authToken;
    private volatile ProxyRequestHandler handler = this::handleRequest;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void close() throws Exception {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(100, MILLISECONDS);
        }
    }

    /**
     * Starts the proxy server and returns its {@link HostAndPort} to connect to.
     *
     * @return the {@link HostAndPort} to use to connect to the proxy
     * @throws IOException In case of any I/O exception
     */
    public HostAndPort startProxy() throws IOException {
        serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
        executor.submit(() -> {
            while (!executor.isShutdown()) {
                final Socket socket = serverSocket.accept();
                executor.submit(() -> {
                    try {
                        final InputStream in = socket.getInputStream();
                        final String initialLine = readLine(in);
                        if (!initialLine.startsWith(CONNECT_PREFIX)) {
                            throw new IllegalArgumentException("Expected " + CONNECT + " request, but found: " +
                                    initialLine);
                        }
                        final int end = initialLine.indexOf(' ', CONNECT_PREFIX.length());
                        final String authority = initialLine.substring(CONNECT_PREFIX.length(), end);
                        final int colon = authority.indexOf(':');
                        final String host = authority.substring(0, colon);
                        final int port = Integer.parseInt(authority.substring(colon + 1));
                        final String protocol = initialLine.substring(end + 1);

                        final Headers headers = readHeaders(in);
                        if (!authority.equalsIgnoreCase(headers.host)) {
                            badRequest(socket, "Host header value must be identical to authority " +
                                    "component. Expected: " + authority + ", found: " + headers.host);
                            return;
                        }
                        final String authToken = this.authToken;
                        if (authToken != null && !("basic " + authToken).equals(headers.proxyAuthorization)) {
                            proxyAuthRequired(socket, protocol);
                            return;
                        }
                        handler.handle(socket, host, port, protocol);
                    } catch (Exception e) {
                        LOGGER.debug("Error from proxy socket={}", socket, e);
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            LOGGER.debug("Error from proxy server socket={} close", socket, e);
                        }
                    }
                });
            }
            return null;
        });

        return serverHostAndPort(serverSocket.getLocalSocketAddress());
    }

    private static void badRequest(final Socket socket, final String cause) throws IOException {
        final OutputStream os = socket.getOutputStream();
        os.write((HTTP_1_1 + " " + BAD_REQUEST + "\r\n" +
                CONTENT_LENGTH + ": " + cause.length() + "\r\n" +
                "\r\n" + cause).getBytes(UTF_8));
        os.flush();
    }

    private static void proxyAuthRequired(final Socket socket, final String protocol) throws IOException {
        final OutputStream os = socket.getOutputStream();
        os.write((protocol + ' ' + PROXY_AUTHENTICATION_REQUIRED + "\r\n" +
                PROXY_AUTHENTICATE + ": Basic realm=\"simple\"" + "\r\n" +
                "\r\n").getBytes(UTF_8));
        os.flush();
    }

    /**
     * Changes the proxy handler to return 500 instead of 200.
     */
    public void badResponseProxy() {
        handler = (socket, host, port, protocol) -> {
            final OutputStream os = socket.getOutputStream();
            os.write((protocol + ' ' + INTERNAL_SERVER_ERROR + "\r\n\r\n").getBytes(UTF_8));
            os.flush();
        };
    }

    /**
     * Override the default handler to the passed {@link ProxyRequestHandler}.
     *
     * @param handler {@link ProxyRequestHandler} to use
     */
    public void proxyRequestHandler(final ProxyRequestHandler handler) {
        this.handler = handler;
    }

    /**
     * Sets a required {@link HttpHeaderNames#PROXY_AUTHORIZATION} header value for "Basic" scheme to validate before
     * accepting a {@code CONNECT} request.
     *
     * @param authToken the auth token to validate
     */
    public void basicAuthToken(@Nullable String authToken) {
        this.authToken = authToken;
    }

    /**
     * Number of established connections to the proxy.
     *
     * @return Number of established connections to the proxy
     */
    public int connectCount() {
        return connectCount.get();
    }

    private static String readLine(final InputStream in) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(512)) {
            int b;
            while ((b = in.read()) >= 0) {
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    bos.write((byte) b);
                }
            }
            return bos.toString(UTF_8.name());
        }
    }

    private static Headers readHeaders(final InputStream in) throws IOException {
        String host = null;
        String proxyAuthorization = null;
        String line;
        while ((line = readLine(in)).length() > 0) {
            final String lowerCaseLine = line.toLowerCase(Locale.ROOT);
            if (lowerCaseLine.startsWith(HOST.toString())) {
                host = line.substring(HOST.length() + 2 /* colon & space */);
            } else if (lowerCaseLine.startsWith(PROXY_AUTHORIZATION.toString())) {
                proxyAuthorization = line.substring(PROXY_AUTHORIZATION.length() + 2 /* colon & space */);
            }
            // Ignore any other headers.
        }
        return new Headers(host, proxyAuthorization);
    }

    private void handleRequest(final Socket serverSocket, final String host, final int port,
                               final String protocol) throws IOException {
        try (Socket clientSocket = new Socket(host, port)) {
            connectCount.incrementAndGet();
            final OutputStream out = serverSocket.getOutputStream();
            out.write((protocol + " 200 Connection established\r\n\r\n").getBytes(UTF_8));
            out.flush();

            executor.submit(() -> {
                try {
                    copyStream(out, clientSocket.getInputStream());
                } catch (IOException e) {
                    LOGGER.debug("Error copying clientSocket input to serverSocket output " +
                            "clientSocket={} serverSocket={}", clientSocket, serverSocket, e);
                } finally {
                    try {
                        // We are simulating a proxy that doesn't do half closure. The proxy should close the server
                        // socket as soon as the server read is done. ServiceTalk's CloseHandler is expected to
                        // handle this gracefully (and delay FIN/RST until requests/responses complete).
                        // See GracefulConnectionClosureHandlingTest and ConnectionCloseHeaderHandlingTest.
                        serverSocket.close();
                    } catch (IOException e) {
                        LOGGER.debug("Error closing serverSocket={}", serverSocket, e);
                    }
                }
            });
            copyStream(clientSocket.getOutputStream(), serverSocket.getInputStream());

            // Don't wait on the clientSocket input to serverSocket output copy to complete. We want to simulate a
            // proxy that doesn't do half closure and that means we should close as soon as possible. ServiceTalk's
            // CloseHandler should handle this gracefully (and delay FIN/RST until requests/responses complete).
            // See GracefulConnectionClosureHandlingTest and ConnectionCloseHeaderHandlingTest.
        }
        // serverSocket is closed outside the scope of this method.
    }

    private static void copyStream(final OutputStream out, final InputStream cin) throws IOException {
        int read;
        // Intentionally use a small size to increase the likelihood of data fragmentation on the wire.
        final byte[] bytes = new byte[8];
        while ((read = cin.read(bytes)) >= 0) {
            out.write(bytes, 0, read);
            out.flush();
        }
        // Don't close either Stream! We close the socket outside the scope of this method (in a specific sequence).
    }

    /**
     * A handler that processes a parsed CONNECT request.
     */
    @FunctionalInterface
    public interface ProxyRequestHandler {

        /**
         * Handle the parsed CONNECT request.
         *
         * @param socket {@link Socket} from a client to a proxy
         * @param host Host to connect to
         * @param port Port to connect to
         * @param protocol String representation of a protocol used for incoming CONNECT request
         * @throws IOException if any exception happens while working with I/O
         */
        void handle(Socket socket, String host, int port, String protocol) throws IOException;
    }

    private static final class Headers {
        @Nullable
        final String host;
        @Nullable
        final String proxyAuthorization;

        Headers(@Nullable final String host, @Nullable final String proxyAuthorization) {
            this.host = host;
            this.proxyAuthorization = proxyAuthorization;
        }
    }
}
