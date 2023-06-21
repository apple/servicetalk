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
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.handler.ssl.SslHandshakeTimeoutException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SslHandshakeTimeoutTest {

    static final long HANDSHAKE_TIMEOUT_MILLIS = CI ? 1000 : 200;

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    @ParameterizedTest(name = "{displayName} [{index}] sslProvider={0}")
    @EnumSource(SslProvider.class)
    void testServerSideTimeout(SslProvider sslProvider) throws Exception {
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, HTTP_1)
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .handshakeTimeout(Duration.ofMillis(HANDSHAKE_TIMEOUT_MILLIS))
                        .provider(sslProvider).build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             // Use a non-secure client to open a new connection without sending ClientHello or any data.
             // We expect that remote server will close the connection after SslHandshake timeout.
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, HTTP_1).buildBlocking();
             ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            connection.connectionContext().onClose().toFuture().get(HANDSHAKE_TIMEOUT_MILLIS * 2, MILLISECONDS);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] sslProvider={0}")
    @EnumSource(SslProvider.class)
    void testClientSideTimeout(SslProvider sslProvider) throws Exception {
        // Use a non-secure server that accepts a new connection but never responds to ClientHello.
        try (ServerSocket serverSocket = new ServerSocket(0, 50, getLoopbackAddress())) {
            SERVER_CTX.executor().submit(() -> {
                Socket socket = serverSocket.accept();
                InputStream is = socket.getInputStream();
                while (is.read() > 0) {
                    // noop
                }
                return null;
            });
            try (BlockingHttpClient client = newClientBuilder(
                    serverHostAndPort(serverSocket.getLocalSocketAddress()), CLIENT_CTX, HTTP_1)
                    .sslConfig(new ClientSslConfigBuilder()
                            .handshakeTimeout(Duration.ofMillis(HANDSHAKE_TIMEOUT_MILLIS))
                            .provider(sslProvider).build())
                    .buildBlocking()) {
                assertThrows(SslHandshakeTimeoutException.class, () -> client.request(client.get("/")));
            }
        }
    }
}
