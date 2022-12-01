/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ReceiveData;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ReceiveMeta;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.Send;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;

@Execution(ExecutionMode.SAME_THREAD)
class ClientEffectiveStrategyTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);

    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String SCHEME = "http";
    private static final String PATH = TestServiceStreaming.SVC_ECHO;

    /**
     * Which builder API will be used and where will test {@link HttpExecutionStrategy} be applied.
     */
    private enum BuilderType {
        /**
         * Test execution strategy applied to single client builder,
         * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)}.
         */
        SINGLE_BUILDER,

        /**
         * Test execution strategy applied to multi client builder,
         * {@link MultiAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)}.
         * Single client builder inherits multi client execution context strategy.
         */
        MULTI_BUILDER,

        /**
         * Multi client builder uses default strategy ({@link HttpExecutionStrategies#defaultStrategy()}).
         * Single client builder inherits multi client builder execution context strategy,
         * {@link HttpExecutionStrategies#defaultStrategy()}.
         * Test execution strategy applied to single client builder,
         * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)}.
         */
        MULTI_DEFAULT_STRATEGY_SINGLE_BUILDER,

        /**
         * Multi client builder uses {@link HttpExecutionStrategies#offloadNone()} strategy.
         * Single client builder inherits multi client builder execution context strategy,
         * {@link HttpExecutionStrategies#offloadNone()}.
         * Test execution strategy applied to single client builder,
         * {@link SingleAddressHttpClientBuilder#executionStrategy(HttpExecutionStrategy)}.
         */
        MULTI_OFFLOAD_NONE_SINGLE_BUILDER
    }

    private static final HttpExecutionStrategy[] BUILDER_STRATEGIES = {
            null, // unspecified
            offloadNever(),
            offloadNone(),
            defaultStrategy(), // should be same as unspecified
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            offloadAll(),
    };
    private static final HttpExecutionStrategy[] FILTER_STRATEGIES = {
            null, // absent
            offloadNever(), // treated as "offloadNone"
            offloadNone(),
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            defaultStrategy(), // treated as "offloadAll"
            offloadAll(),
    };
    private static final HttpExecutionStrategy[] LB_STRATEGIES = {
            null, // absent
            offloadNever(), // treated as "offloadNone"
            offloadNone(),
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            defaultStrategy(), // treated as "offloadAll"
            offloadAll(),
    };
    private static final HttpExecutionStrategy[] CF_STRATEGIES = {
            null, // absent
            offloadNever(), // treated as "offloadNone"
            offloadNone(),
            HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
            defaultStrategy(), // treated as "offloadAll"
            offloadAll(),
    };

    private static ServerContext context;

    @BeforeAll
    static void initServer() {
        try {
            context = HttpServers.forAddress(localAddress(0))
                    .ioExecutor(SERVER_CTX.ioExecutor())
                    .executor(SERVER_CTX.executor())
                    .listenStreamingAndAwait(new TestServiceStreaming());
        } catch (Throwable all) {
            throw new AssertionError("Failed to initialize server", all);
        }
    }

    @AfterAll
    static void shutdownServer() throws Exception {
        if (context != null) {
            context.close();
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> casesSupplier() {
        List<Arguments> arguments = new ArrayList<>();
        for (BuilderType builderType : BuilderType.values()) {
            for (HttpExecutionStrategy builderStrategy : BUILDER_STRATEGIES) {
                if (BuilderType.MULTI_OFFLOAD_NONE_SINGLE_BUILDER == builderType &&
                        null == builderStrategy) {
                    // null builderStrategy won't actually override, so skip.
                    continue;
                }
                for (HttpExecutionStrategy filterStrategy : FILTER_STRATEGIES) {
                    for (HttpExecutionStrategy lbStrategy : LB_STRATEGIES) {
                        for (HttpExecutionStrategy cfStrategy : CF_STRATEGIES) {
                            arguments.add(Arguments.of(builderType, builderStrategy,
                                    filterStrategy, lbStrategy, cfStrategy));
                        }
                    }
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "Type={0} builder={1} filter={2} LB={3} CF={4}")
    @MethodSource("casesSupplier")
    void clientStrategy(final BuilderType builderType,
                        @Nullable final HttpExecutionStrategy builderStrategy,
                        @Nullable final HttpExecutionStrategy filterStrategy,
                        @Nullable final HttpExecutionStrategy lbStrategy,
                        @Nullable final HttpExecutionStrategy cfStrategy) throws Exception {
        ClientInvokingThreadRecorder invokingThreadsRecorder = new ClientInvokingThreadRecorder();

        MultiAddressHttpClientBuilder.SingleAddressInitializer<HostAndPort, InetSocketAddress> initializer =
                (scheme, address, clientBuilder) -> {
                    clientBuilder.appendClientFilter(invokingThreadsRecorder);
                    if (null != filterStrategy) {
                        clientBuilder.appendClientFilter(new StreamingHttpClientFilterFactory() {
                            @Override
                            public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                                return new StreamingHttpClientFilter(client) {
                                };
                            }

                            @Override
                            public HttpExecutionStrategy requiredOffloads() {
                                return filterStrategy;
                            }
                        });
                    }
                    if (null != lbStrategy) {
                        HttpLoadBalancerFactory<InetSocketAddress> lfFactory =
                                DefaultHttpLoadBalancerFactory.Builder.from(
                                new LoadBalancerFactoryImpl() {
                                    @Override
                                    public ExecutionStrategy requiredOffloads() {
                                        return lbStrategy;
                                    }
                                }).build();
                        clientBuilder.loadBalancerFactory(lfFactory);
                    }
                    if (null != cfStrategy) {
                        clientBuilder.appendConnectionFilter(new StreamingHttpConnectionFilterFactory() {
                            @Override
                            public StreamingHttpConnectionFilter create(
                                    final FilterableStreamingHttpConnection connection) {
                                return new StreamingHttpConnectionFilter(connection) {
                                };
                            }

                            @Override
                            public HttpExecutionStrategy requiredOffloads() {
                                return cfStrategy;
                            }
                        });
                    }

                    if (builderType != BuilderType.MULTI_BUILDER && null != builderStrategy) {
                        clientBuilder.executionStrategy(builderStrategy);
                    }
                };
        String requestTarget;
        Supplier<StreamingHttpClient> clientBuilder;
        switch (builderType) {
            case SINGLE_BUILDER:
                requestTarget = PATH;
                SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> singleClientBuilder =
                        HttpClients.forSingleAddress(serverHostAndPort(context))
                                .ioExecutor(CLIENT_CTX.ioExecutor())
                                .executor(CLIENT_CTX.executor());
                // apply initializer immediately
                initializer.initialize(SCHEME, serverHostAndPort(context), singleClientBuilder);
                clientBuilder = singleClientBuilder::buildStreaming;
                break;
            case MULTI_BUILDER:
            case MULTI_DEFAULT_STRATEGY_SINGLE_BUILDER:
            case MULTI_OFFLOAD_NONE_SINGLE_BUILDER:
                requestTarget = SCHEME + "://" + serverHostAndPort(context) + PATH;
                MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> multiClientBuilder =
                        HttpClients.forMultiAddressUrl()
                                .initializer(initializer)
                                .ioExecutor(CLIENT_CTX.ioExecutor())
                                .executor(CLIENT_CTX.executor());
                if (BuilderType.MULTI_BUILDER == builderType && null != builderStrategy) {
                    multiClientBuilder.executionStrategy(builderStrategy);
                }
                if (BuilderType.MULTI_OFFLOAD_NONE_SINGLE_BUILDER == builderType && null != builderStrategy) {
                    // This is expected to ALWAYS be overridden in initializer.
                    multiClientBuilder.executionStrategy(offloadNone());
                }
                clientBuilder = multiClientBuilder::buildStreaming;
                break;
            default:
                throw new AssertionError("Unexpected clientType");
        }

        // Exercise the client
        try (StreamingHttpClient client = Objects.requireNonNull(clientBuilder.get())) {
            for (final ClientApi clientApi : ClientApi.values()) {
                HttpExecutionStrategy effectiveStrategy = computeClientExecutionStrategy(
                        builderType, builderStrategy, filterStrategy, lbStrategy, cfStrategy, clientApi);

                invokingThreadsRecorder.reset(effectiveStrategy);
                String responseBody = getResponse(clientApi, client, requestTarget);
                assertThat("Unexpected response: " + responseBody, responseBody, is(not(emptyString())));
                invokingThreadsRecorder.verifyOffloads(clientApi, client.executionContext().executionStrategy(),
                        responseBody);

                // Execute request one more time to make sure we cover all paths:
                // 1. When client opens a new connection, open connection callback comes on IoThread.
                // 2. When client reuses connection, aggregation of request payload body can run on application thread.
                invokingThreadsRecorder.reset(effectiveStrategy);
                responseBody = getResponse(clientApi, client, requestTarget);
                assertThat("Unexpected response: " + responseBody, responseBody, is(not(emptyString())));
                invokingThreadsRecorder.verifyOffloads(clientApi, client.executionContext().executionStrategy(),
                        responseBody);
            }
        }
    }

    /**
     * Computes the base execution strategy that the client will use based on the selected builder strategy, filter
     * strategy, load balancer strategy, connection factory filter strategy.
     *
     * @param builderType Type of the builder being used for creating the client.
     * @param builder strategy specified for client builder or null to use builder default.
     * @param filter strategy specified for client stream filter to be added to client builder or null if no
     * filter will be added.
     * @param lb strategy specified for load balancer factory to be added to client builder or null if no
     * load balancer will be added.
     * @param cf strategy specified for connection filter factory to be added to client builder or null if no
     * connection filter will be added.
     * @param clientApi the client API which will be used for the request.
     * @return The strategy as computed
     */
    private static HttpExecutionStrategy computeClientExecutionStrategy(final BuilderType builderType,
                                                                 @Nullable final HttpExecutionStrategy builder,
                                                                 @Nullable final HttpExecutionStrategy filter,
                                                                 @Nullable final HttpExecutionStrategy lb,
                                                                 @Nullable final HttpExecutionStrategy cf,
                                                                 final ClientApi clientApi) {
        @Nullable HttpExecutionStrategy chain = mergeStrategies(cf, mergeStrategies(lb, filter));

        HttpExecutionStrategy merged = null != chain && chain.hasOffloads() ?
                // filter chain has offloads
                null == builder || defaultStrategy() == builder ?
                        // builder has default strategy, use chain strategy
                        chain :
                        // builder specifies strategy.
                        builder.hasOffloads() ?
                                // combine with chain strategy
                                mergeStrategies(builder, chain) :
                                // builder wants no offloads
                                builder :
                // filter chain has no offloads
                null == builder ?
                        // unspecified, it is default
                        defaultStrategy() :
                        // use builder strategy as specified
                        builder;

        switch (builderType) {
            case SINGLE_BUILDER:
                return defaultStrategy() == merged ? clientApi.strategy() : merged;
            case MULTI_BUILDER:
                return null == builder || defaultStrategy() == builder ?
                    clientApi.strategy().merge(merged) :
                    builder.hasOffloads() ? merged : builder;
            case MULTI_DEFAULT_STRATEGY_SINGLE_BUILDER:
                    if (defaultStrategy() == merged || (null != builder && !builder.hasOffloads())) {
                        merged = offloadNone();
                    }
                    return clientApi.strategy().merge(merged);
            case MULTI_OFFLOAD_NONE_SINGLE_BUILDER:
                return builder == null ?
                        offloadNone() : defaultStrategy() == merged ? offloadNone() : merged;
            default:
                throw new AssertionError("Unexpected builder type: " + builderType);
        }
    }

    @Nullable
    private static HttpExecutionStrategy mergeStrategies(@Nullable HttpExecutionStrategy first,
                                                         @Nullable HttpExecutionStrategy second) {
        first = replaceSpecialStrategies(first);
        second = replaceSpecialStrategies(second);
        return null == first ? second :
                null == second ? first : first.merge(second);
    }

    @Nullable
    private static HttpExecutionStrategy replaceSpecialStrategies(@Nullable final HttpExecutionStrategy strategy) {
        return offloadNever() == strategy ? offloadNone() :
                defaultStrategy() == strategy ? offloadAll() : strategy;
    }

    /**
     * Return a string response by performing a request operation.
     *
     * @param clientApi The client API variation to use for the request.
     * @param client The async streaming HTTP client to use for the request.
     * @param requestTarget The target of the request.
     * @return The response converted to a String from UTF-8 payload.
     * @throws Exception for any problems completing the request.
     */
    private static String getResponse(final ClientApi clientApi, final StreamingHttpClient client,
                                      final String requestTarget) throws Exception {
        switch (clientApi) {
            case BLOCKING_AGGREGATED: {
                BlockingHttpClient blockingClient = client.asBlockingClient();
                HttpResponse response = blockingClient.request(blockingClient.post(requestTarget)
                        .payloadBody(content(blockingClient.executionContext())));
                return response.payloadBody().toString(UTF_8);
            }

            case BLOCKING_STREAMING: {
                BlockingStreamingHttpClient blockingStreamingClient = client.asBlockingStreamingClient();
                BlockingStreamingHttpResponse response = blockingStreamingClient.request(
                        blockingStreamingClient.post(requestTarget)
                                .payloadBody(singleton(content(blockingStreamingClient.executionContext()))));
                Supplier<CompositeBuffer> supplier =
                        blockingStreamingClient.executionContext().bufferAllocator()::newCompositeBuffer;
                return StreamSupport.stream(response.payloadBody().spliterator(), false)
                        .reduce((Buffer base, Buffer buffer) -> (base instanceof CompositeBuffer ?
                                (CompositeBuffer) base : supplier.get().addBuffer(base)).addBuffer(buffer))
                        .map(buffer -> buffer.toString(UTF_8))
                        .orElseThrow(() -> new AssertionError("No payload in response"));
            }

            case ASYNC_AGGREGATED: {
                HttpClient httpClient = client.asClient();
                HttpResponse response = httpClient.request(httpClient.post(requestTarget)
                                .payloadBody(content(httpClient.executionContext())))
                        .toFuture().get();
                return response.payloadBody().toString(UTF_8);
            }

            case ASYNC_STREAMING: {
                CompositeBuffer responsePayload = client.request(client.post(requestTarget)
                                .payloadBody(Publisher.from(content(client.executionContext()))))
                        .flatMap(resp -> resp.payloadBody().collect(() ->
                                        client.executionContext().bufferAllocator().newCompositeBuffer(),
                                CompositeBuffer::addBuffer))
                        .toFuture().get();
                return responsePayload.toString(UTF_8);
            }

            default:
                throw new AssertionError("Unexpected client api " + clientApi);
        }
    }

    private static Buffer content(HttpExecutionContext ctx) {
        return ctx.bufferAllocator().fromUtf8(ctx.executionStrategy().toString());
    }

    private static final class ClientInvokingThreadRecorder implements StreamingHttpClientFilterFactory {

        private Thread applicationThread = Thread.currentThread();
        private HttpExecutionStrategy expectedStrategy;
        private final EnumSet<ClientOffloadPoint> offloadPoints = EnumSet.noneOf(ClientOffloadPoint.class);
        private final ConcurrentMap<ClientOffloadPoint, String> invokingThreads = new ConcurrentHashMap<>();
        private final Queue<Throwable> errors = new LinkedBlockingQueue<>();

        void reset(HttpExecutionStrategy expectedStrategy) {
            invokingThreads.clear();
            errors.clear();
            offloadPoints.clear();
            applicationThread = Thread.currentThread();

            this.expectedStrategy = expectedStrategy;
            // adjust expected offloads for specific execution strategy
            if (expectedStrategy.isSendOffloaded()) {
                offloadPoints.add(Send);
            }
            if (expectedStrategy.isMetadataReceiveOffloaded()) {
                offloadPoints.add(ReceiveMeta);
            }
            if (expectedStrategy.isDataReceiveOffloaded()) {
                offloadPoints.add(ReceiveData);
            }
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // No influence since we do not block.
            return HttpExecutionStrategies.offloadNone();
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    final HttpExecutionStrategy clientStrategy = delegate.executionContext().executionStrategy();
                    final HttpExecutionStrategy requestStrategy = request.context().get(HTTP_EXECUTION_STRATEGY_KEY);
                    return delegate.request(request.transformPayloadBody(payload ->
                                    payload.beforeRequest(__ -> recordThread(Send, clientStrategy, requestStrategy))))
                            .beforeOnSuccess(__ -> recordThread(ReceiveMeta, clientStrategy, requestStrategy))
                            .map(resp -> resp.transformPayloadBody(payload -> payload
                                    .beforeOnNext(__ -> recordThread(ReceiveData, clientStrategy, requestStrategy))));
                }
            };
        }

        void recordThread(final ClientOffloadPoint offloadPoint, final HttpExecutionStrategy clientStrategy,
                          @Nullable final HttpExecutionStrategy requestStrategy) {
            invokingThreads.compute(offloadPoint, (ClientOffloadPoint offload, String recorded) -> {
                Thread current = Thread.currentThread();
                boolean appThread = current == applicationThread;
                boolean ioThread = IoThreadFactory.IoThread.isIoThread(current);
                if (appThread && Send == offloadPoint && !offloadPoints.contains(Send)) {
                    // We allow the app thread to be used for send if not offloaded
                } else {
                    if (offloadPoints.contains(offloadPoint)) {
                        if (ioThread) {
                            final AssertionError e = new AssertionError("Expected offloaded thread at " + offloadPoint +
                                    ", but was running on " + current.getName() + ". clientStrategy=" + clientStrategy +
                                    ", requestStrategy=" + requestStrategy);
                            errors.add(e);
                        }
                    } else {
                        if (!ioThread) {
                            final AssertionError e = new AssertionError("Expected IoThread or " +
                                    applicationThread.getName() + " at " + offloadPoint +
                                    ", but was running on an offloading executor thread: " + current.getName() +
                                    ". clientStrategy=" + clientStrategy + ", expectedStrategy=" + expectedStrategy
                                    + ", requestStrategy=" + requestStrategy);
                            errors.add(e);
                        }
                    }
                }
                return ioThread ? "eventLoop" : (appThread ? "application" : "offloaded");
            });
        }

        public void verifyOffloads(ClientApi clientApi, HttpExecutionStrategy clientStrategy, String apiStrategy) {
            assertNoAsyncErrors("API=" + clientApi + ", apiStrategy=" + apiStrategy +
                    ", clientStrategy=" + clientStrategy +
                    ", expectedStrategy=" + expectedStrategy + ". Async Errors! See suppressed", errors);
            assertThat("Unexpected offload points recorded. " + invokingThreads,
                    invokingThreads.size(), Matchers.is(ClientOffloadPoint.values().length));
        }
    }

    private static class LoadBalancerFactoryImpl
            implements LoadBalancerFactory<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection> {
        @SuppressWarnings("deprecation")
        @Override
        public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
                final String targetResource,
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<InetSocketAddress>>>
                        eventPublisher,
                final ConnectionFactory<InetSocketAddress, T> connectionFactory) {
            return new RoundRobinLoadBalancerFactory
                    .Builder<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection>().build()
                    .newLoadBalancer(targetResource, eventPublisher, connectionFactory);
        }

        @Override
        public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancerTyped(
                final String targetResource,
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<InetSocketAddress>>>
                        eventPublisher,
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection>
                        connectionFactory) {
            return new RoundRobinLoadBalancerFactory
                    .Builder<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection>().build()
                    .newLoadBalancerTyped(targetResource, eventPublisher, connectionFactory);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.offloadNone();
        }
    }

    /**
     * Which API flavor will be used.
     */
    private enum ClientApi {
        ASYNC_AGGREGATED(EnumSet.of(ReceiveData)),
        ASYNC_STREAMING(EnumSet.allOf(ClientOffloadPoint.class)),
        BLOCKING_AGGREGATED(EnumSet.noneOf(ClientOffloadPoint.class)),
        BLOCKING_STREAMING(EnumSet.of(Send));

        private final HttpExecutionStrategy strategy;

        ClientApi(EnumSet<ClientOffloadPoint> offloads) {
            HttpExecutionStrategies.Builder builder = HttpExecutionStrategies.customStrategyBuilder();

            if (offloads.contains(Send)) {
                builder.offloadSend();
            }
            if (offloads.contains(ReceiveMeta)) {
                builder.offloadReceiveMetadata();
            }
            if (offloads.contains(ReceiveData)) {
                builder.offloadReceiveData();
            }

            this.strategy = builder.build();
        }

        public HttpExecutionStrategy strategy() {
            return strategy;
        }
    }

    /**
     * Execution points at which the client will sample the executing thread
     */
    enum ClientOffloadPoint {
        Send,
        ReceiveMeta,
        ReceiveData
    }
}
