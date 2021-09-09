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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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

    @Override
    public void close() throws Exception {
        try {
            safeClose(serverSocket);
        } finally {
            executor.shutdown();
            executor.awaitTermination(100, MILLISECONDS);
        }
    }

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
                            // ignore headers
                        }

                        handler.handle(socket, in, initialLine);
                    } catch (Exception e) {
                        LOGGER.debug("Error from proxy", e);
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            LOGGER.debug("Error from proxy server socket close", e);
                        }
                    }
                });
            }
            return null;
        });

        return HostAndPort.of(serverSocketAddress.getAddress().getHostAddress(), serverSocketAddress.getPort());
    }

    void badResponseProxy() {
        handler = (socket, in, initialLine) -> {
            socket.getOutputStream().write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes(UTF_8));
            socket.getOutputStream().flush();
        };
    }

    int connectCount() {
        return connectCount.get();
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

    private void handleRequest(final Socket socket, final InputStream in, final String initialLine) throws IOException,
            ExecutionException, InterruptedException {
        try {
            if (initialLine.startsWith(CONNECT_PREFIX)) {
                final int end = initialLine.indexOf(' ', CONNECT_PREFIX.length());
                final String authority = initialLine.substring(CONNECT_PREFIX.length(), end);
                final String protocol = initialLine.substring(end + 1);
                final int colon = authority.indexOf(':');
                final String host = authority.substring(0, colon);
                final int port = Integer.parseInt(authority.substring(colon + 1));

                try (Socket clientSocket = new Socket(host, port)) {
                    connectCount.incrementAndGet();
                    final OutputStream out = socket.getOutputStream();
                    out.write((protocol + " 200 Connection established\r\n\r\n").getBytes(UTF_8));
                    out.flush();

                    final InputStream cin = clientSocket.getInputStream();
                    Future<Void> f = executor.submit(() -> {
                        copyStream(out, cin);
                        return null;
                    });
                    copyStream(clientSocket.getOutputStream(), in);
                    f.get(); // wait for the copy of proxy client input to server output to finish copying.
                }
            } else {
                throw new RuntimeException("Unrecognized initial line: " + initialLine);
            }
        } finally {
            in.close();
        }
    }

    private static void copyStream(final OutputStream out, final InputStream cin) throws IOException {
        try {
            int b;
            while ((b = cin.read()) >= 0) {
                out.write(b);
            }
            out.flush();
        } finally {
            try {
                cin.close();
            } finally {
                out.close();
            }
        }
    }

    @FunctionalInterface
    private interface ProxyRequestHandler {
        void handle(Socket socket, InputStream in, String initialLine) throws IOException,
                ExecutionException, InterruptedException;
    }
}
