/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslClientAuthMode.REQUIRE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SslContextTest {

    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    @Test
    void mutualSslWithSSLContext() throws Exception {
        SSLContext serverSslContext = createServerSSLContextWithClientAuth();
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(serverSslContext)
                        .clientAuthMode(REQUIRE)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {
            SSLContext clientSslContext = createClientSSLContextWithKeyMaterial();
            try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .sslConfig(new ClientSslConfigBuilder(clientSslContext)
                            .peerHost(serverPemHostname())
                            .build())
                    .buildBlocking()) {
                assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
            }
        }
    }

    private static SSLContext createClientSSLContextWithKeyMaterial() throws Exception {
        KeyManagerFactory kmf = createKeyManagerFactory(DefaultTestCerts::loadClientP12);
        TrustManagerFactory tmf = createTrustManagerFactory(DefaultTestCerts::loadServerCAPem);
        return createSSLContext(kmf, tmf);
    }

    private static SSLContext createServerSSLContextWithClientAuth() throws Exception {
        KeyManagerFactory kmf = createKeyManagerFactory(DefaultTestCerts::loadServerP12);
        TrustManagerFactory tmf = createTrustManagerFactory(DefaultTestCerts::loadClientCAPem);
        return createSSLContext(kmf, tmf);
    }

    private static KeyManagerFactory createKeyManagerFactory(java.util.function.Supplier<InputStream> p12Supplier)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = p12Supplier.get()) {
            keyStore.load(is, KEYSTORE_PASSWORD);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD);
        return kmf;
    }

    private static TrustManagerFactory createTrustManagerFactory(java.util.function.Supplier<InputStream> caPemSupplier)
            throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate caCert;
        try (InputStream is = caPemSupplier.get()) {
            caCert = cf.generateCertificate(is);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    private static SSLContext createSSLContext(KeyManagerFactory kmf, TrustManagerFactory tmf) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }
}
