/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import io.netty.handler.ssl.OpenSsl;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLHandshakeException;

import static io.netty.handler.ssl.OpenSslContextOption.MAX_CERTIFICATE_LIST_BYTES;
import static io.netty.handler.ssl.SslProvider.isOptionSupported;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslClientAuthMode.REQUIRE;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class SslCertificateMaxSizeTest {
    /**
     * <a href="
     * https://source.chromium.org/chromium/chromium/src/+/main:third_party/boringssl/src/ssl/handshake.cc;l=233">limit
     * </a>
     */
    private static final int BORINGSSL_LOWER_LIMIT = 16384;
    private static boolean certMaxSizeAvailable() {
        return OpenSsl.isAvailable() &&
                isOptionSupported(io.netty.handler.ssl.SslProvider.OPENSSL, MAX_CERTIFICATE_LIST_BYTES);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @CsvSource({"true,false", "false,true", "false,false"})
    @EnabledIf(
            value = "certMaxSizeAvailable",
            disabledReason = "OpenSSL not available or max certificate size is not supported")
    void respectsCertificateMaxSize(boolean serverLowerLimit, boolean clientLowerLimit) throws Exception {
        final int serverCertChain = getTotalByteCount(DefaultTestCerts.loadServerPem());
        final int clientCertChain = getTotalByteCount(DefaultTestCerts.loadClientPem());
        // FIXME: To test the lower bound the cert length must be larger than BoringSSL's lower limit.
        if (serverLowerLimit) {
            assumeTrue(clientCertChain > BORINGSSL_LOWER_LIMIT);
        }
        if (clientLowerLimit) {
            assumeTrue(serverCertChain > BORINGSSL_LOWER_LIMIT);
        }
        try (ServerContext server = serverBuilder(serverLowerLimit ? 1 : clientCertChain)
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()))) {
            try (BlockingHttpClient client = clientBuilder(server, clientLowerLimit ? 1 : serverCertChain)
                    .buildBlocking()) {
                if (serverLowerLimit || clientLowerLimit) {
                    assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/sayHello")));
                } else {
                    assertThat(client.request(client.get("/sayHello")).status(), equalTo(OK));
                }
            }
        }
    }

    private static HttpServerBuilder serverBuilder(int maxCertBytes) {
        ServerSslConfigBuilder sslConfig = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey)
                .trustManager(DefaultTestCerts::loadClientCAPem)
                .clientAuthMode(REQUIRE)
                .maxCertificateListBytes(maxCertBytes)
                .provider(OPENSSL);
        return HttpServers.forAddress(localAddress(0)).sslConfig(sslConfig.build());
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder(
            ServerContext ctx, int maxCertBytes) {
        ClientSslConfigBuilder sslConfig = new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey)
                .peerHost(serverPemHostname())
                .maxCertificateListBytes(maxCertBytes)
                .provider(OPENSSL);
        return HttpClients
                .forSingleAddress(serverHostAndPort(ctx))
                .sslConfig(sslConfig.build());
    }

    private static int getTotalByteCount(InputStream is) throws IOException {
        int totalBytes = 0;
        try {
            final int readSize = 2048;
            int length;
            byte[] bytes = new byte[readSize];
            while ((length = is.read(bytes)) > 0) {
                totalBytes += length;
            }
        } finally {
            is.close();
        }
        return totalBytes;
    }
}
