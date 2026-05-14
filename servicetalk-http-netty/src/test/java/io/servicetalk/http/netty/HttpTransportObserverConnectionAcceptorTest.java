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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that verify {@link ConnectionObserver#connectionEstablished} and
 * {@link ConnectionObserver#multiplexedConnectionEstablished} callbacks fire at the correct time
 * relative to connection acceptors.
 */
class HttpTransportObserverConnectionAcceptorTest {

    /**
     * Protocol configurations covering all distinct server bind paths.
     */
    private enum Protocol {
        /** HTTP/1.1 plain — uses {@link NettyHttpServer#bind}. */
        H1,
        /** HTTP/2 prior-knowledge — uses {@link H2ServerParentConnectionContext#bind}. */
        H2,
        /** HTTP/2 with ALPN — uses {@link DeferredServerChannelBinder#bind}. */
        H2_ALPN
    }

    private TransportObserver svrTransportObserver;
    private ConnectionObserver svrConnectionObserver;
    private DataObserver svrDataObserver;
    private MultiplexedObserver svrMultiplexedObserver;

    private void setUpMocks() {
        svrTransportObserver = mock(TransportObserver.class, "svrTransportObserver");
        svrConnectionObserver = mock(ConnectionObserver.class, "svrConnectionObserver");
        svrDataObserver = mock(DataObserver.class, "svrDataObserver");
        svrMultiplexedObserver = mock(MultiplexedObserver.class, "svrMultiplexedObserver");
        when(svrTransportObserver.onNewConnection(any(), any())).thenReturn(svrConnectionObserver);
        lenient().when(svrConnectionObserver.connectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(svrDataObserver);
        lenient().when(svrConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(svrMultiplexedObserver);
    }

    private HttpServerBuilder configureServer(Protocol protocol) {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .transportObserver(svrTransportObserver);
        switch (protocol) {
            case H1:
                builder.protocols(h1Default());
                break;
            case H2:
                builder.protocols(h2Default());
                break;
            case H2_ALPN:
                ServerSslConfig serverSslConfig = new ServerSslConfigBuilder(
                        DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build();
                builder.protocols(h2Default(), h1Default()).sslConfig(serverSslConfig);
                break;
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return builder;
    }

    private BlockingHttpClient configureClient(Protocol protocol, ServerContext server) {
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                HttpClients.forSingleAddress(serverHostAndPort(server));
        switch (protocol) {
            case H1:
                clientBuilder.protocols(h1Default());
                break;
            case H2:
                clientBuilder.protocols(h2Default());
                break;
            case H2_ALPN:
                ClientSslConfig clientSslConfig = new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build();
                clientBuilder.protocols(h2Default(), h1Default()).sslConfig(clientSslConfig);
                break;
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return clientBuilder.buildBlocking();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(Protocol.class)
    void connectionEstablishedNotCalledWhenAcceptorRejects(Protocol protocol) throws Exception {
        setUpMocks();
        HttpServerBuilder serverBuilder = configureServer(protocol)
                .appendLateConnectionAcceptor(info -> Completable.failed(DELIBERATE_EXCEPTION));

        try (ServerContext server = serverBuilder.listenAndAwait((ctx, req, factory) ->
                succeeded(factory.ok().payloadBody("Hello", textSerializerUtf8())))) {
            try (BlockingHttpClient client = configureClient(protocol, server)) {
                assertThrows(Exception.class, () -> client.request(client.get("/")));
            }
        }

        verify(svrTransportObserver, await()).onNewConnection(any(), any());
        verify(svrConnectionObserver, never()).connectionEstablished(any(ConnectionInfo.class));
        verify(svrConnectionObserver, never()).multiplexedConnectionEstablished(any(ConnectionInfo.class));
        verify(svrConnectionObserver, await()).connectionClosed(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(Protocol.class)
    void connectionEstablishedNotCalledWhenEarlyAcceptorRejects(Protocol protocol) throws Exception {
        setUpMocks();
        HttpServerBuilder serverBuilder = configureServer(protocol)
                .appendEarlyConnectionAcceptor(info -> Completable.failed(DELIBERATE_EXCEPTION));

        try (ServerContext server = serverBuilder.listenAndAwait((ctx, req, factory) ->
                succeeded(factory.ok().payloadBody("Hello", textSerializerUtf8())))) {
            try (BlockingHttpClient client = configureClient(protocol, server)) {
                assertThrows(Exception.class, () -> client.request(client.get("/")));
            }
        }

        verify(svrTransportObserver, await()).onNewConnection(any(), any());
        verify(svrConnectionObserver, never()).connectionEstablished(any(ConnectionInfo.class));
        verify(svrConnectionObserver, never()).multiplexedConnectionEstablished(any(ConnectionInfo.class));
        // Note: connectionClosed is NOT verified here because when the early acceptor rejects,
        // the connectionFunction never executes, so the ConnectionObserverHandler is never added
        // to the pipeline and the observer never receives a connectionClosed callback.
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(Protocol.class)
    void connectionEstablishedAfterLateAcceptor(Protocol protocol) throws Exception {
        final AtomicBoolean lateAcceptorCompleted = new AtomicBoolean(false);
        final AtomicBoolean establishedAfterAcceptor = new AtomicBoolean(false);

        setUpMocks();
        // Override default mock setup to track ordering
        StreamObserver svrStreamObserver = mock(StreamObserver.class, "svrStreamObserver");
        when(svrConnectionObserver.connectionEstablished(any(ConnectionInfo.class)))
                .thenAnswer(invocation -> {
                    establishedAfterAcceptor.set(lateAcceptorCompleted.get());
                    return svrDataObserver;
                });
        when(svrConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
                .thenAnswer(invocation -> {
                    establishedAfterAcceptor.set(lateAcceptorCompleted.get());
                    return svrMultiplexedObserver;
                });
        lenient().when(svrMultiplexedObserver.onNewStream()).thenReturn(svrStreamObserver);
        lenient().when(svrStreamObserver.streamEstablished()).thenReturn(svrDataObserver);
        lenient().when(svrDataObserver.onNewRead()).thenReturn(mock(ReadObserver.class));
        lenient().when(svrDataObserver.onNewWrite()).thenReturn(mock(WriteObserver.class));

        HttpServerBuilder serverBuilder = configureServer(protocol)
                .appendLateConnectionAcceptor(info -> {
                    lateAcceptorCompleted.set(true);
                    return Completable.completed();
                });

        try (ServerContext server = serverBuilder.listenAndAwait((ctx, req, factory) ->
                succeeded(factory.ok().payloadBody("Hello", textSerializerUtf8())))) {
            try (BlockingHttpClient client = configureClient(protocol, server)) {
                assertThat(client.request(client.get("/")).status(), is(OK));
            }
        }

        assertThat("connectionEstablished should fire after late acceptor",
                establishedAfterAcceptor.get(), is(true));
    }

    static VerificationWithTimeout await() {
        return Mockito.timeout(Long.MAX_VALUE);
    }
}
