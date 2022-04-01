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
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpServerBuilder;
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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
class ClientEffectiveStrategyTest {
    private static final String SCHEME = "http";
    private static final String PATH = "/";
    private static final String GREETING = "Hello";
    private static final HttpExecutionStrategy CUSTOM_OFFLOAD_ALL = new HttpExecutionStrategy() {
        @Override
        public boolean isMetadataReceiveOffloaded() {
            return true;
        }

        @Override
        public boolean isDataReceiveOffloaded() {
            return true;
        }

        @Override
        public boolean isSendOffloaded() {
            return true;
        }

        @Override
        public boolean isEventOffloaded() {
            return true;
        }

        @Override
        public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
            return HttpExecutionStrategies.offloadAll();
        }

        @Override
        public boolean isCloseOffloaded() {
            return true;
        }
    };

    /**
     * Which builder API will be used and where will ExecutionStrategy be initialized.
     */
    private enum BuilderType {
        Single,
        Multi_ExecutionStrategy_On_Builder,
        Multi_ExecutionStrategy_In_Initializer,
        Multi_ExecutionStrategy_Initializer_Override
    }

    /**
     * Which API flavor will be used.
     */
    private enum ClientApi {
        BlockingAggregate,
        BlockingStreaming,
        AsyncAggregate,
        AsyncStreaming
    }

    /**
     * Execution points at which the client will sample the executing thread
     */
    enum ClientOffloadPoint {
        Send,
        ReceiveMeta,
        ReceiveData
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

    private static final ServerContext context;

    static {
        try {
            HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0));
            context = serverBuilder.listenBlocking((ctx, request, responseFactory) ->
                    responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator()
                            .fromAscii(GREETING))).toFuture().get();
        } catch (Throwable all) {
            throw new AssertionError("Failed to initialize server", all);
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> casesSupplier() {
        List<Arguments> arguments = new ArrayList<>();
        for (BuilderType builderType : BuilderType.values()) {
            if (BuilderType.Multi_ExecutionStrategy_Initializer_Override == builderType) {
                continue;
            }
            for (ClientApi clientApi : ClientApi.values()) {
                for (HttpExecutionStrategy builderStrategy : BUILDER_STRATEGIES) {
                    if (BuilderType.Multi_ExecutionStrategy_Initializer_Override == builderType &&
                            null == builderStrategy) {
                        // null builderStrategy won't actually override, so skip.
                        continue;
                    }
                    for (HttpExecutionStrategy filterStrategy : FILTER_STRATEGIES) {
                        for (HttpExecutionStrategy lbStrategy : LB_STRATEGIES) {
                            for (HttpExecutionStrategy cfStrategy : CF_STRATEGIES) {
                                arguments.add(Arguments.of(builderType, clientApi, builderStrategy,
                                        filterStrategy, lbStrategy, cfStrategy));
                            }
                        }
                    }
                }
            }
        }
        return arguments.stream();
    }

    @AfterAll
    static void shutdown() throws Exception {
        context.closeGracefully();
    }

    @ParameterizedTest(name = "Type={0} API={1} builder={2} filter={3} LB={4} CF={5}")
    @MethodSource("casesSupplier")
    void clientStrategy(final BuilderType builderType, ClientApi clientApi,
                        @Nullable final HttpExecutionStrategy builderStrategy,
                        @Nullable final HttpExecutionStrategy filterStrategy,
                        @Nullable final HttpExecutionStrategy lbStrategy,
                        @Nullable final HttpExecutionStrategy cfStrategy) throws Exception {
        HttpExecutionStrategy effectiveStrategy = computeClientExecutionStrategy(
                builderType, builderStrategy, filterStrategy, lbStrategy, cfStrategy, clientApi);

        ClientInvokingThreadRecorder invokingThreadsRecorder =
                new ClientInvokingThreadRecorder(clientApi, effectiveStrategy);

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

                    if (builderType != BuilderType.Multi_ExecutionStrategy_On_Builder && null != builderStrategy) {
                        clientBuilder.executionStrategy(builderStrategy);
                    }
                };
        String requestTarget;
        Supplier<StreamingHttpClient> clientBuilder;
        switch (builderType) {
            case Single:
                requestTarget = PATH;
                SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> singleClientBuilder =
                        HttpClients.forSingleAddress(serverHostAndPort(context));
                // apply initializer immediately
                initializer.initialize(SCHEME, serverHostAndPort(context), singleClientBuilder);
                clientBuilder = singleClientBuilder::buildStreaming;
                break;
            case Multi_ExecutionStrategy_On_Builder:
            case Multi_ExecutionStrategy_In_Initializer:
            case Multi_ExecutionStrategy_Initializer_Override:
                requestTarget = SCHEME + "://" + serverHostAndPort(context) + PATH;
                MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> multiClientBuilder =
                        HttpClients.forMultiAddressUrl().initializer(initializer);
                if (BuilderType.Multi_ExecutionStrategy_On_Builder == builderType && null != builderStrategy) {
                    multiClientBuilder.executionStrategy(builderStrategy);
                }
                if (BuilderType.Multi_ExecutionStrategy_Initializer_Override == builderType &&
                        null != builderStrategy) {
                    // This is expected to ALWAYS be overridden in initializer.
                    multiClientBuilder.executionStrategy(CUSTOM_OFFLOAD_ALL);
                }
                clientBuilder = multiClientBuilder::buildStreaming;
                break;
            default:
                throw new AssertionError("Unexpected clientType");
        }

        // Exercise the client
        try (StreamingHttpClient client = Objects.requireNonNull(clientBuilder.get())) {
            String responseBody = getResponse(clientApi, client, requestTarget);
            assertThat(responseBody, is(GREETING));
            invokingThreadsRecorder.verifyOffloads();
        }
    }

    /**
     * Computes the base execution strategy that the client will use based on the selected builder strategy, filter
     * strategy, load balancer strategy, connection factory filter strategy.
     *
     * @param builder strategy specified for client builder or null to use builder default.
     * @param filter strategy specified for client stream filter to be added to client builder or null if no
     * filter will be added.
     * @param lb strategy specified for load balancer factory to be added to client builder or null if no
     * load balancer will be added.
     * @param cf strategy specified for connection filter factory to be added to client builder or null if no
     * connection filter will be added.
     * @return The strategy as computed
     */
    private HttpExecutionStrategy computeClientExecutionStrategy(BuilderType builderType,
                                                                 @Nullable final HttpExecutionStrategy builder,
                                                                 @Nullable final HttpExecutionStrategy filter,
                                                                 @Nullable final HttpExecutionStrategy lb,
                                                                 @Nullable final HttpExecutionStrategy cf,
                                                                 ClientApi clientApi) {
        @Nullable HttpExecutionStrategy chain = mergeStrategies(cf, mergeStrategies(lb, filter));

        HttpExecutionStrategy merged = null != chain && chain.hasOffloads() ?
                null == builder || defaultStrategy() == builder ?
                        chain : builder.hasOffloads() ? mergeStrategies(builder, chain) : builder :
                null == builder ? defaultStrategy() : builder;

        switch (builderType) {
            case Single:
                if (defaultStrategy() == merged) {
                    switch (clientApi) {
                        case BlockingAggregate:
                            return offloadNone();
                        case BlockingStreaming:
                            return HttpExecutionStrategies.customStrategyBuilder().offloadSend().build();
                        case AsyncAggregate:
                            return HttpExecutionStrategies.customStrategyBuilder().offloadReceiveData().build();
                        case AsyncStreaming:
                            return HttpExecutionStrategies.customStrategyBuilder()
                                    .offloadSend().offloadReceiveMetadata().offloadReceiveData().build();
                        default:
                            throw new AssertionError("Unexpected client api: " + clientApi);
                    }
                } else {
                    return merged;
                }
            case Multi_ExecutionStrategy_On_Builder:
                if (null == builder || defaultStrategy() == builder) {
                    if (defaultStrategy() == merged) {
                        merged = offloadNone();
                    }
                    switch (clientApi) {
                        case BlockingAggregate:
                            return merged;
                        case BlockingStreaming:
                            return HttpExecutionStrategies.customStrategyBuilder().offloadSend().build().merge(merged);
                        case AsyncAggregate:
                            return HttpExecutionStrategies.customStrategyBuilder()
                                    .offloadReceiveData().build().merge(merged);
                        case AsyncStreaming:
                            return HttpExecutionStrategies.customStrategyBuilder()
                                    .offloadSend().offloadReceiveMetadata().offloadReceiveData().build().merge(merged);
                        default:
                            throw new AssertionError("Unexpected client api: " + clientApi);
                    }
                }
                return merged;
            case Multi_ExecutionStrategy_In_Initializer:
                    if (defaultStrategy() == merged || (null != builder && !builder.hasOffloads())) {
                        merged = offloadNone();
                    }
                    switch (clientApi) {
                        case BlockingAggregate:
                            return merged;
                        case BlockingStreaming:
                            return HttpExecutionStrategies.customStrategyBuilder().offloadSend().build().merge(merged);
                        case AsyncAggregate:
                            return HttpExecutionStrategies.customStrategyBuilder()
                                    .offloadReceiveData().build().merge(merged);
                        case AsyncStreaming:
                            return HttpExecutionStrategies.customStrategyBuilder()
                                    .offloadSend().offloadReceiveMetadata().offloadReceiveData().build().merge(merged);
                        default:
                            throw new AssertionError("Unexpected client api: " + clientApi);
                    }
            default:
                throw new AssertionError("Unexpected builder type: " + builderType);
        }
    }

    private @Nullable HttpExecutionStrategy mergeStrategies(@Nullable HttpExecutionStrategy first,
                                                            @Nullable HttpExecutionStrategy second) {
        first = offloadNever() != first ? defaultStrategy() != first ? first : offloadAll() : offloadNone();
        second = offloadNever() != second ? defaultStrategy() != second ? second : offloadAll() : offloadNone();
        return null == first ? second : null == second ? first : first.merge(second);
    }

    private String getResponse(ClientApi clientApi, StreamingHttpClient client, String requestTarget) throws Exception {
        switch (clientApi) {
            case BlockingAggregate:
                BlockingHttpClient blockingClient = client.asBlockingClient();
                return blockingClient.request(blockingClient.get(requestTarget)).payloadBody()
                        .toString(StandardCharsets.US_ASCII);
            case BlockingStreaming:
                BlockingStreamingHttpClient blockingStreamingClient = client.asBlockingStreamingClient();
                Supplier<CompositeBuffer> supplier = client.executionContext().bufferAllocator()::newCompositeBuffer;
                return StreamSupport.stream(
                                blockingStreamingClient.request(blockingStreamingClient.get(requestTarget))
                                        .payloadBody().spliterator(), false)
                        .reduce((Buffer base, Buffer buffer) -> (base instanceof CompositeBuffer ?
                                ((CompositeBuffer) base) : supplier.get().addBuffer(base)).addBuffer(buffer))
                        .map(buffer -> buffer.toString(StandardCharsets.US_ASCII))
                        .orElse("");
            case AsyncStreaming:
                return client.request(client.get(requestTarget)).flatMap(resp -> resp.payloadBody().collect(() ->
                                        client.executionContext().bufferAllocator().newCompositeBuffer(),
                                CompositeBuffer::addBuffer))
                        .toFuture().get()
                        .toString(StandardCharsets.US_ASCII);
            case AsyncAggregate:
                HttpClient httpClient = client.asClient();
                return httpClient.request(httpClient.get(requestTarget)).toFuture().get().payloadBody()
                        .toString(StandardCharsets.US_ASCII);
            default:
                throw new AssertionError("Unexpected client api " + clientApi);
        }
    }

    private static final class ClientInvokingThreadRecorder implements StreamingHttpClientFilterFactory {

        private final EnumSet<ClientOffloadPoint> offloadPoints;
        private final ConcurrentMap<ClientOffloadPoint, String> invokingThreads = new ConcurrentHashMap<>();
        private final Queue<Throwable> errors = new LinkedBlockingQueue<>();

        ClientInvokingThreadRecorder(ClientApi clientApi, HttpExecutionStrategy streamingAsyncStrategy) {
            if (!streamingAsyncStrategy.hasOffloads()) {
                offloadPoints = EnumSet.noneOf(ClientOffloadPoint.class);
            } else if (defaultStrategy() != streamingAsyncStrategy) {
                offloadPoints = EnumSet.noneOf(ClientOffloadPoint.class);
                // adjust expected offloads for specific execution strategy
                if (streamingAsyncStrategy.isSendOffloaded()) {
                    offloadPoints.add(Send);
                }
                if (streamingAsyncStrategy.isMetadataReceiveOffloaded()) {
                    offloadPoints.add(ReceiveMeta);
                }
                if (streamingAsyncStrategy.isDataReceiveOffloaded()) {
                    offloadPoints.add(ReceiveData);
                }
            } else {
                switch (clientApi) {
                    case BlockingAggregate:
                        offloadPoints = EnumSet.noneOf(ClientOffloadPoint.class);
                        break;
                    case BlockingStreaming:
                        offloadPoints = EnumSet.of(Send);
                        break;
                    case AsyncAggregate:
                        offloadPoints = EnumSet.of(ReceiveData);
                        break;
                    case AsyncStreaming:
                        offloadPoints = EnumSet.allOf(ClientOffloadPoint.class);
                        break;
                    default:
                        throw new AssertionError("unexpected case " + clientApi);
                }
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
                    return delegate.request(request.transformPayloadBody(payload ->
                                    payload.beforeRequest(__ -> recordThread(Send))))
                            .beforeOnSuccess(__ -> recordThread(ReceiveMeta))
                            .map(resp -> resp.transformPayloadBody(payload ->
                                    payload.beforeOnNext(__ -> recordThread(ReceiveData))));
                }
            };
        }

        void recordThread(final ClientOffloadPoint offloadPoint) {
            invokingThreads.compute(offloadPoint, (ClientOffloadPoint offload, String recorded) -> {
                Thread current = Thread.currentThread();
                boolean ioThread = IoThreadFactory.IoThread.isIoThread(current);
                if (offloadPoints.contains(offloadPoint)) {
                    if (ioThread) {
                        errors.add(new AssertionError("Expected offloaded thread at " + offloadPoint));
                    }
                } else {
                    if (!ioThread) {
                        errors.add(new AssertionError("Expected ioThread at " + offloadPoint));
                    }
                }
                return ioThread ? "eventLoop" : "offloaded";
            });
        }

        public void verifyOffloads() {
            assertNoAsyncErrors(errors);
            assertThat("Unexpected offload points recorded. " + invokingThreads,
                    invokingThreads.size(), Matchers.is(ClientOffloadPoint.values().length));
        }
    }

    private static class LoadBalancerFactoryImpl
            implements LoadBalancerFactory<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection> {

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
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.offloadNone();
        }
    }
}
