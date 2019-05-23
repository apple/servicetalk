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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.utils.RetryingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;

import io.netty.channel.unix.Errors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class ClientClosureRaceTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientClosureRaceTest.class);
    public static final int ITERATIONS = ServiceTalkTestTimeout.CI ? 1000 : 500;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private int port;

    @Before
    public void startServer() throws Exception {
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
        executor = Executors.newCachedThreadPool();

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

    @After
    public void stopServer() throws Exception {
        serverSocket.close();
        executor.shutdownNow();
    }

    @Test
    public void testSequential() throws Exception {
        final HttpClient client = newClientBuilder().maxPipelinedRequests(1).build();

        int count = 0;
        try {
            while (count < ITERATIONS) {
                count++;
                final Single<HttpResponse> responseSingle1 = client.request(client.get("/foo"));
                final Single<HttpResponse> responseSingle2 = responseSingle1.flatMap(
                        response -> client.request(client.get("/bar")));
                try {
                    final HttpResponse response = responseSingle2.whenOnError(t -> LOGGER.error("Encountered error", t))
                            .toFuture().get();
                    LOGGER.debug("Response {} = {}", count, response);
                } catch (ExecutionException e) {
                    if (isReadError(e)) {
                        // occasionally expected due to the race
                        continue;
                    }
                    throw e;
                }
            }
        } finally {
            LOGGER.info("Completed {} requests", count);
        }
    }

    @Test
    public void testSequentialPosts() throws Exception {
        final HttpClient client = newClientBuilder().maxPipelinedRequests(1).build();

        int count = 0;
        try {
            while (count < ITERATIONS) {
                count++;
                final Single<HttpResponse> responseSingle1 = client.request(client.post("/foo")
                        .payloadBody("Some payload", textSerializer()));
                final Single<HttpResponse> responseSingle2 = responseSingle1.flatMap(
                        response -> client.request(client.post("/bar")
                                .payloadBody("Another payload", textSerializer())));
                try {
                    final HttpResponse response = responseSingle2.whenOnError(t -> LOGGER.error("Encountered error", t))
                            .toFuture().get();
                    LOGGER.debug("Response {} = {}", count, response);
                } catch (ExecutionException e) {
                    if (isReadError(e)) {
                        // occasionally expected due to the race
                        continue;
                    }
                    throw e;
                }
            }
        } finally {
            LOGGER.info("Completed {} requests", count);
        }
    }

    @Test
    public void testPipelined() throws Exception {
        final HttpClient client = newClientBuilder().maxPipelinedRequests(2).build();

        int count = 0;
        try {
            while (count < ITERATIONS) {
                count++;
                final Single<HttpResponse> responseSingle1 = client.request(client.get("/foo"));
                final Single<HttpResponse> responseSingle2 = client.request(client.get("/bar"));
                try {
                    final Collection<HttpResponse> responses = Single.collectUnordered(responseSingle1, responseSingle2)
                            .whenOnError(t -> LOGGER.error("Encountered error", t)).toFuture().get();
                    LOGGER.debug("Response {} = {}", count, responses);
                } catch (ExecutionException e) {
                    if (isReadError(e)) {
                        // occasionally expected due to the race
                        continue;
                    }
                    throw e;
                }
            }
        } finally {
            LOGGER.info("Completed {} requests", count);
        }
    }

    @Test
    public void testPipelinedPosts() throws Exception {
        final HttpClient client = newClientBuilder().maxPipelinedRequests(2).build();

        int count = 0;
        try {
            while (count < ITERATIONS) {
                count++;
                final Single<HttpResponse> responseSingle1 = client.request(client.get("/foo")
                        .payloadBody("Some payload", textSerializer()));
                final Single<HttpResponse> responseSingle2 = client.request(client.get("/bar")
                        .payloadBody("Another payload", textSerializer()));
                try {
                    final Collection<HttpResponse> responses = Single.collectUnordered(responseSingle1, responseSingle2)
                            .whenOnError(t -> LOGGER.error("Encountered error", t)).toFuture().get();
                    LOGGER.debug("Response {} = {}", count, responses);
                } catch (ExecutionException e) {
                    if (isReadError(e)) {
                        // occasionally expected due to the race
                        continue;
                    }
                    throw e;
                }
            }
        } finally {
            LOGGER.info("Completed {} requests", count);
        }
    }

    private SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder() {
        return HttpClients.forSingleAddress("localhost", port)
                .enableWireLogging("servicetalk-tests-client-wire-logger")
                .appendClientFilter(new RetryingHttpRequesterFilter.Builder().maxRetries(10)
                        .buildWithImmediateRetries());
    }

    private static boolean isReadError(final ExecutionException e) {
        // This exception instance check will likely need to be updated for Windows builds.
        return (e.getCause() instanceof Errors.NativeIoException &&
                e.getCause().getMessage().contains("syscall:read")) ||
                (e.getCause() instanceof ClosedChannelException &&
                        e.getCause().getMessage().startsWith("CHANNEL_CLOSED_INBOUND"));
    }
}
