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

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.ReservedHttpConnection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.MAX_CONCURRENCY_NO_OFFLOADING;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class H2ServerDefaultMaxConcurrentStreamsTest {

    @Test
    void serverAdvertisesDefaultMaxConcurrentStreamsWhenUnset() throws Exception {
        // Explicitly do NOT call maxConcurrentStreams() on the H2 settings — exercise the server-side default
        // applied by H2ServerParentChannelInitializer.
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(HttpProtocol.HTTP_2.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(HttpProtocol.HTTP_2.config)
                     .build()) {

            BlockingQueue<Integer> maxConcurrentStreams = new LinkedBlockingDeque<>();
            try (ReservedHttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
                // We should avoid racy behavior because the max concurrency event streams are set to replay the last
                // message they observed to new subscribers.
                conn.transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING)
                        .forEach(event -> maxConcurrentStreams.add(event.event()));
                return conn;
            }).toFuture().get()) {
                // In some cases preface and initial settings won't be sent until after we make the first request.
                HttpResponse response = connection.request(connection.get("/")).toFuture().get();
                assertThat(response.status(), is(OK));

                while (maxConcurrentStreams.take() != 100) {
                    // we spin until we get the value we expect, or the test times out.
                }
            }
        }
    }
}
