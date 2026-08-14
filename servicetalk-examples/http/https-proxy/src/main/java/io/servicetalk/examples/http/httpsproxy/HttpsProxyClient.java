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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ProxyConfigBuilder;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;

import static io.servicetalk.examples.http.httpsproxy.HttpsProxyServer.ORIGIN_PORT;
import static io.servicetalk.examples.http.httpsproxy.HttpsProxyServer.PROXY_PORT;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * A client that reaches an origin server through a mutual-TLS HTTP CONNECT proxy: it presents a client certificate to
 * the proxy (outer TLS) and does ordinary server-auth TLS to the origin (inner TLS) over the established tunnel.
 * <p>
 * Run {@link HttpsProxyServer} first.
 */
public final class HttpsProxyClient {

    private HttpsProxyClient() {
    }

    public static void main(String[] args) throws Exception {
        // Note: DefaultTestCerts contains self-signed certificates that may be used only for local testing
        // or demonstration purposes. Never use those for real use-cases.
        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", ORIGIN_PORT)
                .proxyConfig(new ProxyConfigBuilder<>(HostAndPort.of("localhost", PROXY_PORT))
                        // Outer TLS to the proxy: trust the proxy's certificate and present our own to authenticate.
                        .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey).build())
                        .build())
                // Inner TLS to the origin, tunneled through the CONNECT: standard server authentication.
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build())
                // Optional: log both TLS handshakes (proxy and origin) as they complete.
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                        new HandshakeLoggingTransportObserver()))
                .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            System.out.println(response.toString((name, value) -> value));
            System.out.println(response.payloadBody(textSerializerUtf8()));
        }
    }
}
