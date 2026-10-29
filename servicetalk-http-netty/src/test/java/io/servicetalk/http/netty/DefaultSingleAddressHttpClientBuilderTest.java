/*
 * Copyright © 2021-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.InternalServiceDiscoverers.mappingServiceDiscoverer;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class DefaultSingleAddressHttpClientBuilderTest {

    @ParameterizedTest(name = "{displayName} [{index}]: ssl={0}, protocol={1}, alpn={2}")
    @CsvSource({"false, HTTP_1, false", "false, HTTP_2, false", "true, HTTP_1, false", "true, HTTP_2, false",
            "true, HTTP_1, true", "true, HTTP_2, true"})
    void newRequestUsesConfiguredScheme(boolean ssl, HttpProtocol protocol, boolean alpn) throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0)).protocols(protocol.config);
        if (ssl) {
            serverBuilder.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                    DefaultTestCerts::loadServerKey).build());
        }
        try (ServerContext server = serverBuilder.listenBlockingAndAwait((ctx, request, responseFactory) ->
                responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator()
                        .fromAscii(request.requestTarget())))) {
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder =
                    HttpClients.forSingleAddress(serverHostAndPort(server)).protocols(alpn ?
                            new HttpProtocolConfig[]{HTTP_2.config, HTTP_1.config} :
                            new HttpProtocolConfig[]{protocol.config});
            if (ssl) {
                clientBuilder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build());
            }
            try (BlockingHttpClient client = clientBuilder.buildBlocking()) {
                String scheme = ssl ? "https" : "http";
                String target = "/hello?foo=bar";
                HttpRequest request = client.get(target);
                assertThat(request.scheme(), is(scheme));
                assertThat(request.requestTarget(), is(target));
                assertThat(request.toStreamingRequest().scheme(), is(scheme));
                assertThat(request.toBlockingStreamingRequest().scheme(), is(scheme));
                assertThat(client.asStreamingClient().get(target).scheme(), is(scheme));
                assertThat(client.asClient().get(target).scheme(), is(scheme));
                assertThat(client.asBlockingStreamingClient().get(target).scheme(), is(scheme));
                assertThat(client.request(request).payloadBody().toString(US_ASCII), is(target));

                ReservedBlockingHttpConnection connection = client.reserveConnection(client.get(target));
                try {
                    HttpRequest reservedRequest = connection.get(target);
                    assertThat(reservedRequest.scheme(), is(scheme));
                    assertThat(connection.request(reservedRequest).payloadBody().toString(US_ASCII), is(target));
                } finally {
                    connection.release();
                }
            }
        }
    }

    @Test
    void hostToCharSequenceFunctionIPv4() throws Exception {
        hostToCharSequenceFunction("", "1.2.3.4", ":", 9999);
    }

    @Test
    void hostToCharSequenceFunctionIPv6() throws Exception {
        hostToCharSequenceFunction("[", "1:2:3::5%80", "]:", 1);
    }

    @Test
    void hostToCharSequenceFunctionIPv4NoPort() {
        assertThrows(NumberFormatException.class, () -> hostToCharSequenceFunction("", "1.2.3.4", ":", null));
    }

    @Test
    void hostToCharSequenceFunctionIPv6NoPort() {
        assertThrows(IllegalArgumentException.class, () -> hostToCharSequenceFunction("[", "1:2:3::5%80", ":", null));
    }

    @Test
    void hostToCharSequenceFunctionIPv6MissingEndBracket() {
        assertThrows(IllegalArgumentException.class, () -> hostToCharSequenceFunction("[", "1:2:3::5%80", ":", 1));
    }

    @Test
    void hostToCharSequenceFunctionIPv6MissingStartBracket() {
        assertThrows(NumberFormatException.class, () -> hostToCharSequenceFunction("", "1:2:3::5%80", "]:", 1));
    }

    @Test
    void hostToCharSequenceFunctionIPv4InvalidPort() {
        assertThrows(NumberFormatException.class, () -> hostToCharSequenceFunction("", "1.2.3.4", ":s", 999));
    }

    @Test
    void hostToCharSequenceFunctionIPv6InvalidPort() {
        assertThrows(NumberFormatException.class, () -> hostToCharSequenceFunction("[", "1:2:3::5%80]", ":s", 1));
    }

    private static final class TestAddress {
        @Override
        public String toString() {
            return "foo:bar";
        }
    }

    @Test
    void oddFormattedToString() {
        new DefaultSingleAddressHttpClientBuilder<>(
             new TestAddress(),
            mappingServiceDiscoverer(u -> null, "description"))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build());
    }

    private static void hostToCharSequenceFunction(String hostNamePrefix, String hostName, String hostNameSuffix,
                                                   @Nullable Integer port) throws Exception {
        try (ServerContext serverCtx = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client =
                     new DefaultSingleAddressHttpClientBuilder<>(
                             hostNamePrefix + hostName + hostNameSuffix + (port == null ? "" : port),
                             mappingServiceDiscoverer(u -> serverCtx.listenAddress(),
                                     "from " + String.class.getSimpleName() + " to a resolved " +
                                             SocketAddress.class.getSimpleName()))
                             .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                     .hostnameVerificationAlgorithm("").build())
                             .buildBlocking()) {
            ReservedBlockingHttpConnection conn = client.reserveConnection(client.get("/"));
            try {
                SSLSession sslSession = conn.connectionContext().sslSession();
                assertNotNull(sslSession);
                assertThat(sslSession.getPeerHost(), startsWith(hostName));
                InetSocketAddress socketAddress = (InetSocketAddress) conn.connectionContext().remoteAddress();
                assertEquals(socketAddress.getPort(), sslSession.getPeerPort());
            } finally {
                conn.release();
            }
        }
    }
}
