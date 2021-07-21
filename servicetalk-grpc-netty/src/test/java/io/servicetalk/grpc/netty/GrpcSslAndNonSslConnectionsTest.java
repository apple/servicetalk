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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.DEFAULT_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcSslAndNonSslConnectionsTest {

    private static final TesterProto.TestRequest REQUEST = TesterProto.TestRequest.newBuilder().setName("test").build();

    private ServerContext nonSecureGrpcServer() throws Exception {
        return GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(serviceFactory());
    }

    private ServerContext secureGrpcServer()
            throws Exception {
        return GrpcServers.forAddress(localAddress(0))
                .sslConfig(
                        trustedServerConfig()
                )
                .listenAndAwait(serviceFactory());
    }

    private GrpcClientBuilder<HostAndPort, InetSocketAddress> secureGrpcClient(
            final ServerContext serverContext, final ClientSslConfigBuilder sslConfigBuilder) {
        return GrpcClients.forAddress(serverHostAndPort(serverContext)).sslConfig(sslConfigBuilder.build());
    }

    private BlockingTesterClient nonSecureGrpcClient(ServerContext serverContext) {
        return GrpcClients.forAddress(serverHostAndPort(serverContext))
                .buildBlocking(clientFactory());
    }

    private TesterProto.Tester.ClientFactory clientFactory() {
        return new TesterProto.Tester.ClientFactory();
    }

    private TesterProto.Tester.ServiceFactory serviceFactory() {
        return new TesterProto.Tester.ServiceFactory.Builder()
                .test(DEFAULT_STRATEGY_ASYNC_SERVICE)
                .build();
    }

    private static ServerSslConfig untrustedServerConfig() {
        // Need a key that won't be trusted by the client, just use the client's key.
        return new ServerSslConfigBuilder(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey).build();
    }

    private ServerSslConfig trustedServerConfig() {
        return new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).build();
    }

    @Test
    void connectingToSecureServerWithSecureClient() throws Exception {
        try (ServerContext serverContext = secureGrpcServer();
             BlockingTesterClient client = secureGrpcClient(serverContext,
                     new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()))
                     .buildBlocking(clientFactory())) {
            final TesterProto.TestResponse response = client.test(REQUEST);
            assertThat(response, is(notNullValue()));
            assertThat(response.getMessage(), is(notNullValue()));
        }
    }

    @Test
    void secureClientToNonSecureServerClosesConnection() throws Exception {
        try (ServerContext serverContext = nonSecureGrpcServer();
             BlockingTesterClient client = secureGrpcClient(serverContext,
                     new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()))
                     .buildBlocking(clientFactory())) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> client.test(REQUEST));
            assertThat(e.getCause(), instanceOf(SSLHandshakeException.class));
        }
    }

    @Test
    void nonSecureClientToSecureServerClosesConnection() throws Exception {
        try (ServerContext serverContext = secureGrpcServer();
             BlockingTesterClient client = nonSecureGrpcClient(serverContext)) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> client.test(REQUEST));
            assertThat(e.getCause(), instanceOf(StacklessClosedChannelException.class));
        }
    }

    @Test
    void secureClientToSecureServerWithoutPeerHostSucceeds() throws Exception {
        try (ServerContext serverContext = secureGrpcServer();
             BlockingTesterClient client = secureGrpcClient(serverContext,
                     new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(null)
                             // if verification is not disabled, identity check fails against the undefined address
                             .hostnameVerificationAlgorithm(""))
                     .inferPeerHost(false)
                     .buildBlocking(clientFactory())) {
            final TesterProto.TestResponse response = client.test(REQUEST);
            assertThat(response, is(notNullValue()));
            assertThat(response.getMessage(), is(notNullValue()));
        }
    }

    @Test
    void noSniClientDefaultServerFallbackSuccess() throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .sslConfig(
                        trustedServerConfig(),
                        singletonMap(getLoopbackAddress().getHostName(), untrustedServerConfig())
                )
                .listenAndAwait(serviceFactory());
             BlockingTesterClient client = GrpcClients.forAddress(
                     getLoopbackAddress().getHostName(), serverHostAndPort(serverContext).port())
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()).build())
                     .inferSniHostname(false)
                     .buildBlocking(clientFactory());
        ) {
            final TesterProto.TestResponse response = client.test(REQUEST);
            assertThat(response, is(notNullValue()));
            assertThat(response.getMessage(), is(notNullValue()));
        }
    }

    @Test
    void noSniClientDefaultServerFallbackFailExpected() throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .sslConfig(
                        untrustedServerConfig(),
                        singletonMap(getLoopbackAddress().getHostName(), trustedServerConfig())
                )
                .listenAndAwait(serviceFactory());
             BlockingTesterClient client = GrpcClients.forAddress(
                     getLoopbackAddress().getHostName(), serverHostAndPort(serverContext).port())
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()).build())
                     .inferSniHostname(false)
                     .buildBlocking(clientFactory());
        ) {
            GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> client.test(REQUEST));
            assertThat(e.getCause(), instanceOf(SSLHandshakeException.class));
        }
    }
}
