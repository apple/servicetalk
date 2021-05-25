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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.function.Function;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {
    private static final String SNI_HOSTNAME = "servicetalk.io";

    @Test
    void sniSuccess() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(untrustedServerConfig(), singletonMap(SNI_HOSTNAME, trustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @Test
    void sniDefaultFallbackSuccess() throws Exception {
        sniDefaultFallbackSuccess(SniTest::newClient);
    }

    private static void sniDefaultFallbackSuccess(Function<ServerContext, BlockingHttpClient> clientFunc)
            throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(trustedServerConfig(), singletonMap("no_match" + SNI_HOSTNAME, untrustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = clientFunc.apply(serverContext)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @Test
    void sniFailExpected() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(trustedServerConfig(), singletonMap(SNI_HOSTNAME, untrustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @Test
    void sniDefaultFallbackFailExpected() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(untrustedServerConfig(), singletonMap("no_match" + SNI_HOSTNAME, trustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @Test
    void sniClientDefaultServerSuccess() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(trustedServerConfig())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @Test
    void noSniClientDefaultServerFallbackSuccess() throws Exception {
        sniDefaultFallbackSuccess(serverContext -> HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .disableSni()
                .buildBlocking());
    }

    @Test
    void noSniClientDefaultServerFallbackFailExpected() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(
                        untrustedServerConfig(),
                        singletonMap(InetAddress.getLoopbackAddress().getHostName(), trustedServerConfig())
                )
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forSingleAddress(
                     HostAndPort.of(
                             InetAddress.getLoopbackAddress().getHostName(),
                             serverHostAndPort(serverContext).port())
             )
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build())
                     .disableSni()
                     .buildBlocking()) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    private static BlockingHttpClient newClient(ServerContext serverContext) {
        return HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .sniHostname(SNI_HOSTNAME).peerHost(serverPemHostname()).build())
                .buildBlocking();
    }

    private static ServerSslConfig untrustedServerConfig() {
        // Need a key that won't be trusted by the client, just use the client's key.
        return new ServerSslConfigBuilder(DefaultTestCerts::loadClientPem,
                DefaultTestCerts::loadClientKey).build();
    }

    private static ServerSslConfig trustedServerConfig() {
        return new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey).build();
    }
}
