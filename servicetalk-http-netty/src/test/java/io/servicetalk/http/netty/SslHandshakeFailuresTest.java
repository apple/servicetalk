/*
 * Copyright © 2023, 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.SslHandshakeTimeoutException;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SslHandshakeFailuresTest {

    static final long HANDSHAKE_TIMEOUT_MILLIS = CI ? 500 : 100;

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static List<Arguments> params() {
        List<Arguments> list = new ArrayList<>();
        for (SslProvider sslProvider : SslProvider.values()) {
            for (HttpProtocol protocol : HttpProtocol.values()) {
                list.add(Arguments.of(sslProvider, protocol));
            }
        }
        return list;
    }

    @ParameterizedTest(name = "{displayName} [{index}] sslProvider={0} protocol={1}")
    @MethodSource("params")
    void testServerSideHandshakeTimeout(SslProvider sslProvider, HttpProtocol protocol) throws Exception {
        BlockingQueue<Throwable> observed = new LinkedBlockingQueue<>();
        try (ServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .handshakeTimeout(Duration.ofMillis(HANDSHAKE_TIMEOUT_MILLIS))
                        .provider(sslProvider).build())
                .transportObserver(new HandshakeObserver(observed))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {
            InetSocketAddress address = (InetSocketAddress) serverContext.listenAddress();
            // Use a non-secure Socket to open a new connection without sending ClientHello or any data.
            // We expect that remote server will close the connection after SslHandshake timeout.
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                assertThat(socket.isConnected(), is(true));
                assertThat(observed.take(), is(instanceOf(io.netty.handler.ssl.SslHandshakeTimeoutException.class)));
            }
        }
        assertThat(observed, is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] sslProvider={0} protocol={1}")
    @MethodSource("params")
    void testClientSideHandshakeTimeout(SslProvider sslProvider, HttpProtocol protocol) throws Exception {
        // Use a non-secure server that accepts a new connection but never responds to ClientHello.
        try (ServerSocket serverSocket = new ServerSocket(0, 50, getLoopbackAddress())) {
            SERVER_CTX.executor().submit(() -> {
                while (true) {
                    Socket socket = serverSocket.accept();
                    InputStream is = socket.getInputStream();
                    while (is.read() > 0) {
                        // noop
                    }
                }
            }).subscribe(__ -> { });
            BlockingQueue<Throwable> observed = new LinkedBlockingQueue<>();
            BlockingQueue<RetryableException> retried = new LinkedBlockingQueue<>();
            try (BlockingHttpClient client = newClientBuilder(
                    serverHostAndPort(serverSocket.getLocalSocketAddress()), CLIENT_CTX, protocol)
                    .sslConfig(new ClientSslConfigBuilder()
                            .handshakeTimeout(Duration.ofMillis(HANDSHAKE_TIMEOUT_MILLIS))
                            .provider(sslProvider).build())
                    .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                            new HandshakeObserver(observed)))
                    .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                            .retryRetryableExceptions((req, t) -> {
                                retried.add(t);
                                return BackOffPolicy.ofImmediate(1);
                            }).build())
                    .buildBlocking()) {
                assertHandshakeTimeoutException(assertThrows(Exception.class, () -> client.request(client.get("/"))));
            }
            assertThat(observed, hasSize(2));
            observed.forEach(SslHandshakeFailuresTest::assertHandshakeTimeoutException);
            assertThat(retried, hasSize(2));
            retried.forEach(e -> assertHandshakeTimeoutException((Throwable) e));
        }
    }

    private static void assertHandshakeTimeoutException(Throwable t) {
        assertThat(t, is(instanceOf(SslHandshakeTimeoutException.class)));
        assertThat(t, is(instanceOf(RetryableException.class)));
        assertThat(t.getCause(), is(instanceOf(io.netty.handler.ssl.SslHandshakeTimeoutException.class)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] sslProvider={0} protocol={1}")
    @MethodSource("params")
    void testServerClosesConnectionDuringHandshake(SslProvider sslProvider, HttpProtocol protocol) throws Exception {
        assumeTrue(protocol != HttpProtocol.HTTP_2, "SslHandler does not fail handshake on exceptionCaught");
        // Use a non-secure server that accepts a new connection but never responds to ClientHello.
        try (ServerSocket serverSocket = new ServerSocket(0, 50, getLoopbackAddress())) {
            SERVER_CTX.executor().submit(() -> {
                while (true) {
                    try (Socket socket = serverSocket.accept()) {
                        InputStream is = socket.getInputStream();
                        is.read();
                        Thread.sleep(HANDSHAKE_TIMEOUT_MILLIS);
                    }
                }
            }).subscribe(__ -> { });
            BlockingQueue<Throwable> observed = new LinkedBlockingQueue<>();
            BlockingQueue<RetryableException> retried = new LinkedBlockingQueue<>();
            try (BlockingHttpClient client = newClientBuilder(
                    serverHostAndPort(serverSocket.getLocalSocketAddress()), CLIENT_CTX, protocol)
                    .sslConfig(new ClientSslConfigBuilder().provider(sslProvider).build())
                    .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                            new HandshakeObserver(observed)))
                    .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                            .retryRetryableExceptions((req, t) -> {
                                retried.add(t);
                                return BackOffPolicy.ofImmediate(1);
                            }).build())
                    .buildBlocking()) {
                assertRetryableClosedChannelException(assertThrows(Exception.class,
                        () -> client.request(client.get("/"))));
            }
            assertThat(observed, hasSize(2));
            observed.forEach(SslHandshakeFailuresTest::assertRetryableClosedChannelException);
            assertThat(retried, hasSize(2));
            retried.forEach(e -> assertRetryableClosedChannelException((Throwable) e));
        }
    }

    private static void assertRetryableClosedChannelException(Throwable t) {
        assertThat(t, is(instanceOf(ClosedChannelException.class)));
        assertThat(t, is(instanceOf(RetryableException.class)));
        Throwable cause = t.getCause();
        assertThat(cause, is(instanceOf(ClosedChannelException.class)));
        assertThat(cause, is(not(instanceOf(RetryableException.class))));
        Throwable suppressed = cause.getSuppressed()[0];
        assertThat(suppressed, is(instanceOf(SSLHandshakeException.class)));
        assertThat(suppressed, is(not(instanceOf(RetryableException.class))));
    }

    private static final class HandshakeObserver implements TransportObserver, ConnectionObserver,
                                                            SecurityHandshakeObserver {

        private final BlockingQueue<Throwable> observed;

        HandshakeObserver(BlockingQueue<Throwable> observed) {
            this.observed = observed;
        }

        @Override
        public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
            return this;
        }

        @Override
        public SecurityHandshakeObserver onSecurityHandshake(final SslConfig sslConfig) {
            return this;
        }

        @Override
        public void handshakeFailed(final Throwable cause) {
            observed.add(cause);
        }

        @Override
        public void handshakeComplete(final SSLSession sslSession) {
            observed.add(new IllegalStateException("Unexpected handshakeComplete"));
        }
    }
}
