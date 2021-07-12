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
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetAddress;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {
    private static final String SNI_HOSTNAME = "servicetalk.io";

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void sniSuccess(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(untrustedServerConfig(alpnIds(useH2, useALPN)),
                        singletonMap(SNI_HOSTNAME, trustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext, useH2, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void sniDefaultFallbackSuccess(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(trustedServerConfig(alpnIds(useH2, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME, untrustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext, useH2, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void sniFailExpected(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(trustedServerConfig(alpnIds(useH2, useALPN)),
                        singletonMap(SNI_HOSTNAME, untrustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext, useH2, useALPN)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void sniDefaultFallbackFailExpected(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(untrustedServerConfig(alpnIds(useH2, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME, trustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext, useH2, useALPN)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void sniClientDefaultServerSuccess(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(trustedServerConfig(alpnIds(useH2, useALPN)))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext, useH2, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void noSniClientDefaultServerFallbackSuccess(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(trustedServerConfig(alpnIds(useH2, useALPN)),
                        singletonMap("localhost", untrustedServerConfig()))
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        ctx.sslSession() != null ? responseFactory.ok() : responseFactory.internalServerError());
             BlockingHttpClient client = HttpClients.forSingleAddress(
                     InetAddress.getLoopbackAddress().getHostName(),
                     serverHostAndPort(serverContext).port())
                     .protocols(protocolConfigs(useH2))
                     .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()), useH2, useALPN).build())
                     .inferSniHostname(false)
                     .buildBlocking()) {
            HttpRequest request = client.get("/");
            ReservedBlockingHttpConnection reserved = client.reserveConnection(request);
            assertNotNull(reserved.connectionContext().sslSession());
            assertEquals(HttpResponseStatus.OK, reserved.request(request).status());
            reserved.release();
        }
    }

    @ParameterizedTest(name = "h2={0}, alpn={1}")
    @MethodSource("useH2UseAlpn")
    void noSniClientDefaultServerFallbackFailExpected(boolean useH2, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(useH2))
                .sslConfig(
                        untrustedServerConfig(alpnIds(useH2, useALPN)),
                        singletonMap(InetAddress.getLoopbackAddress().getHostName(), trustedServerConfig())
                )
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forSingleAddress(
                     InetAddress.getLoopbackAddress().getHostName(),
                     serverHostAndPort(serverContext).port()
             )
                     .protocols(protocolConfigs(useH2))
                     .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem),
                             useH2, useALPN).build())
                     .inferSniHostname(false)
                     .buildBlocking()) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> useH2UseAlpn() {
        return Stream.of(Arguments.of(false, false),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(true, true)
        );
    }

    private static BlockingHttpClient newClient(ServerContext serverContext,
                                                boolean useH2, boolean useALPN) {
        return HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(protocolConfigs(useH2))
                .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .sniHostname(SNI_HOSTNAME).peerHost(serverPemHostname()), useH2, useALPN).build())
                .buildBlocking();
    }

    private static ClientSslConfigBuilder configureAlpn(ClientSslConfigBuilder builder, boolean useH2,
                                                        boolean useALPN) {
        if (useALPN) {
            builder.alpnProtocols(alpnIds(useH2, true));
        }
        return builder;
    }

    private static ServerSslConfig untrustedServerConfig() {
        return untrustedServerConfig((String[]) null);
    }

    private static ServerSslConfig untrustedServerConfig(@Nullable String... alpn) {
        // Need a key that won't be trusted by the client, just use the client's key.
        ServerSslConfigBuilder builder = new ServerSslConfigBuilder(DefaultTestCerts::loadClientPem,
                DefaultTestCerts::loadClientKey);
        if (alpn != null) {
            builder.alpnProtocols(alpn);
        }
        return builder.build();
    }

    private static ServerSslConfig trustedServerConfig() {
        return trustedServerConfig((String[]) null);
    }

    @Nullable
    private static String[] alpnIds(boolean useH2, boolean useALPN) {
        return useALPN ? (useH2 ? new String[] {AlpnIds.HTTP_2} : new String[] {AlpnIds.HTTP_1_1}) : null;
    }

    private static ServerSslConfig trustedServerConfig(@Nullable String... alpn) {
        ServerSslConfigBuilder builder = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey);
        if (alpn != null) {
            builder.alpnProtocols(alpn);
        }
        return builder.build();
    }

    private static HttpProtocolConfig[] protocolConfigs(boolean useH2) {
        return useH2 ? new HttpProtocolConfig[] {h2Default()} : new HttpProtocolConfig[] {h1Default()};
    }
}
