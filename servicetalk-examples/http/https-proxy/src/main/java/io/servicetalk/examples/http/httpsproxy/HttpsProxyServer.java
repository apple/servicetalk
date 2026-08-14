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
package io.servicetalk.examples.http.httpsproxy;

import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.netty.ProxyTunnel;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Starts the two server-side components used by this example: an origin HTTPS server and a mutual-TLS
 * {@link ProxyTunnel CONNECT proxy} in front of it. Run this before {@link HttpsProxyClient}.
 */
public final class HttpsProxyServer {

    static final int ORIGIN_PORT = 8080;
    static final int PROXY_PORT = 8081;

    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    private HttpsProxyServer() {
    }

    public static void main(String[] args) throws Exception {
        // The proxy terminates the outer TLS handshake (requiring a client certificate) and then blindly tunnels the
        // inner, end-to-end TLS session between the client and the origin. ProxyTunnel is a test fixture reused here
        // to keep the example self-contained.
        final ProxyTunnel proxyTunnel = new ProxyTunnel();
        proxyTunnel.sslContext(proxyMtlsSslContext());
        proxyTunnel.needClientAuth(true);
        proxyTunnel.startProxy(PROXY_PORT);

        // A regular origin HTTPS server (server-auth TLS only); the proxy forwards the tunneled bytes here.
        HttpServers.forPort(ORIGIN_PORT)
                // Note: DefaultTestCerts contains self-signed certificates that may be used only for local testing
                // or demonstration purposes. Never use those for real use-cases.
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .payloadBody("Reached the origin through a mutual-TLS CONNECT proxy!", textSerializerUtf8()))
                .awaitShutdown();
    }

    // The proxy's mutual-TLS SSLContext: server identity from the shared "localhost" test certificate, trusting
    // client certificates issued by the example client CA.
    private static SSLContext proxyMtlsSslContext() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = DefaultTestCerts.loadServerP12()) {
            keyStore.load(in, KEYSTORE_PASSWORD);
        }
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD);

        final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (InputStream in = DefaultTestCerts.loadClientCAPem()) {
            trustStore.setCertificateEntry("client-ca",
                    CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }
}
