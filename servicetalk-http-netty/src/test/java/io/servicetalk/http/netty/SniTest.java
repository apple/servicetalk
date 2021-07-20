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
import io.servicetalk.http.api.BlockingHttpService;
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

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {
    private static final String SNI_HOSTNAME = "servicetalk.io";

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void sniSuccess(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(untrustedServerConfig(alpnIds(protocols, useALPN)),
                        singletonMap(SNI_HOSTNAME, trustedServerConfig(alpnIds(protocols, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, protocols, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void sniDefaultFallbackSuccess(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(trustedServerConfig(alpnIds(protocols, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME, untrustedServerConfig()))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, protocols, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void sniFailExpected(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(trustedServerConfig(alpnIds(protocols, useALPN)),
                        singletonMap(SNI_HOSTNAME, untrustedServerConfig(alpnIds(protocols, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, protocols, useALPN)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void sniDefaultFallbackFailExpected(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(untrustedServerConfig(alpnIds(protocols, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME, trustedServerConfig(alpnIds(protocols, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, protocols, useALPN)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void sniClientDefaultServerSuccess(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(trustedServerConfig(alpnIds(protocols, useALPN)))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, protocols, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void noSniClientDefaultServerFallbackSuccess(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(trustedServerConfig(alpnIds(protocols, useALPN)),
                        singletonMap("localhost", untrustedServerConfig()))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = HttpClients.forSingleAddress(
                     getLoopbackAddress().getHostName(),
                     serverHostAndPort(serverContext).port())
                     .protocols(protocolConfigs(protocols))
                     .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()), protocols, useALPN).build())
                     .inferSniHostname(false)
                     .buildBlocking()) {
            HttpRequest request = client.get("/");
            ReservedBlockingHttpConnection reserved = client.reserveConnection(request);
            assertNotNull(reserved.connectionContext().sslSession());
            assertEquals(HttpResponseStatus.OK, reserved.request(request).status());
            reserved.release();
        }
    }

    @ParameterizedTest(name = "protocols={0}, alpn={1}")
    @MethodSource("protocolsAndAlpn")
    void noSniClientDefaultServerFallbackFailExpected(List<HttpProtocol> protocols, boolean useALPN) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfigs(protocols))
                .sslConfig(untrustedServerConfig(alpnIds(protocols, useALPN)),
                        singletonMap(getLoopbackAddress().getHostName(),
                                trustedServerConfig(alpnIds(protocols, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = HttpClients.forSingleAddress(getLoopbackAddress().getHostName(),
                        serverHostAndPort(serverContext).port())
                     .protocols(protocolConfigs(protocols))
                     .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem),
                             protocols, useALPN).build())
                     .inferSniHostname(false)
                     .buildBlocking()) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> protocolsAndAlpn() {
        return Stream.of(Arguments.of(singletonList(HTTP_2), false),
                Arguments.of(singletonList(HTTP_2), true),
                Arguments.of(singletonList(HTTP_1), false),
                Arguments.of(singletonList(HTTP_1), true)
        );
    }

    private static BlockingHttpClient newClient(ServerContext serverContext,
                                                List<HttpProtocol> protocols, boolean useALPN) {
        return HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(protocolConfigs(protocols))
                .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .sniHostname(SNI_HOSTNAME).peerHost(serverPemHostname()), protocols, useALPN).build())
                .buildBlocking();
    }

    private static ClientSslConfigBuilder configureAlpn(ClientSslConfigBuilder builder, List<HttpProtocol> protocols,
                                                        boolean useALPN) {
        if (useALPN) {
            builder.alpnProtocols(alpnIds(protocols));
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

    @Nullable
    private static String[] alpnIds(List<HttpProtocol> protocols, boolean useALPN) {
        return useALPN ? alpnIds(protocols) : null;
    }

    private static String[] alpnIds(List<HttpProtocol> protocols) {
        String[] ids = new String[protocols.size()];
        for (int i = 0; i < ids.length; ++i) {
            ids[i] = protocols.get(i).config.alpnId();
        }
        return ids;
    }

    private static ServerSslConfig trustedServerConfig(@Nullable String... alpn) {
        ServerSslConfigBuilder builder = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey);
        if (alpn != null) {
            builder.alpnProtocols(alpn);
        }
        return builder.build();
    }

    private static HttpProtocolConfig[] protocolConfigs(List<HttpProtocol> protocols) {
        HttpProtocolConfig[] configs = new HttpProtocolConfig[protocols.size()];
        for (int i = 0; i < configs.length; ++i) {
            configs[i] = protocols.get(i).config;
        }
        return configs;
    }

    private static BlockingHttpService newSslVerifyService() {
        return (ctx, request, responseFactory) ->
                ctx.sslSession() != null ? responseFactory.ok() : responseFactory.internalServerError();
    }
}
