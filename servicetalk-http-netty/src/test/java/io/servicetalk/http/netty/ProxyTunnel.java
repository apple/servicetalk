/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

final class ProxyTunnel implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyTunnel.class);
    private static final String CONNECT_PREFIX = "CONNECT ";

    private final ExecutorService executor = newCachedThreadPool(new DefaultThreadFactory("proxy-tunnel"));
    private final AtomicInteger connectCount = new AtomicInteger();

    @Nullable
    private ServerSocket serverSocket;
    private ProxyRequestHandler handler = this::handleRequest;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void close() throws Exception {
        try {
            safeClose(serverSocket);
        } finally {
            executor.shutdown();
            executor.awaitTermination(100, MILLISECONDS);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    HostAndPort startProxy() throws IOException {
        serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
        final InetSocketAddress serverSocketAddress = (InetSocketAddress) serverSocket.getLocalSocketAddress();
        executor.submit(() -> {
            while (!executor.isShutdown()) {
                final Socket socket = serverSocket.accept();
                executor.submit(() -> {
                    try {
                        final InputStream in = socket.getInputStream();
                        final String initialLine = readLine(in);
                        while (readLine(in).length() > 0) {
                            // Ignore headers.
                        }

                        handler.handle(socket, initialLine);
                    } catch (Exception e) {
                        LOGGER.debug("Error from proxy {}", socket, e);
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            LOGGER.debug("Error from proxy server socket {} close", socket, e);
                        }
                    }
                });
            }
            return null;
        });

        return HostAndPort.of(serverSocketAddress.getAddress().getHostAddress(), serverSocketAddress.getPort());
    }

    void badResponseProxy() {
        handler = (socket, initialLine) -> {
            final OutputStream os = socket.getOutputStream();
            os.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes(UTF_8));
            os.flush();
        };
    }

    int connectCount() {
        return connectCount.get();
    }

    private static String readLine(final InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
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

    private void handleRequest(final Socket serverSocket, final String initialLine) throws IOException {
        if (initialLine.startsWith(CONNECT_PREFIX)) {
            final int end = initialLine.indexOf(' ', CONNECT_PREFIX.length());
            final String authority = initialLine.substring(CONNECT_PREFIX.length(), end);
            final String protocol = initialLine.substring(end + 1);
            final int colon = authority.indexOf(':');
            final String host = authority.substring(0, colon);
            final int port = Integer.parseInt(authority.substring(colon + 1));

            try (Socket clientSocket = new Socket(host, port)) {
                connectCount.incrementAndGet();
                final OutputStream out = serverSocket.getOutputStream();
                out.write((protocol + " 200 Connection established\r\n\r\n").getBytes(UTF_8));
                out.flush();

                executor.submit(() -> {
                    try {
                        copyStream(out, clientSocket.getInputStream());
                    } catch (IOException e) {
                        LOGGER.debug("Error copying clientSocket input to serverSocket output", e);
                    } finally {
                        try {
                            // We are simulating a proxy that doesn't do half closure. The proxy should close the server
                            // socket as soon as the server read is done.
                            serverSocket.close();
                        } catch (IOException e) {
                            LOGGER.debug("Error closing serverSocket", e);
                        }
                    }
                });
                copyStream(clientSocket.getOutputStream(), serverSocket.getInputStream());

                // Don't wait on the clientSocket input to serverSocket output copy to complete. We want to simulate a
                // proxy that doesn't do half closure and that means we should close as soon as possible. ServiceTalk's
                // CloseHandler should handle this scenario gracefully (and delay FIN/RST until requests/responses are
                // completed).
            }
        } else {
            throw new IllegalArgumentException("Unrecognized initial line: " + initialLine);
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

    @FunctionalInterface
    private interface ProxyRequestHandler {
        void handle(Socket socket, String initialLine) throws IOException;
    }
}
