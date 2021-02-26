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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Function;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpClients.forSingleAddressViaProxy;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityHandshakeObserverTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached("server-io", "server-executor");
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor");



    private final TransportObserver clientTransportObserver;
    private final ConnectionObserver clientConnectionObserver;
    private final SecurityHandshakeObserver clientSecurityHandshakeObserver;

    private final TransportObserver serverTransportObserver;
    private final ConnectionObserver serverConnectionObserver;
    private final SecurityHandshakeObserver serverSecurityHandshakeObserver;

    SecurityHandshakeObserverTest() {
        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "clientSecurityHandshakeObserver");
        DataObserver clientDataObserver = mock(DataObserver.class, "clientDataObserver");
        MultiplexedObserver clientMultiplexedObserver = mock(MultiplexedObserver.class, "clientMultiplexedObserver");
        StreamObserver clientStreamObserver = mock(StreamObserver.class, "clientStreamObserver");
        ReadObserver clientReadObserver = mock(ReadObserver.class, "clientReadObserver");
        WriteObserver clientWriteObserver = mock(WriteObserver.class, "clientWriteObserver");
        when(clientTransportObserver.onNewConnection()).thenReturn(clientConnectionObserver);
        when(clientConnectionObserver.onSecurityHandshake()).thenReturn(clientSecurityHandshakeObserver);
        when(clientConnectionObserver.connectionEstablished(any(ConnectionInfo.class))).thenReturn(clientDataObserver);
        when(clientConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
            .thenReturn(clientMultiplexedObserver);
        when(clientMultiplexedObserver.onNewStream()).thenReturn(clientStreamObserver);
        when(clientStreamObserver.streamEstablished()).thenReturn(clientDataObserver);
        when(clientDataObserver.onNewRead()).thenReturn(clientReadObserver);
        when(clientDataObserver.onNewWrite()).thenReturn(clientWriteObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "serverSecurityHandshakeObserver");
        DataObserver serverDataObserver = mock(DataObserver.class, "serverDataObserver");
        MultiplexedObserver serverMultiplexedObserver = mock(MultiplexedObserver.class, "serverMultiplexedObserver");
        StreamObserver serverStreamObserver = mock(StreamObserver.class, "serverStreamObserver");
        ReadObserver serverReadObserver = mock(ReadObserver.class, "serverReadObserver");
        WriteObserver serverWriteObserver = mock(WriteObserver.class, "serverWriteObserver");
        when(serverTransportObserver.onNewConnection()).thenReturn(serverConnectionObserver);
        when(serverConnectionObserver.onSecurityHandshake()).thenReturn(serverSecurityHandshakeObserver);
        when(serverConnectionObserver.connectionEstablished(any(ConnectionInfo.class))).thenReturn(serverDataObserver);
        when(serverConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
            .thenReturn(serverMultiplexedObserver);
        when(serverMultiplexedObserver.onNewStream()).thenReturn(serverStreamObserver);
        when(serverStreamObserver.streamEstablished()).thenReturn(serverDataObserver);
        when(serverDataObserver.onNewRead()).thenReturn(serverReadObserver);
        when(serverDataObserver.onNewWrite()).thenReturn(serverWriteObserver);
    }

    @Test
    void withH1() throws Exception {
        verifyHandshakeObserved(address -> forAddress(address).protocols(h1Default()),
                                address -> forSingleAddress(address).protocols(h1Default()));
    }

    @Test
    void withH2() throws Exception {
        verifyHandshakeObserved(address -> forAddress(address).protocols(h2Default()),
                                address -> forSingleAddress(address).protocols(h2Default()));
    }

    @Test
    void withAlpnPreferH1() throws Exception {
        verifyHandshakeObserved(address -> forAddress(address).protocols(h1Default(), h2Default()),
                                address -> forSingleAddress(address).protocols(h1Default(), h2Default()));
    }

    @Test
    void withAlpnPreferH2() throws Exception {
        verifyHandshakeObserved(address -> forAddress(address).protocols(h2Default(), h1Default()),
                                address -> forSingleAddress(address).protocols(h2Default(), h1Default()));
    }

    @Test
    void withProxyTunnel() throws Exception {
        try (ProxyTunnel proxyTunnel = new ProxyTunnel()) {
            HostAndPort proxyAddress = proxyTunnel.startProxy();
            verifyHandshakeObserved(HttpServers::forAddress,
                                    address -> forSingleAddressViaProxy(address, proxyAddress));
        }
    }

    private void verifyHandshakeObserved(Function<SocketAddress, HttpServerBuilder> serverBuilderFactory,
                                         Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort,
                                             InetSocketAddress>> clientBuilderFactory) throws Exception {

        try (ServerContext serverContext = serverBuilderFactory.apply(localAddress(0))
            .ioExecutor(SERVER_CTX.ioExecutor())
            .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
            .sslConfig(new ServerSslConfigBuilder(
                        DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build())
            .transportObserver(serverTransportObserver)
            .listenStreamingAndAwait(new TestServiceStreaming());

             BlockingHttpClient client = clientBuilderFactory.apply(serverHostAndPort(serverContext))
                 .ioExecutor(CLIENT_CTX.ioExecutor())
                 .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
                 .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()).build())
                 .appendConnectionFactoryFilter(
                     new TransportObserverConnectionFactoryFilter<>(clientTransportObserver))
                 .buildBlocking()) {

            String content = "payload_body";
            HttpResponse response = client.request(client.post(SVC_ECHO).payloadBody(content, textSerializerUtf8()));
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody(textSerializerUtf8()), equalTo(content));

            verify(clientConnectionObserver).onSecurityHandshake();
            verify(clientSecurityHandshakeObserver).handshakeComplete(any(SSLSession.class));

            verify(serverConnectionObserver).onSecurityHandshake();
            verify(serverSecurityHandshakeObserver).handshakeComplete(any(SSLSession.class));
        }
    }
}
