/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.netty.InternalServiceDiscoverers.mappingServiceDiscoverer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class DefaultSingleAddressHttpClientBuilderTest {
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
