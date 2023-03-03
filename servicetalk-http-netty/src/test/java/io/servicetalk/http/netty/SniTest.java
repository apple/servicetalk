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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.http.netty.MutualSslTest.SSL_PROVIDERS;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String SNI_HOSTNAME = serverPemHostname();

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void sniSuccess(SslProvider serverSslProvider, SslProvider clientSslProvider,
                    HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(untrustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)),
                        singletonMap(SNI_HOSTNAME, trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, clientSslProvider, protocol, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void sniDefaultFallbackSuccess(SslProvider serverSslProvider, SslProvider clientSslProvider,
                                   HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME, untrustedServerConfig(serverSslProvider)))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, clientSslProvider, protocol, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void sniFailExpected(SslProvider serverSslProvider, SslProvider clientSslProvider,
                         HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)),
                        singletonMap(SNI_HOSTNAME,
                                untrustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, clientSslProvider, protocol, useALPN)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void sniDefaultFallbackFailExpected(SslProvider serverSslProvider, SslProvider clientSslProvider,
                                        HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(untrustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME,
                                trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, clientSslProvider, protocol, useALPN)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void sniClientDefaultServerSuccess(SslProvider serverSslProvider, SslProvider clientSslProvider,
                                       HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = newClient(serverContext, clientSslProvider, protocol, useALPN)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void noSniClientDefaultServerFallbackSuccess(SslProvider serverSslProvider, SslProvider clientSslProvider,
                                                 HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)),
                        singletonMap("no_match" + SNI_HOSTNAME, untrustedServerConfig(serverSslProvider)))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = HttpClients.forSingleAddress(
                     getLoopbackAddress().getHostName(),
                     serverHostAndPort(serverContext).port())
                     .protocols(protocol.config)
                     .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname()).provider(clientSslProvider), protocol, useALPN).build())
                     .inferSniHostname(false)
                     .buildBlocking()) {
            HttpRequest request = client.get("/");
            ReservedBlockingHttpConnection reserved = client.reserveConnection(request);
            assertNotNull(reserved.connectionContext().sslSession());
            assertEquals(HttpResponseStatus.OK, reserved.request(request).status());
            reserved.release();
        }
    }

    @ParameterizedTest(name = "serverSslProvider={0} clientSslProvider={1} protocol={2} useALPN={3}")
    @MethodSource("params")
    void noSniClientDefaultServerFallbackFailExpected(SslProvider serverSslProvider, SslProvider clientSslProvider,
                                                      HttpProtocol protocol, boolean useALPN) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(untrustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN)),
                        singletonMap(getLoopbackAddress().getHostName(),
                                trustedServerConfig(serverSslProvider, alpnIds(protocol, useALPN))))
                .listenBlockingAndAwait(newSslVerifyService());
             BlockingHttpClient client = HttpClients.forSingleAddress(getLoopbackAddress().getHostName(),
                        serverHostAndPort(serverContext).port())
                     .protocols(protocol.config)
                     .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                     .provider(clientSslProvider), protocol, useALPN).build())
                     .inferPeerHost(false)
                     .inferSniHostname(false)
                     .buildBlocking()) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    private static Collection<Arguments> params() {
        List<Arguments> params = new ArrayList<>();
        for (SslProvider serverSslProvider : SSL_PROVIDERS) {
            for (SslProvider clientSslProvider : SSL_PROVIDERS) {
                for (HttpProtocol protocol : HttpProtocol.values()) {
                    for (boolean useAlpn : asList(false, true)) {
                        params.add(Arguments.of(serverSslProvider, clientSslProvider, protocol, useAlpn));
                    }
                }
            }
        }
        return params;
    }

    private static BlockingHttpClient newClient(ServerContext serverContext, SslProvider provider,
                                                HttpProtocol protocol, boolean useALPN) {
        return newClientBuilder(serverContext, CLIENT_CTX, protocol)
                .sslConfig(configureAlpn(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .provider(provider)
                        .sniHostname(SNI_HOSTNAME)
                        .peerHost(serverPemHostname()),
                        protocol, useALPN)
                        .build())
                .buildBlocking();
    }

    private static ClientSslConfigBuilder configureAlpn(ClientSslConfigBuilder builder, HttpProtocol protocol,
                                                        boolean useALPN) {
        if (useALPN) {
            builder.alpnProtocols(protocol.config.alpnId());
        }
        return builder;
    }

    private static ServerSslConfig untrustedServerConfig(SslProvider provider) {
        return untrustedServerConfig(provider, (String[]) null);
    }

    private static ServerSslConfig untrustedServerConfig(SslProvider provider, @Nullable String... alpn) {
        // Need a key that won't be trusted by the client, just use the client's key.
        ServerSslConfigBuilder builder = new ServerSslConfigBuilder(DefaultTestCerts::loadClientPem,
                DefaultTestCerts::loadClientKey).provider(provider);
        if (alpn != null) {
            builder.alpnProtocols(alpn);
        }
        return builder.build();
    }

    @Nullable
    private static String[] alpnIds(HttpProtocol protocol, boolean useALPN) {
        return useALPN ? new String[] {protocol.config.alpnId()} : null;
    }

    private static ServerSslConfig trustedServerConfig(SslProvider provider, @Nullable String... alpn) {
        ServerSslConfigBuilder builder = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey).provider(provider);
        if (alpn != null) {
            builder.alpnProtocols(alpn);
        }
        return builder.build();
    }

    private static BlockingHttpService newSslVerifyService() {
        return (ctx, request, responseFactory) -> {
            SSLSession session = ctx.sslSession();
            if (!(session instanceof ExtendedSSLSession)) {
                return responseFactory.internalServerError();
            }
            long matched = ((ExtendedSSLSession) session).getRequestedServerNames().stream()
                    .filter(sni -> sni instanceof SNIHostName)
                    .map(SNIHostName.class::cast)
                    .map(SNIHostName::getAsciiName)
                    .filter(SNI_HOSTNAME::equals)
                    .count();
            return matched == 1 ? responseFactory.ok() : responseFactory.internalServerError();
        };
    }
}
