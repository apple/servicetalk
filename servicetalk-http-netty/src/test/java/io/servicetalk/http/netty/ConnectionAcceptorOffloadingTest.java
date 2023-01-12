/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ReducedConnectionInfo;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConnectionAcceptorOffloadingTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSingleAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = HttpServers.forPort(0).appendConnectionAcceptorFilter(
                ConnectionAcceptorFactory.withStrategy(original ->
                        context -> {
                            offloaded.set(!IoThreadFactory.IoThread.currentThreadIsIoThread());
                            return original.accept(context);
                        },
                        offload ? ConnectExecutionStrategy.offloadAll() : ConnectExecutionStrategy.offloadNone()));
        doRequest(builder);

        assertThat("ConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for ConnectionAcceptor", offloaded.get(), is(offload));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSingleEarlyAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = HttpServers.forPort(0).appendEarlyConnectionAcceptor(new EarlyConnectionAcceptor() {
            @Override
            public Completable accept(final ReducedConnectionInfo info) {
                assertNotNull(info);
                offloaded.set(!IoThreadFactory.IoThread.currentThreadIsIoThread());
                return Completable.completed();
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return offload ? ConnectExecutionStrategy.offloadAll() : ConnectExecutionStrategy.offloadNone();
            }
        });
        doRequest(builder);

        assertThat("EarlyConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for EarlyConnectionAcceptor", offloaded.get(), is(offload));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSingleLateAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = HttpServers.forPort(0).appendLateConnectionAcceptor(new LateConnectionAcceptor() {
            @Override
            public Completable accept(final ConnectionInfo info) {
                assertNotNull(info);
                offloaded.set(!IoThreadFactory.IoThread.currentThreadIsIoThread());
                return Completable.completed();
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return offload ? ConnectExecutionStrategy.offloadAll() : ConnectExecutionStrategy.offloadNone();
            }
        });
        doRequest(builder);

        assertThat("LateConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for LateConnectionAcceptor", offloaded.get(), is(offload));
    }

    /**
     * Tests the offload merging and makes sure that if at least one is offloaded, both are.
     */
    @Test
    void testMultipleEarlyAcceptorOffloading() throws Exception {
        final AtomicInteger numOffloaded = new AtomicInteger();

        EarlyConnectionAcceptor notOffloaded = new EarlyConnectionAcceptor() {
            @Override
            public Completable accept(final ReducedConnectionInfo info) {
                if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                    numOffloaded.incrementAndGet();
                }
                return Completable.completed();
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return ConnectExecutionStrategy.offloadNone();
            }
        };
        EarlyConnectionAcceptor offloaded = info -> {
            if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                numOffloaded.incrementAndGet();
            }
            return Completable.completed();
        };

        HttpServerBuilder builder = HttpServers
                .forPort(0)
                .appendEarlyConnectionAcceptor(notOffloaded)
                .appendEarlyConnectionAcceptor(offloaded);
        doRequest(builder);

        assertEquals(2, numOffloaded.get());
    }

    /**
     * Tests the offload merging and makes sure that if at least one is offloaded, both are.
     */
    @Test
    void testMultipleLateAcceptorOffloading() throws Exception {
        final AtomicInteger numOffloaded = new AtomicInteger();

        LateConnectionAcceptor notOffloaded = new LateConnectionAcceptor() {
            @Override
            public Completable accept(final ConnectionInfo info) {
                if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                    numOffloaded.incrementAndGet();
                }
                return Completable.completed();
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return ConnectExecutionStrategy.offloadNone();
            }
        };
        LateConnectionAcceptor offloaded = info -> {
            if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                numOffloaded.incrementAndGet();
            }
            return Completable.completed();
        };

        HttpServerBuilder builder = HttpServers
                .forPort(0)
                .appendLateConnectionAcceptor(notOffloaded)
                .appendLateConnectionAcceptor(offloaded);
        doRequest(builder);

        assertEquals(2, numOffloaded.get());
    }

    private static void doRequest(final HttpServerBuilder serverBuilder) throws Exception {
        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = serverBuilder.listenAndAwait(service)) {
            SocketAddress serverAddress = server.listenAddress();

            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {
                HttpResponse response = client.request(client.get("/sayHello"));
                assertThat("unexpected status", response.status(), is(HttpResponseStatus.OK));
            }
        }
    }
}
