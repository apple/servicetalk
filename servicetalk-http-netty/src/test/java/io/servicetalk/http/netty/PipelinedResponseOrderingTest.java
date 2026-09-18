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

import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Verifies each response is delivered to the request that produced it when several requests are in flight on a single
 * pipelined HTTP/1.1 connection. Two requests in flight cannot distinguish correct ordering from a reversed tail, so
 * the cases that carry weight are three and above.
 */
class PipelinedResponseOrderingTest {

    private static final int TRIALS = 20;
    private static final String REQ_ID = "x-req-id";
    private static final String RESP_ID = "x-resp-id";

    @ParameterizedTest(name = "{displayName} inFlight={0}")
    @ValueSource(ints = {2, 3, 4, 8, 16})
    void reservedConnectionDeliversResponsesToTheirOwnRequests(int inFlight) throws Exception {
        // The server answers immediately and reads the next request only afterwards, so what puts several responses
        // in flight is the client subscribing all inFlight requests before awaiting any of them.
        BlockingHttpService echo = (ctx, request, factory) ->
                factory.ok().addHeader(RESP_ID, request.headers().get(REQ_ID).toString());
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).listenBlockingAndAwait(echo);
             HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h1().maxPipelinedRequests(inFlight).build())
                     .build()) {
            ReservedHttpConnection connection = client.reserveConnection(client.get("/reserve")).toFuture().get();
            try {
                for (int trial = 0; trial < TRIALS; trial++) {
                    List<Future<HttpResponse>> pending = new ArrayList<>(inFlight);
                    for (int i = 0; i < inFlight; i++) {
                        pending.add(connection.request(
                                connection.get("/r").addHeader(REQ_ID, id(trial, i))).toFuture());
                    }
                    List<String> seen = new ArrayList<>(inFlight);
                    for (Future<HttpResponse> future : pending) {
                        seen.add(responseId(future.get(DEFAULT_TIMEOUT_SECONDS, SECONDS)));
                    }
                    assertThat(seen, is(expectedIds(trial, inFlight)));
                }
            } finally {
                connection.closeAsync().toFuture().get();
            }
        }
    }

    @Test
    void concurrentCallersOverPipelinedConnections() throws Exception {
        // Many callers sharing pipelined connections. A stalled response chain shows up here as a task that never
        // finishes rather than as a mismatch.
        final int callers = 120;
        final int perCaller = 20;
        final int warmConnections = 4;
        BlockingHttpService echo = (ctx, request, factory) ->
                factory.ok().addHeader(RESP_ID, request.headers().get(REQ_ID).toString());
        ExecutorService executor = Executors.newFixedThreadPool(32);
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).listenBlockingAndAwait(echo);
             HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h1().maxPipelinedRequests(16).build())
                     .build()) {
            // Reserving forces distinct connections; releasing returns them to the pool. Without this the callers
            // all start against an empty pool, each opens its own connection, and nothing is pipelined.
            List<ReservedHttpConnection> warm = new ArrayList<>(warmConnections);
            for (int i = 0; i < warmConnections; i++) {
                warm.add(client.reserveConnection(client.get("/warm")).toFuture().get());
            }
            for (ReservedHttpConnection warmed : warm) {
                warmed.releaseAsync().toFuture().get();
            }

            CountDownLatch start = new CountDownLatch(1);
            List<String> mismatches = new CopyOnWriteArrayList<>();
            List<Future<?>> tasks = new ArrayList<>(callers);
            for (int c = 0; c < callers; c++) {
                final int caller = c;
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < perCaller; i++) {
                        String expected = id(caller, i);
                        HttpResponse response = client.request(client.get("/r").addHeader(REQ_ID, expected))
                                .toFuture().get(DEFAULT_TIMEOUT_SECONDS, SECONDS);
                        if (!expected.equals(responseId(response))) {
                            mismatches.add("sent " + expected + " got " + responseId(response));
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
            assertThat(mismatches, is(emptyList()));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String responseId(HttpResponse response) {
        CharSequence id = response.headers().get(RESP_ID);
        return id == null ? "missing" : id.toString();
    }

    private static List<String> expectedIds(int trial, int inFlight) {
        return IntStream.range(0, inFlight).mapToObj(i -> id(trial, i)).collect(Collectors.toList());
    }

    private static String id(int trial, int i) {
        return trial + "-" + i;
    }
}
