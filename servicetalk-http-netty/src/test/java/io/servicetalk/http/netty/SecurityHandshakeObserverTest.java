/*
 * Copyright © 2020-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocol.toConfigs;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SecurityHandshakeObserverTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached("server-io", "server-executor")
                .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor")
                .setClassLevel(true);

    private final TransportObserver clientTransportObserver;
    private final ConnectionObserver clientConnectionObserver;
    private final SecurityHandshakeObserver clientSecurityHandshakeObserver;
    private final InOrder clientOrder;

    private final TransportObserver serverTransportObserver;
    private final ConnectionObserver serverConnectionObserver;
    private final SecurityHandshakeObserver serverSecurityHandshakeObserver;
    private final InOrder serverOrder;

    private final CountDownLatch bothHandshakeFinished = new CountDownLatch(2);
    private final CountDownLatch bothClosed = new CountDownLatch(2);

    SecurityHandshakeObserverTest() {
        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "clientSecurityHandshakeObserver");
        when(clientTransportObserver.onNewConnection(any(), any())).thenReturn(clientConnectionObserver);
        when(clientConnectionObserver.onSecurityHandshake(any(SslConfig.class)))
                .thenReturn(clientSecurityHandshakeObserver);
        countDownOnClosed(clientConnectionObserver, bothClosed);
        countDownOnHandshakeTermination(clientSecurityHandshakeObserver, bothHandshakeFinished);
        clientOrder = inOrder(clientTransportObserver, clientConnectionObserver, clientSecurityHandshakeObserver);

        serverTransportObserver = mock(TransportObserver.class, "serverTransportObserver");
        serverConnectionObserver = mock(ConnectionObserver.class, "serverConnectionObserver");
        serverSecurityHandshakeObserver = mock(SecurityHandshakeObserver.class, "serverSecurityHandshakeObserver");
        when(serverTransportObserver.onNewConnection(any(), any())).thenReturn(serverConnectionObserver);
        when(serverConnectionObserver.onSecurityHandshake(any(SslConfig.class)))
                .thenReturn(serverSecurityHandshakeObserver);
        countDownOnClosed(serverConnectionObserver, bothClosed);
        countDownOnHandshakeTermination(serverSecurityHandshakeObserver, bothHandshakeFinished);
        serverOrder = inOrder(serverTransportObserver, serverConnectionObserver, serverSecurityHandshakeObserver);
    }

    private static void countDownOnHandshakeTermination(SecurityHandshakeObserver observer, CountDownLatch latch) {
        doAnswer(__ -> {
            latch.countDown();
            return null;
        }).when(observer).handshakeComplete(any(SSLSession.class));
        doAnswer(__ -> {
            latch.countDown();
            return null;
        }).when(observer).handshakeFailed(any(Throwable.class));
    }

    private static void countDownOnClosed(ConnectionObserver observer, CountDownLatch latch) {
        doAnswer(__ -> {
            latch.countDown();
            return null;
        }).when(observer).connectionClosed();
        doAnswer(__ -> {
            latch.countDown();
            return null;
        }).when(observer).connectionClosed(any(Throwable.class));
    }

    private static Stream<Arguments> arguments() {
        List<Arguments> arguments = new ArrayList<>();
        for (List<HttpProtocol> protocol : HttpProtocol.allCombinations()) {
            if (protocol.size() == 1) {
                for (boolean acceptInsecureConnection : asList(true, false)) {
                    arguments.add(Arguments.of(protocol, acceptInsecureConnection));
                }
            } else {
                arguments.add(Arguments.of(protocol, false));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} acceptInsecureConnections={1}")
    @MethodSource("arguments")
    void verifyHandshakeComplete(List<HttpProtocol> protocols, boolean acceptInsecureConnection) throws Exception {
        verifyHandshakeObserved(protocols, false, acceptInsecureConnection);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} acceptInsecureConnections={1}")
    @MethodSource("arguments")
    void verifyHandshakeFailed(List<HttpProtocol> protocols, boolean acceptInsecureConnection) throws Exception {
        verifyHandshakeObserved(protocols, true, acceptInsecureConnection);
    }

    @Test
    void optionalSslWithPlaintextDoesNotTriggerSecurityHandshake() throws Exception {
        try (ServerContext serverContext = BuilderUtils
                .newServerBuilder(SERVER_CTX)
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build(), true)
                .transportObserver(serverTransportObserver)
                .listenStreamingAndAwait(new TestServiceStreaming());
             BlockingHttpClient client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX).buildBlocking()) {
            assertThat(client.request(client.get(SVC_ECHO)).status(), is(OK));
        }

        verify(serverConnectionObserver, never()).onSecurityHandshake(any(SslConfig.class));
        verifyNoMoreInteractions(serverSecurityHandshakeObserver);
    }

    private void verifyHandshakeObserved(List<HttpProtocol> protocols, boolean failHandshake,
                                         boolean acceptInsecureConnection) throws Exception {
        try (ServerContext serverContext = BuilderUtils.newServerBuilder(SERVER_CTX)
            .protocols(toConfigs(protocols))
            .sslConfig(new ServerSslConfigBuilder(
                        DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build(),
                    acceptInsecureConnection)
            .transportObserver(serverTransportObserver)
            .listenStreamingAndAwait(new TestServiceStreaming());

             BlockingHttpClient client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX)
                 .protocols(toConfigs(protocols))
                 .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(failHandshake ? "unknown" : serverPemHostname()).build())
                 .appendConnectionFactoryFilter(
                     new TransportObserverConnectionFactoryFilter<>(clientTransportObserver))
                 .buildBlocking()) {

            if (failHandshake) {
                assertThrows(SSLHandshakeException.class, () -> client.request(client.get(SVC_ECHO)));
            } else {
                assertThat(client.request(client.get(SVC_ECHO)).status(), is(OK));
            }

            bothHandshakeFinished.await();
        }

        bothClosed.await();
        HttpProtocol expectedProtocol = protocols.get(0);
        verifyObservers(clientOrder, clientTransportObserver, clientConnectionObserver,
                clientSecurityHandshakeObserver, expectedProtocol, failHandshake);
        verifyObservers(serverOrder, serverTransportObserver, serverConnectionObserver,
                serverSecurityHandshakeObserver, expectedProtocol, failHandshake);
    }

    private static void verifyObservers(InOrder order, TransportObserver transportObserver,
            ConnectionObserver connectionObserver, SecurityHandshakeObserver securityHandshakeObserver,
            HttpProtocol expectedProtocol, boolean failHandshake) {
        order.verify(transportObserver).onNewConnection(any(), any());
        order.verify(connectionObserver).onTransportHandshakeComplete(any());
        order.verify(connectionObserver).onSecurityHandshake(any(SslConfig.class));
        if (failHandshake) {
            ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
            order.verify(securityHandshakeObserver).handshakeFailed(exceptionCaptor.capture());
            Throwable exception = exceptionCaptor.getValue();
            assertThat(exception, is(anyOf(
                    instanceOf(SSLHandshakeException.class),
                    instanceOf(ClosedChannelException.class))));
            if (exception instanceof ClosedChannelException) {
                assertThat(exception.getSuppressed(), hasItemInArray(instanceOf(SSLHandshakeException.class)));
            }
            order.verify(connectionObserver).connectionClosed(exception);
        } else {
            order.verify(securityHandshakeObserver).handshakeComplete(any(SSLSession.class));
            if (expectedProtocol.version.major() > 1) {
                order.verify(connectionObserver).multiplexedConnectionEstablished(any());
            } else {
                order.verify(connectionObserver).connectionEstablished(any());
            }
        }
        verifyNoMoreInteractions(transportObserver, securityHandshakeObserver);
    }
}
