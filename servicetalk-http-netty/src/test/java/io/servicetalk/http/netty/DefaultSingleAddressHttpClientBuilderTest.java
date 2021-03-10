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
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.Test;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.forResolvedAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DefaultSingleAddressHttpClientBuilderTest {
    @Test
    public void hostToCharSequenceFunctionIPv4() throws Exception {
        hostToCharSequenceFunction("", "1.2.3.4", ":", 9999);
    }

    @Test
    public void hostToCharSequenceFunctionIPv6() throws Exception {
        hostToCharSequenceFunction("[", "1:2:3::5%80", "]:", 1);
    }

    @Test(expected = NumberFormatException.class)
    public void hostToCharSequenceFunctionIPv4NoPort() throws Exception {
        hostToCharSequenceFunction("", "1.2.3.4", ":", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void hostToCharSequenceFunctionIPv6NoPort() throws Exception {
        hostToCharSequenceFunction("[", "1:2:3::5%80", ":", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void hostToCharSequenceFunctionIPv6MissingEndBracket() throws Exception {
        hostToCharSequenceFunction("[", "1:2:3::5%80", ":", 1);
    }

    @Test(expected = NumberFormatException.class)
    public void hostToCharSequenceFunctionIPv6MissingStartBracket() throws Exception {
        hostToCharSequenceFunction("", "1:2:3::5%80", "]:", 1);
    }

    @Test(expected = NumberFormatException.class)
    public void hostToCharSequenceFunctionIPv4InvalidPort() throws Exception {
        hostToCharSequenceFunction("", "1.2.3.4", ":s", 999);
    }

    @Test(expected = NumberFormatException.class)
    public void hostToCharSequenceFunctionIPv6InvalidPort() throws Exception {
        hostToCharSequenceFunction("[", "1:2:3::5%80]", ":s", 1);
    }

    private static void hostToCharSequenceFunction(String hostNamePrefix, String hostName, String hostNameSuffix,
                                                   @Nullable Integer port) throws Exception {
        try (ServerContext serverCtx = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client =
                     forResolvedAddress(hostNamePrefix + hostName + hostNameSuffix + (port == null ? "" : port),
                             serverCtx.listenAddress())
                             .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                     .disableHostnameVerification().build())
                             .buildBlocking()) {
            ReservedBlockingHttpConnection conn = client.reserveConnection(client.get("/"));
            try {
                SSLSession sslSession = conn.connectionContext().sslSession();
                assertNotNull(sslSession);
                assertEquals(hostName, sslSession.getPeerHost());
                if (port == null) {
                    assertEquals(-1, sslSession.getPeerPort());
                } else {
                    assertEquals(port.intValue(), sslSession.getPeerPort());
                }
            } finally {
                conn.release();
            }
        }
    }
}
