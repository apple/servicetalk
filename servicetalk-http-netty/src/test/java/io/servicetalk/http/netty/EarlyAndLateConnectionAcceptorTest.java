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
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import io.netty.channel.unix.Errors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyAndLateConnectionAcceptorTest {
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
        doSuccessRequestResponse(builder, ServerType.HTTP_1);

        assertThat("ConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for ConnectionAcceptor", offloaded.get(), is(offload));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSingleEarlyAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = HttpServers.forPort(0).appendEarlyConnectionAcceptor(new EarlyConnectionAcceptor() {
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
        doSuccessRequestResponse(builder, ServerType.HTTP_1);

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
        doSuccessRequestResponse(builder, ServerType.HTTP_1);

        assertThat("LateConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for LateConnectionAcceptor", offloaded.get(), is(offload));
    }

    /**
     * Tests the offload merging and makes sure that if at least one is offloaded, both are.
     */
    @ParameterizedTest
    @MethodSource("multipleAcceptorsOffloadingArgs")
    void testMultipleAcceptorsOffloading(boolean firstOffloaded, boolean secondOffloaded, boolean thirdOffloaded,
                                         ServerType serverType)
            throws Exception {
        final AtomicInteger earlyOffloaded = new AtomicInteger();
        final AtomicInteger lateOffloaded = new AtomicInteger();
        final Queue<Integer> executionOrder = new ArrayBlockingQueue<>(6);

        HttpServerBuilder builder = HttpServers
                .forPort(0)
                .appendEarlyConnectionAcceptor(earlyAcceptor(firstOffloaded, earlyOffloaded, executionOrder, 1))
                .appendEarlyConnectionAcceptor(earlyAcceptor(secondOffloaded, earlyOffloaded, executionOrder, 2))
                .appendEarlyConnectionAcceptor(earlyAcceptor(thirdOffloaded, earlyOffloaded, executionOrder, 3))
                .appendLateConnectionAcceptor(lateAcceptor(firstOffloaded, lateOffloaded, executionOrder, 4))
                .appendLateConnectionAcceptor(lateAcceptor(secondOffloaded, lateOffloaded, executionOrder, 5))
                .appendLateConnectionAcceptor(lateAcceptor(thirdOffloaded, lateOffloaded, executionOrder, 6));

        doSuccessRequestResponse(builder, serverType);

        assertEquals(3, earlyOffloaded.get());
        assertEquals(3, lateOffloaded.get());
        assertArrayEquals(new Integer[] {1, 2, 3, 4, 5, 6}, executionOrder.toArray(new Integer[0]));
    }

    private static Stream<Arguments> multipleAcceptorsOffloadingArgs() {
        return Stream.of(
                Arguments.of(true, false, false, ServerType.HTTP_1),
                Arguments.of(false, true, false, ServerType.HTTP_1),
                Arguments.of(false, false, true, ServerType.HTTP_1),
                Arguments.of(true, false, false, ServerType.HTTP_1_TLS),
                Arguments.of(false, true, false, ServerType.HTTP_1_TLS),
                Arguments.of(false, false, true, ServerType.HTTP_1_TLS),
                Arguments.of(true, false, false, ServerType.HTTP_2),
                Arguments.of(false, true, false, ServerType.HTTP_2),
                Arguments.of(false, false, true, ServerType.HTTP_2)
        );
    }

    private static EarlyConnectionAcceptor earlyAcceptor(boolean shouldOffload, final AtomicInteger numOffloaded,
                                                         final Queue<Integer> executionOrder, final int numOrder) {
        if (shouldOffload) {
            return info -> {
                if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                    numOffloaded.incrementAndGet();
                }
                executionOrder.offer(numOrder);
                return Completable.completed();
            };
        } else {
            return new EarlyConnectionAcceptor() {
                @Override
                public Completable accept(final ConnectionInfo info) {
                    if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                        numOffloaded.incrementAndGet();
                    }
                    executionOrder.offer(numOrder);
                    return Completable.completed();
                }

                @Override
                public ConnectExecutionStrategy requiredOffloads() {
                    return ConnectExecutionStrategy.offloadNone();
                }
            };
        }
    }

    private static LateConnectionAcceptor lateAcceptor(boolean shouldOffload, final AtomicInteger numOffloaded,
                                                        final Queue<Integer> executionOrder, final int numOrder) {
        if (shouldOffload) {
            return info -> {
                if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                    numOffloaded.incrementAndGet();
                }
                executionOrder.offer(numOrder);
                return Completable.completed();
            };
        } else {
            return new LateConnectionAcceptor() {
                @Override
                public Completable accept(final ConnectionInfo info) {
                    if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                        numOffloaded.incrementAndGet();
                    }
                    executionOrder.offer(numOrder);
                    return Completable.completed();
                }

                @Override
                public ConnectExecutionStrategy requiredOffloads() {
                    return ConnectExecutionStrategy.offloadNone();
                }
            };
        }
    }

    enum ServerType {
        HTTP_1,
        HTTP_1_TLS,
        HTTP_2
    }

    private static void doSuccessRequestResponse(final HttpServerBuilder serverBuilder, final ServerType serverType)
            throws Exception {

        ServerSslConfig serverSslConfig = new ServerSslConfigBuilder(
                DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build();

        if (serverType == ServerType.HTTP_1) {
            serverBuilder.protocols(h1Default());
        } else if (serverType == ServerType.HTTP_1_TLS) {
            serverBuilder.protocols(h1Default()).sslConfig(serverSslConfig);
        } else if (serverType == ServerType.HTTP_2) {
            serverBuilder.protocols(h2Default()).sslConfig(serverSslConfig);
        }

        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = serverBuilder.listenAndAwait(service)) {
            SocketAddress serverAddress = server.listenAddress();

            ClientSslConfig clientSslConfig = new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build();
            final SingleAddressHttpClientBuilder<SocketAddress, SocketAddress> clientBuilder =
                    HttpClients.forResolvedAddress(serverAddress);
            if (serverType == ServerType.HTTP_1) {
                clientBuilder.protocols(h1Default());
            } else if (serverType == ServerType.HTTP_1_TLS) {
                clientBuilder.protocols(h1Default()).sslConfig(clientSslConfig);
            } else if (serverType == ServerType.HTTP_2) {
                clientBuilder.protocols(h2Default()).sslConfig(clientSslConfig);
            }

            try (BlockingHttpClient client = clientBuilder.buildBlocking()) {
                HttpResponse response = client.request(client.get("/sayHello"));
                assertThat("unexpected status", response.status(), is(HttpResponseStatus.OK));
            }
        }
    }

    /**
     * Verifies that the {@link io.servicetalk.transport.api.EarlyConnectionAcceptor} can reject incoming connections.
     */
    @Test
    void earlyConnectionAcceptorCanReject() throws Exception {
        HttpServerBuilder builder = HttpServers.forPort(0)
                .appendEarlyConnectionAcceptor(info -> Completable.failed(new Exception("woops")));

        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = builder.listenAndAwait(service)) {
            SocketAddress serverAddress = server.listenAddress();

            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {

                Errors.NativeIoException ex = Assertions.assertThrows(
                        Errors.NativeIoException.class, () -> client.request(client.get("/sayHello")));
                assertTrue(ex.getMessage().contains("Connection reset by peer"));
            }
        }
    }

    /**
     * Verifies that the {@link io.servicetalk.transport.api.LateConnectionAcceptor} can reject incoming connections.
     */
    @Test
    void lateConnectionAcceptorCanReject() throws Exception {
        HttpServerBuilder builder = HttpServers.forPort(0)
                .appendLateConnectionAcceptor(info -> Completable.failed(new Exception("woops")));

        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = builder.listenAndAwait(service)) {
            SocketAddress serverAddress = server.listenAddress();

            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {

                Errors.NativeIoException ex = Assertions.assertThrows(
                        Errors.NativeIoException.class, () -> client.request(client.get("/sayHello")));
                assertTrue(ex.getMessage().contains("Connection reset by peer"));
            }
        }
    }
}
