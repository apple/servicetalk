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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslListenMode;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the behavior and functionality of {@link ReadOnlyTcpServerConfig#sslListenMode()}.
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

    @ParameterizedTest(name = "{displayName} [{index}] sslListenMode={0}, protocol={1} sslProvider={2}")
    @MethodSource("args")
    void acceptsEncryptedAndNonEncryptedRequests(final SslListenMode sslListenMode, final HttpProtocol protocol,
                                                 final SslProvider sslProvider)
            throws Exception {
        final HttpService service = (ctx, request, responseFactory) -> {
            if ("/secure".equals(request.path())) {
                assertNotNull(ctx.sslConfig());
                assertNotNull(ctx.sslSession());
            } else {
                assertNull(ctx.sslConfig());
                assertNull(ctx.sslSession());
            }
            return succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
        };

        try (ServerContext server = serverBuilder(sslListenMode, sslProvider, protocol).listenAndAwait(service)) {
            for (int i = 0; i < 4; i++) {
                try (BlockingHttpClient client = clientBuilder(server, sslProvider, true, protocol).buildBlocking()) {
                    final HttpResponse response = client.request(client.get("/secure"));
                    assertEquals(HttpResponseStatus.OK, response.status());
                }

                try (BlockingHttpClient client = clientBuilder(server, sslProvider, false, protocol).buildBlocking()) {
                    final HttpRequest request = client.get("/insecure");
                    if (sslListenMode == SslListenMode.SSL_OPTIONAL) {
                        final HttpResponse response = client.request(request);
                        assertEquals(HttpResponseStatus.OK, response.status());
                    } else {
                        assertThrows(ClosedChannelException.class, () -> client.request(request));
                    }
                }
            }
        }
    }

    private static Stream<Arguments> args() {
        List<Arguments> arguments = new ArrayList<>();
        for (SslListenMode listenMode : SslListenMode.values()) {
            for (HttpProtocol protocol : Arrays.asList(HttpProtocol.HTTP_1, HttpProtocol.HTTP_2)) {
                for (SslProvider sslProvider : SslProvider.values()) {
                    arguments.add(Arguments.of(listenMode, protocol, sslProvider));
                }
            }
        }
        return arguments.stream();
    }

    private static HttpServerBuilder serverBuilder(final SslListenMode sslListenMode, final SslProvider sslProvider,
                                                   final HttpProtocol... protocols) {
        ServerSslConfigBuilder configBuilder = new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                DefaultTestCerts::loadServerKey)
                .provider(sslProvider);
        return newServerBuilder(SERVER_CTX, protocols)
                .sslConfig(configBuilder.build())
                .sslListenMode(sslListenMode);
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder(
            final ServerContext ctx, final SslProvider sslProvider, final boolean withSsl,
            final HttpProtocol... protocols) {
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                newClientBuilder(ctx, CLIENT_CTX, protocols);
        if (withSsl) {
            builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname()).provider(sslProvider).build());
        }
        return builder;
    }
}
