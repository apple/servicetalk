/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
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
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.NettyIoExecutors;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.List;
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
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Thread.NORM_PRIORITY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

public abstract class AbstractNettyHttpServerTest {

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

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule().silent();

    @Mock
    Function<StreamingHttpRequest, Publisher<Buffer>> publisherSupplier;

    private static IoExecutor clientIoExecutor;
    private static IoExecutor serverIoExecutor;

    private final Executor clientExecutor;
    private final Executor serverExecutor;
    private final ExecutorSupplier clientExecutorSupplier;
    private final ExecutorSupplier serverExecutorSupplier;
    private ConnectionAcceptor connectionAcceptor = ACCEPT_ALL;
    private boolean sslEnabled;
    private ServerContext serverContext;
    private StreamingHttpClient httpClient;
    private StreamingHttpConnection httpConnection;
    private StreamingHttpService service;
    @Nullable
    private StreamingHttpServiceFilterFactory serviceFilterFactory;
    private HttpProtocolConfig protocol = h1Default();
    @Nullable
    private TransportObserver clientTransportObserver;
    @Nullable
    private TransportObserver serverTransportObserver;

    AbstractNettyHttpServerTest(ExecutorSupplier clientExecutorSupplier, ExecutorSupplier serverExecutorSupplier) {
        this.clientExecutorSupplier = clientExecutorSupplier;
        this.serverExecutorSupplier = serverExecutorSupplier;
        this.clientExecutor = clientExecutorSupplier.executorSupplier.get();
        this.serverExecutor = serverExecutorSupplier.executorSupplier.get();
    }

    @BeforeClass
    public static void createIoExecutors() {
        clientIoExecutor = NettyIoExecutors.createIoExecutor(new IoThreadFactory("client-io-executor"));
        serverIoExecutor = NettyIoExecutors.createIoExecutor(new IoThreadFactory("server-io-executor"));
    }

    @Before
    public void startServer() throws Exception {
        final InetSocketAddress bindAddress = localAddress(0);
        service(new TestServiceStreaming(publisherSupplier));

        // A small SNDBUF is needed to test that the server defers closing the connection until writes are complete.
        // However, if it is too small, tests that expect certain chunks of data will see those chunks broken up
        // differently.
        final HttpServerBuilder serverBuilder = HttpServers.forAddress(bindAddress)
                .executionStrategy(defaultStrategy(serverExecutor))
                .socketOption(StandardSocketOptions.SO_SNDBUF, 100)
                .protocols(protocol);
        if (sslEnabled) {
            serverBuilder.secure().commit(DefaultTestCerts::loadServerPem,
                    DefaultTestCerts::loadServerKey);
        }
        if (serviceFilterFactory != null) {
            serverBuilder.appendServiceFilter(serviceFilterFactory);
        }
        if (serverTransportObserver != null) {
            serverBuilder.transportObserver(serverTransportObserver);
        }
        serverContext = awaitIndefinitelyNonNull(listen(serverBuilder.ioExecutor(serverIoExecutor)
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(connectionAcceptor)))
                .beforeOnSuccess(ctx -> LOGGER.debug("Server started on {}.", ctx.listenAddress()))
                .beforeOnError(throwable -> LOGGER.debug("Failed starting server on {}.", bindAddress)));

        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder = newClientBuilder();
        if (sslEnabled) {
            clientBuilder.secure().disableHostnameVerification()
                    .trustManager(DefaultTestCerts::loadMutualAuthCaPem).commit();
        }
        if (clientTransportObserver != null) {
            clientBuilder.appendConnectionFactoryFilter(observableConnectionFactoryFilter(clientTransportObserver));
        }
        httpClient = clientBuilder.ioExecutor(clientIoExecutor)
                .executionStrategy(defaultStrategy(clientExecutor))
                .protocols(protocol)
                .buildStreaming();
        httpConnection = httpClient.reserveConnection(httpClient.get("/")).toFuture().get();
    }

    SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder() {
        return HttpClients.forResolvedAddress(serverHostAndPort(serverContext));
    }

    Single<ServerContext> listen(HttpServerBuilder builder) {
        return builder.listenStreaming(service);
    }

    protected void ignoreTestWhen(ExecutorSupplier clientExecutorSupplier, ExecutorSupplier serverExecutorSupplier) {
        assumeThat("Ignored flaky test",
                parseBoolean(System.getenv("CI")) &&
                        this.clientExecutorSupplier == clientExecutorSupplier &&
                        this.serverExecutorSupplier == serverExecutorSupplier, is(FALSE));
    }

    protected void service(final StreamingHttpService service) {
        this.service = service;
    }

    void serviceFilterFactory(StreamingHttpServiceFilterFactory serviceFilterFactory) {
        this.serviceFilterFactory = serviceFilterFactory;
    }

    @After
    public void stopServer() throws Exception {
        newCompositeCloseable().appendAll(httpConnection, httpClient, clientExecutor, serverContext, serverExecutor)
                .close();
    }

    @AfterClass
    public static void shutdownClientIoExecutor() throws Exception {
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

    ServerContext serverContext() {
        return serverContext;
    }

    void protocol(HttpProtocolConfig protocol) {
        this.protocol = requireNonNull(protocol);
    }

    void transportObserver(@Nullable TransportObserver client, @Nullable TransportObserver server) {
        this.clientTransportObserver = client;
        this.serverTransportObserver = server;
    }

    private static ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection>
            observableConnectionFactoryFilter(TransportObserver clientTransportObserver) {
        return original ->
                new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(original) {
                    @Override
                    public Single<FilterableStreamingHttpConnection> newConnection(
                            InetSocketAddress inetSocketAddress, @Nullable TransportObserver observer) {
                        return delegate().newConnection(inetSocketAddress, clientTransportObserver);
                    }
                };
    }

    StreamingHttpResponse makeRequest(final StreamingHttpRequest request)
            throws Exception {
        return awaitIndefinitelyNonNull(httpConnection.request(request));
    }

    void assertResponse(final StreamingHttpResponse response, final HttpProtocolVersion version,
                        final HttpResponseStatus status, final int expectedSize)
            throws ExecutionException, InterruptedException {
        assertEquals(status, response.status());
        assertEquals(version, response.version());

        final int size = awaitIndefinitelyNonNull(
                response.payloadBody().collect(() -> 0, (is, c) -> is + c.readableBytes()));
        assertEquals(expectedSize, size);
    }

    void assertResponse(final StreamingHttpResponse response, final HttpProtocolVersion version,
                        final HttpResponseStatus status, final List<String> expectedPayloadChunksAsStrings)
            throws ExecutionException, InterruptedException {
        assertEquals(status, response.status());
        assertEquals(version, response.version());
        final List<String> bodyAsListOfStrings = getBodyAsListOfStrings(response);
        if (expectedPayloadChunksAsStrings.isEmpty()) {
            assertTrue(bodyAsListOfStrings.isEmpty());
        } else {
            assertThat(bodyAsListOfStrings, contains(expectedPayloadChunksAsStrings.toArray()));
        }
    }

    Publisher<Buffer> getChunkPublisherFromStrings(final String... texts) {
        return Publisher.from(texts).map(this::getChunkFromString);
    }

    StreamingHttpConnection streamingHttpConnection() {
        return httpConnection;
    }

    Buffer getChunkFromString(final String text) {
        return DEFAULT_ALLOCATOR.fromAscii(text);
    }

    static List<String> getBodyAsListOfStrings(final StreamingHttpResponse response)
            throws ExecutionException, InterruptedException {
        return awaitIndefinitelyNonNull(response.payloadBody().map(c -> c.toString(US_ASCII)));
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
