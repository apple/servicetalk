/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpContextKeys;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReserveConnectionTest {

    private HttpServerContext serverContext;
    private BlockingHttpClient httpClient;
    private AtomicInteger createdConnections;

    @BeforeEach
    void setup() throws Exception {
        createdConnections = new AtomicInteger(0);

        serverContext = HttpServers
                .forAddress(localAddress(0))
                .listenBlocking((ctx1, request, responseFactory) -> responseFactory.ok()).toFuture().get();

        InetSocketAddress listenAddress = (InetSocketAddress) serverContext.listenAddress();
        httpClient = HttpClients
                .forSingleAddress(listenAddress.getHostName(), listenAddress.getPort())
                .appendConnectionFactoryFilter(o ->
                        new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(o) {
                            @Override
                            public Single<FilterableStreamingHttpConnection> newConnection(
                                    final InetSocketAddress inetSocketAddress,
                                    @Nullable final ContextMap context,
                                    @Nullable final TransportObserver observer) {
                                return delegate()
                                        .newConnection(inetSocketAddress, context, observer)
                                        .beforeOnSuccess(c -> createdConnections.incrementAndGet());
                            }
                        })
                .buildBlocking();
    }

    @AfterEach
    void cleanup() throws Exception {
        httpClient.close();
        serverContext.close();
    }

    @Test
    void reusesConnectionOnReserve() throws Exception {
        HttpRequest metaData = httpClient.get("/");

        ReservedBlockingHttpConnection connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();

        assertEquals(1, createdConnections.get());
    }

    @Test
    void canForceNewConnection() throws Exception {
        HttpRequest metaData = httpClient.get("/");
        metaData.context().put(HttpContextKeys.HTTP_FORCE_NEW_CONNECTION, true);

        ReservedBlockingHttpConnection connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();

        assertEquals(3, createdConnections.get());
    }
}
