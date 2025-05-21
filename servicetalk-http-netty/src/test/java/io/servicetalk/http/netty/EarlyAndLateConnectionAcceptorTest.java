/*
 * Copyright © 2023, 2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.EarlyConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.LateConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.time.Duration.ofMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EarlyAndLateConnectionAcceptorTest {

    @ParameterizedTest(name = "{displayName} [{index}] offload={0}")
    @ValueSource(booleans = {false, true})
    void testSingleAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = serverBuilder().appendConnectionAcceptorFilter(
                ConnectionAcceptorFactory.withStrategy(original ->
                        context -> {
                            offloaded.set(!IoThreadFactory.IoThread.currentThreadIsIoThread());
                            return original.accept(context);
                        },
                        offload ? ConnectExecutionStrategy.offloadAll() : ConnectExecutionStrategy.offloadNone()));
        doSuccessRequestResponse(builder, Protocol.H1);

        assertThat("ConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for ConnectionAcceptor", offloaded.get(), is(offload));
    }

    @ParameterizedTest(name = "{displayName} [{index}] offload={0}")
    @ValueSource(booleans = {false, true})
    void testSingleEarlyAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = serverBuilder().appendEarlyConnectionAcceptor(new EarlyConnectionAcceptor() {
            @Override
            public Completable accept(final ConnectionInfo info) {
                throw new UnsupportedOperationException("Not expected to be invoked");
            }

            @Override
            public Completable accept(final ConnectionContext ctx) {
                assertNotNull(ctx);
                assertNotNull(ctx.sslConfig());
                assertNull(ctx.sslSession());
                offloaded.set(!IoThreadFactory.IoThread.currentThreadIsIoThread());
                return Completable.completed();
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return offload ? ConnectExecutionStrategy.offloadAll() : ConnectExecutionStrategy.offloadNone();
            }
        });
        doSuccessRequestResponse(builder, Protocol.H1_TLS);

        assertThat("EarlyConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for EarlyConnectionAcceptor", offloaded.get(), is(offload));
    }

    @ParameterizedTest(name = "{displayName} [{index}] offload={0}")
    @ValueSource(booleans = {false, true})
    void testSingleLateAcceptorOffloading(boolean offload) throws Exception {
        AtomicReference<Boolean> offloaded = new AtomicReference<>();

        HttpServerBuilder builder = serverBuilder().appendLateConnectionAcceptor(new LateConnectionAcceptor() {
            @Override
            public Completable accept(final ConnectionInfo info) {
                throw new UnsupportedOperationException("Not expected to be invoked");
            }

            @Override
            public Completable accept(final ConnectionContext ctx) {
                assertNotNull(ctx);
                assertNotNull(ctx.sslConfig());
                assertNotNull(ctx.sslSession());
                offloaded.set(!IoThreadFactory.IoThread.currentThreadIsIoThread());
                return Completable.completed();
            }

            @Override
            public ConnectExecutionStrategy requiredOffloads() {
                return offload ? ConnectExecutionStrategy.offloadAll() : ConnectExecutionStrategy.offloadNone();
            }
        });
        doSuccessRequestResponse(builder, Protocol.H1_TLS);

        assertThat("LateConnectionAcceptor was not invoked", offloaded.get(), is(notNullValue()));
        assertThat("Incorrect offloading for LateConnectionAcceptor", offloaded.get(), is(offload));
    }

    /**
     * Tests the offload merging and makes sure that if at least one is offloaded, both are.
     */
    @ParameterizedTest(name = "{displayName} [{index}] firstOffloaded={0} secondOffloaded={1} thirdOffloaded={2}")
    @CsvSource({"true,false,false", "false,true,false", "false,false,true"})
    void testMultipleAcceptorsOffloading(boolean firstOffloaded, boolean secondOffloaded, boolean thirdOffloaded)
            throws Exception {
        final AtomicInteger earlyOffloaded = new AtomicInteger();
        final AtomicInteger lateOffloaded = new AtomicInteger();
        final Queue<Integer> executionOrder = new ArrayBlockingQueue<>(6);

        HttpServerBuilder builder = serverBuilder()
                .appendEarlyConnectionAcceptor(earlyAcceptor(firstOffloaded, earlyOffloaded, executionOrder, 1))
                .appendEarlyConnectionAcceptor(earlyAcceptor(secondOffloaded, earlyOffloaded, executionOrder, 2))
                .appendEarlyConnectionAcceptor(earlyAcceptor(thirdOffloaded, earlyOffloaded, executionOrder, 3))
                .appendLateConnectionAcceptor(lateAcceptor(firstOffloaded, lateOffloaded, executionOrder, 4))
                .appendLateConnectionAcceptor(lateAcceptor(secondOffloaded, lateOffloaded, executionOrder, 5))
                .appendLateConnectionAcceptor(lateAcceptor(thirdOffloaded, lateOffloaded, executionOrder, 6));

        doSuccessRequestResponse(builder, Protocol.H1);

        assertEquals(3, earlyOffloaded.get());
        assertEquals(3, lateOffloaded.get());
        assertArrayEquals(new Integer[] {1, 2, 3, 4, 5, 6}, executionOrder.toArray(new Integer[0]));
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(Protocol.class)
    void testEarlyAcceptorWithOffloadingAndDifferentProtocols(Protocol protocol) throws Exception {
        final AtomicInteger earlyOffloaded = new AtomicInteger();

        HttpServerBuilder builder = serverBuilder()
                .appendEarlyConnectionAcceptor(info -> {
                    if (!IoThreadFactory.IoThread.currentThreadIsIoThread()) {
                        earlyOffloaded.incrementAndGet();
                    }
                    return info.executionContext().executor().timer(ofMillis(50));
                });
        doSuccessRequestResponse(builder, protocol);
        assertEquals(1, earlyOffloaded.get());
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
                    throw new UnsupportedOperationException("Not expected to be invoked");
                }

                @Override
                public Completable accept(final ConnectionContext ctx) {
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
                    throw new UnsupportedOperationException("Not expected to be invoked");
                }

                @Override
                public Completable accept(final ConnectionContext ctx) {
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

    private enum Protocol {
        H1,
        H1_TLS,
        H2,
        H2_TLS,
        H2_ALPN
    }

    private static void doSuccessRequestResponse(final HttpServerBuilder serverBuilder, final Protocol protocol)
            throws Exception {

        ServerSslConfig serverSslConfig = new ServerSslConfigBuilder(
                DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build();

        if (protocol == Protocol.H1) {
            serverBuilder.protocols(h1Default());
        } else if (protocol == Protocol.H1_TLS) {
            serverBuilder.protocols(h1Default()).sslConfig(serverSslConfig);
        } else if (protocol == Protocol.H2) {
            serverBuilder.protocols(h2Default());
        } else if (protocol == Protocol.H2_TLS) {
            serverBuilder.protocols(h2Default()).sslConfig(serverSslConfig);
        } else if (protocol == Protocol.H2_ALPN) {
            serverBuilder.protocols(h2Default(), h1Default()).sslConfig(serverSslConfig);
        }

        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = serverBuilder.listenAndAwait(service)) {
            ClientSslConfig clientSslConfig = new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname()).build();
            final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                    HttpClients.forSingleAddress(serverHostAndPort(server));
            if (protocol == Protocol.H1) {
                clientBuilder.protocols(h1Default());
            } else if (protocol == Protocol.H1_TLS) {
                clientBuilder.protocols(h1Default()).sslConfig(clientSslConfig);
            } else if (protocol == Protocol.H2) {
                clientBuilder.protocols(h2Default());
            } else if (protocol == Protocol.H2_TLS) {
                clientBuilder.protocols(h2Default()).sslConfig(clientSslConfig);
            } else if (protocol == Protocol.H2_ALPN) {
                clientBuilder.protocols(h2Default(), h1Default()).sslConfig(clientSslConfig);
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
        HttpServerBuilder builder = serverBuilder()
                .appendEarlyConnectionAcceptor(info -> Completable.failed(DELIBERATE_EXCEPTION));

        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = builder.listenAndAwait(service);
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server)).buildBlocking()) {
            assertThrows(IOException.class, () -> client.request(client.get("/sayHello")));
        }
    }

    /**
     * Verifies that the {@link io.servicetalk.transport.api.LateConnectionAcceptor} can reject incoming connections.
     */
    @Test
    void lateConnectionAcceptorCanReject() throws Exception {
        HttpServerBuilder builder = serverBuilder()
                .appendLateConnectionAcceptor(info -> Completable.failed(DELIBERATE_EXCEPTION));

        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        try (ServerContext server = builder.listenAndAwait(service);
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server)).buildBlocking()) {
            assertThrows(IOException.class, () -> client.request(client.get("/sayHello")));
        }
    }

    private static HttpServerBuilder serverBuilder() {
        return HttpServers.forAddress(localAddress(0));
    }
}
