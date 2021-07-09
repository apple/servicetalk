/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.NettyIoExecutors;
import io.servicetalk.transport.netty.internal.IoThreadFactory;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Thread.NORM_PRIORITY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class AbstractNettyHttpServerTest {

    enum ExecutorSupplier {
        IMMEDIATE(Executors::immediate),
        CACHED(() -> newCachedThreadExecutor(new DefaultThreadFactory("client-executor", true, NORM_PRIORITY))),
        CACHED_SERVER(() -> newCachedThreadExecutor(new DefaultThreadFactory("server-executor", true, NORM_PRIORITY)));

        final Supplier<Executor> executorSupplier;

        ExecutorSupplier(final Supplier<Executor> executorSupplier) {
            this.executorSupplier = executorSupplier;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNettyHttpServerTest.class);

    @Mock
    Function<StreamingHttpRequest, Publisher<Buffer>> publisherSupplier;

    private static IoExecutor clientIoExecutor;
    private static IoExecutor serverIoExecutor;

    private Executor clientExecutor;
    private Executor serverExecutor;
    private ExecutorSupplier clientExecutorSupplier;
    private ExecutorSupplier serverExecutorSupplier;
    private ConnectionAcceptor connectionAcceptor = ACCEPT_ALL;
    private boolean sslEnabled;
    private ServerContext serverContext;
    private StreamingHttpClient httpClient;
    private StreamingHttpConnection httpConnection;
    private StreamingHttpService service;
    @Nullable
    private StreamingHttpServiceFilterFactory serviceFilterFactory;
    @Nullable
    private ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> connectionFactoryFilter;
    private HttpProtocolConfig protocol = h1Default();
    private TransportObserver clientTransportObserver = NoopTransportObserver.INSTANCE;
    private TransportObserver serverTransportObserver = NoopTransportObserver.INSTANCE;

    void setUp(ExecutorSupplier clientExecutorSupplier, ExecutorSupplier serverExecutorSupplier) {
        this.clientExecutorSupplier = clientExecutorSupplier;
        this.serverExecutorSupplier = serverExecutorSupplier;
        this.clientExecutor = clientExecutorSupplier.executorSupplier.get();
        this.serverExecutor = serverExecutorSupplier.executorSupplier.get();
        try {
            startServer();
        } catch (Exception e) {
            fail(e);
        }
    }

    @BeforeAll
    static void createIoExecutors() {
        clientIoExecutor = NettyIoExecutors.createIoExecutor(new IoThreadFactory("client-io-executor"));
        serverIoExecutor = NettyIoExecutors.createIoExecutor(new IoThreadFactory("server-io-executor"));
    }

    private void startServer() throws Exception {
        final InetSocketAddress bindAddress = localAddress(0);
        service(new TestServiceStreaming(publisherSupplier));

        // A small SNDBUF is needed to test that the server defers closing the connection until writes are complete.
        // However, if it is too small, tests that expect certain chunks of data will see those chunks broken up
        // differently.
        final HttpServerBuilder serverBuilder = HttpServers.forAddress(bindAddress)
                .executionStrategy(defaultStrategy(serverExecutor))
                .socketOption(StandardSocketOptions.SO_SNDBUF, 100)
                .protocols(protocol)
                .transportObserver(serverTransportObserver)
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true);
        if (sslEnabled) {
            serverBuilder.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                    DefaultTestCerts::loadServerKey).build());
        }
        if (serviceFilterFactory != null) {
            serverBuilder.appendServiceFilter(serviceFilterFactory);
        }
        serverContext = awaitIndefinitelyNonNull(listen(serverBuilder.ioExecutor(serverIoExecutor)
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(connectionAcceptor)))
                .beforeOnSuccess(ctx -> LOGGER.debug("Server started on {}.", ctx.listenAddress()))
                .beforeOnError(throwable -> LOGGER.debug("Failed starting server on {}.", bindAddress)));

        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder = newClientBuilder();
        if (sslEnabled) {
            clientBuilder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname()).build());
        }
        if (connectionFactoryFilter != null) {
            clientBuilder.appendConnectionFactoryFilter(connectionFactoryFilter);
        }
        if (clientTransportObserver != NoopTransportObserver.INSTANCE) {
            clientBuilder.appendConnectionFactoryFilter(
                    new TransportObserverConnectionFactoryFilter<>(clientTransportObserver));
        }
        httpClient = clientBuilder.ioExecutor(clientIoExecutor)
                .executionStrategy(defaultStrategy(clientExecutor))
                .protocols(protocol)
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .buildStreaming();
        httpConnection = httpClient.reserveConnection(httpClient.get("/")).toFuture().get();
    }

    private SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder() {
        return HttpClients.forResolvedAddress(serverHostAndPort(serverContext));
    }

    Single<ServerContext> listen(HttpServerBuilder builder) {
        return builder.listenStreaming(service);
    }

    void ignoreTestWhen(ExecutorSupplier clientExecutorSupplier, ExecutorSupplier serverExecutorSupplier) {
        assumeFalse(parseBoolean(System.getenv("CI")) &&
                        this.clientExecutorSupplier == clientExecutorSupplier &&
                        this.serverExecutorSupplier == serverExecutorSupplier,
                   "Ignored flaky test");
    }

    void service(final StreamingHttpService service) {
        this.service = service;
    }

    void serviceFilterFactory(StreamingHttpServiceFilterFactory serviceFilterFactory) {
        this.serviceFilterFactory = serviceFilterFactory;
    }

    void connectionFactoryFilter(
            ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> connectionFactoryFilter) {
        this.connectionFactoryFilter = connectionFactoryFilter;
    }

    @AfterEach
    void stopServer() throws Exception {
        newCompositeCloseable().appendAll(httpConnection, httpClient, clientExecutor, serverContext, serverExecutor)
                .close();
    }

    @AfterAll
    static void shutdownClientIoExecutor() throws Exception {
        newCompositeCloseable().appendAll(clientIoExecutor, serverIoExecutor).close();
    }

    void connectionAcceptor(final ConnectionAcceptor connectionAcceptor) {
        this.connectionAcceptor = connectionAcceptor;
    }

    void sslEnabled(final boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    boolean isSslEnabled() {
        return sslEnabled;
    }

    final ServerContext serverContext() {
        return serverContext;
    }

    final StreamingHttpClient streamingHttpClient() {
        return httpClient;
    }

    final StreamingHttpConnection streamingHttpConnection() {
        return httpConnection;
    }

    void protocol(HttpProtocolConfig protocol) {
        this.protocol = requireNonNull(protocol);
    }

    void transportObserver(TransportObserver client, TransportObserver server) {
        this.clientTransportObserver = requireNonNull(client);
        this.serverTransportObserver = requireNonNull(server);
    }

    StreamingHttpResponse makeRequest(final StreamingHttpRequest request)
            throws Exception {
        return awaitIndefinitelyNonNull(httpConnection.request(request));
    }

    void assertResponse(final HttpResponseMetaData response, final HttpProtocolVersion version,
                        final HttpResponseStatus status) {
        assertEquals(status, response.status());
        assertEquals(version, response.version());
    }

    void assertResponse(final StreamingHttpResponse response, final HttpProtocolVersion version,
                        final HttpResponseStatus status, final int expectedSize)
            throws ExecutionException, InterruptedException {
        assertResponse(response, version, status);
        final int size = awaitIndefinitelyNonNull(
                response.payloadBody().collect(() -> 0, (is, c) -> is + c.readableBytes()));
        assertEquals(expectedSize, size);
    }

    void assertResponse(final StreamingHttpResponse response, final HttpProtocolVersion version,
                        final HttpResponseStatus status, final String expectedPayload)
            throws ExecutionException, InterruptedException {
        assertResponse(response, version, status);
        String actualPayload = response.payloadBody().collect(StringBuilder::new, (sb, chunk) -> {
            sb.append(chunk.toString(US_ASCII));
            return sb;
        }).toFuture().get().toString();
        assertThat(actualPayload, is(expectedPayload));
    }

    static Publisher<Buffer> getChunkPublisherFromStrings(final String... texts) {
        return Publisher.from(texts).map(AbstractNettyHttpServerTest::getChunkFromString);
    }

    static Buffer getChunkFromString(final String text) {
        return DEFAULT_ALLOCATOR.fromAscii(text);
    }

    static <T> T awaitSingleIndefinitelyNonNull(Single<T> single) {
        try {
            return requireNonNull(single.toFuture().get());
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    void assertConnectionClosed() throws Exception {
        httpConnection.onClose().toFuture().get();
    }
}
