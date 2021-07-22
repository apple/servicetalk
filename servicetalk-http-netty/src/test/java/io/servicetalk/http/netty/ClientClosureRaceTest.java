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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.utils.RetryingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofNanos;

@Timeout(90)
class ClientClosureRaceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientClosureRaceTest.class);
    private static final int ITERATIONS = 600;
    @Nullable
    private static ExecutorService executor;
    private ServerSocket serverSocket;

    @BeforeAll
    static void beforeClass() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterAll
    static void afterClass() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        assert executor != null;
        serverSocket = new ServerSocket(0, 50, getLoopbackAddress());

        executor.submit(() -> {
            while (!executor.isShutdown()) {
                final Socket socket = serverSocket.accept();
                executor.submit(() -> {
                    try {
                        final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                US_ASCII));
                        final PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),
                                US_ASCII));

                        final String initialLine = in.readLine();
                        LOGGER.debug("initialLine: " + initialLine);
                        String line;
                        while ((line = in.readLine()) != null && !line.isEmpty()) {
                            LOGGER.debug("line: " + line);
                        }
                        out.print("HTTP/1.1 200 OK\r\n");
                        out.print("content-length: 12\r\n");
                        // out.print("connection: close\r\n"); // Don't send this, so the closure is unexpected.
                        out.print("\r\n");
                        out.print("Hello world!");
                        out.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
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

    @AfterEach
    void stopServer() throws Exception {
        serverSocket.close();
    }

    @Test
    void testSequential() throws Exception {
        try (HttpClient client = newClientBuilder().build()) {
            runIterations(() -> client.request(client.get("/foo")).flatMap(
                    response -> client.request(client.get("/bar"))));
        }
    }

    @Test
    void testSequentialPosts() throws Exception {
        try (HttpClient client = newClientBuilder().build()) {
            runIterations(() ->
                    client.request(client.post("/foo").payloadBody("Some payload", textSerializerUtf8()))
                            .flatMap(response -> client.request(client.post("/bar")
                                    .payloadBody("Another payload", textSerializerUtf8()))));
        }
    }

    @Test
    void testPipelined() throws Exception {
        try (HttpClient client = newClientBuilder()
                .protocols(h1().maxPipelinedRequests(2).build())
                .build()) {
            runIterations(() -> collectUnordered(client.request(client.get("/foo")),
                    client.request(client.get("/bar"))));
        }
    }

    @Test
    void testPipelinedPosts() throws Exception {
        try (HttpClient client = newClientBuilder()
                .protocols(h1().maxPipelinedRequests(2).build())
                .build()) {
            runIterations(() -> collectUnordered(
                    client.request(client.get("/foo").payloadBody("Some payload", textSerializerUtf8())),
                    client.request(client.get("/bar").payloadBody("Another payload", textSerializerUtf8()))));
        }
    }

    private void runIterations(Callable<Single<?>> test) throws Exception {
        int count = 0;
        try {
            while (count < ITERATIONS) {
                count++;
                Object response = test.call().toFuture().get();
                LOGGER.debug("Response {} = {}", count, response);
            }
        } finally {
            if (count != ITERATIONS) {
                LOGGER.info("Completed {} requests", count);
            }
        }
    }

    private SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder() {
        final RetryingHttpRequesterFilter.Builder retryBuilder = new RetryingHttpRequesterFilter.Builder();
        return HttpClients.forSingleAddress(HostAndPort.of((InetSocketAddress) serverSocket.getLocalSocketAddress()))
                .appendClientFilter(retryBuilder.maxRetries(Integer.MAX_VALUE)
                        .retryFor((md, t) ->
                            // This test has the server intentionally hard-close the connection after responding
                            // to the first request, however some tests use pipelining and may write multiple requests
                            // on the same connection which would result in a non-retryable exception. Since this test
                            // doesn't care about idempotency it should always retry.
                            true
                        ).buildWithConstantBackoffFullJitter(ofNanos(1)));
    }
}
