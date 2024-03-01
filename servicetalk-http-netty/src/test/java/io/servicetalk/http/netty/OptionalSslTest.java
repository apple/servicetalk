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
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the behavior and functionality of {@link ServerSslConfig#acceptInsecureConnections()}.
 */
final class OptionalSslTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    /**
     * Tests that when enabled, the server accepts both TLS and non-TLS connections/requests on the same listen
     * socket.
     *
     * @param acceptEnabled if acceptInsecureConnections is enabled on the server.
     */
    @ParameterizedTest(name = "{displayName} [{index}] acceptEnabled={0}")
    @ValueSource(booleans = {true, false})
    void acceptsEncryptedAndNonEncryptedHttp1Requests(final boolean acceptEnabled) throws Exception {
        final HttpService service = (ctx, request, responseFactory) ->
                succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));

        try (ServerContext server = serverBuilder(acceptEnabled).listenAndAwait(service)) {
            try (BlockingHttpClient client = clientBuilder(server, true).buildBlocking()) {
                final HttpResponse response = client.request(client.get("/"));
                assertEquals(HttpResponseStatus.OK, response.status());
            }

            try (BlockingHttpClient client = clientBuilder(server, false).buildBlocking()) {
                final HttpRequest request = client.get("/");
                if (acceptEnabled) {
                    final HttpResponse response = client.request(request);
                    assertEquals(HttpResponseStatus.OK, response.status());
                } else {
                    assertThrows(ClosedChannelException.class, () -> client.request(request));
                }
            }
        }
    }

    /**
     * Since plaintext H2 is not supported, this test makes sure that optional TLS does not work when H2 is
     * enabled.
     *
     * @param includeH1 if HTTP/1 should also be included in the supported protocols list.
     */
    @ParameterizedTest(name = "{displayName} [{index}] includeH1={0}")
    @ValueSource(booleans = {true, false})
    void doesNotAllowOptionalSslForH2(final boolean includeH1) {
        final HttpProtocolConfig[] protocols = includeH1 ?
                new HttpProtocolConfig[] { h1Default(), h2Default() } :
                new HttpProtocolConfig[] {  h2Default() };
        assertThrows(IllegalStateException.class, () -> serverBuilder(true)
                .protocols(protocols)
                .listenAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()))));
    }

    @Test
    void supportsH1WithSni() {

    }

    @Test
    void supportsH1WithAlpn() {

    }

    private static HttpServerBuilder serverBuilder(final boolean acceptEnabled) {
        ServerSslConfigBuilder configBuilder = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey)
                .acceptInsecureConnections(acceptEnabled);
        return newServerBuilder(SERVER_CTX).sslConfig(configBuilder.build());
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder(final ServerContext ctx,
                                                                                                final boolean withSsl) {
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder = newClientBuilder(ctx, CLIENT_CTX);
        if (withSsl) {
            builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname()).build());
        }
        return builder;
    }
}
