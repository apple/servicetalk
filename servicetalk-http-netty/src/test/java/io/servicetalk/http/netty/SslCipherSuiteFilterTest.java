/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslConfig.CipherSuiteFilter.IDENTITY;
import static io.servicetalk.transport.api.SslConfig.CipherSuiteFilter.SUPPORTED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SslCipherSuiteFilterTest {

    private static final String SUPPORTED_CIPHER = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";
    private static final String UNSUPPORTED_CIPHER = "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305";

    @ParameterizedTest(name = "{displayName} [{index}] provider={0}")
    @EnumSource(SslProvider.class)
    void clientWithIdentityFilterAndUnsupportedCipher(SslProvider provider) throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
                try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                        .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .peerHost(serverPemHostname())
                                .provider(provider)
                                .ciphers(SUPPORTED_CIPHER, UNSUPPORTED_CIPHER)
                                .cipherSuiteFilter(IDENTITY)
                                .build())
                        .buildBlocking()) {

                    if (provider == SslProvider.JDK) {
                        client.request(client.get("/"));
                    }
                }
            });
            if (provider == SslProvider.OPENSSL) {
                e = (IllegalArgumentException) e.getCause().getCause();
            }
            assertThat(e.getMessage(), containsStringIgnoringCase("unsupported"));
            assertThat(e.getMessage(), containsString(UNSUPPORTED_CIPHER));
        }
    }

    @Test
    void serverWithIdentityFilterAndUnsupportedCipherOpenSslProvider() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            HttpServers.forAddress(localAddress(0))
                    .sslConfig(new ServerSslConfigBuilder(
                            DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                            .provider(SslProvider.OPENSSL)
                            .ciphers(SUPPORTED_CIPHER, UNSUPPORTED_CIPHER)
                            .cipherSuiteFilter(IDENTITY)
                            .build())
                    .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
        });
        e = (IllegalArgumentException) e.getCause().getCause();
        assertThat(e.getMessage(), containsStringIgnoringCase("unsupported"));
        assertThat(e.getMessage(), containsString(UNSUPPORTED_CIPHER));
    }

    @Test
    void serverWithIdentityFilterAndUnsupportedCipherJdkProvider() throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .provider(SslProvider.JDK)
                        .ciphers(SUPPORTED_CIPHER, UNSUPPORTED_CIPHER)
                        .cipherSuiteFilter(IDENTITY)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname())
                             .build())
                     .buildBlocking()) {
            assertThrows(IOException.class, () -> client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] provider={0}")
    @EnumSource(SslProvider.class)
    void supportedFilterWithUnsupportedCipher(SslProvider provider) throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .provider(provider)
                        .ciphers(SUPPORTED_CIPHER, UNSUPPORTED_CIPHER)
                        .cipherSuiteFilter(SUPPORTED)
                        // Enforce TLSv1.2 because OPENSSL provider ignores ciphers when TLSv1.3 is used
                        .sslProtocols("TLSv1.2")
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .peerHost(serverPemHostname())
                             .provider(provider)
                             .ciphers(SUPPORTED_CIPHER, UNSUPPORTED_CIPHER)
                             .cipherSuiteFilter(SUPPORTED)
                             // Enforce TLSv1.2 because OPENSSL provider ignores ciphers when TLSv1.3 is used
                             .sslProtocols("TLSv1.2")
                             .build())
                     .buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            SSLSession sslSession = connection.connectionContext().sslSession();
            assertThat(sslSession, is(notNullValue()));
            assertThat(sslSession.getCipherSuite(), is(SUPPORTED_CIPHER));

            HttpResponse response = connection.request(client.get("/"));
            assertThat(response.status(), is(OK));
        }
    }
}
