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
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
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
import io.servicetalk.http.api.HttpResponse;
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.RequestPayloadSubscription;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseData;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseMeta;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategyNoVerify;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

class ClientEffectiveStrategyTest {

    private static final String GREETING = "Hello";

    enum ClientStrategyCase implements Function<ClientType, Params> {
        noUserStrategyNoFilter(clientType -> new Params(clientType, Offloads.DEFAULT,
                false, false, false, false,
                InvokingThreadsRecorder::noStrategy)),
        noUserStrategyWithFilter(clientType -> new Params(clientType, Offloads.ALL,
                false, true, false, false,
                InvokingThreadsRecorder::noStrategy)),
        noUserStrategyWithLB(clientType -> new Params(clientType, Offloads.DEFAULT,
                false, false, true, false,
                InvokingThreadsRecorder::noStrategy)),
        noUserStrategyWithCF(clientType -> new Params(clientType, Offloads.ALL,
                false, false, false, true,
                InvokingThreadsRecorder::noStrategy)),
        userStrategyNoFilter(clientType -> new Params(clientType, Offloads.DEFAULT,
                true, false, false, false,
                () -> userStrategyNoVerify(defaultStrategy()))),
        userStrategyWithFilter(clientType -> new Params(clientType, Offloads.ALL,
                true, true, false, false,
                () -> userStrategyNoVerify(defaultStrategy()))),
        userStrategyWithLB(clientType -> new Params(clientType, Offloads.DEFAULT,
                true, false, true, false,
                () -> userStrategyNoVerify(defaultStrategy()))),
        userStrategyWithCF(clientType -> new Params(clientType, Offloads.ALL,
                true, false, false, true,
                () -> userStrategyNoVerify(defaultStrategy()))),
        userStrategyNoOffloadsNoFilter(clientType -> new Params(clientType, Offloads.NONE,
                false, false, false, false,
                () -> userStrategy(noOffloadsStrategy()))),
        userStrategyNoOffloadsWithFilter(clientType -> new Params(clientType, Offloads.NONE,
                false, true, false, false,
                () -> userStrategy(noOffloadsStrategy()))),
        userStrategyNoOffloadsWithLB(clientType -> new Params(clientType, Offloads.NONE,
                false, false, true, false,
                () -> userStrategy(noOffloadsStrategy()))),
        userStrategyNoOffloadsWithCF(clientType -> new Params(clientType, Offloads.NONE,
                false, false, false, true,
                () -> userStrategy(noOffloadsStrategy()))),
        customUserStrategyNoFilter(clientType -> new Params(clientType, Offloads.ALL,
                true, false, false, false,
                () -> userStrategy(customStrategyBuilder().offloadAll().build()))),
        customUserStrategyWithFilter(clientType -> new Params(clientType, Offloads.ALL,
                true, true, false, false,
                () -> userStrategy(customStrategyBuilder().offloadAll().build()))),
        customUserStrategyWithLB(clientType -> new Params(clientType, Offloads.ALL,
                true, false, true, false,
                () -> userStrategy(customStrategyBuilder().offloadAll().build()))),
        customUserStrategyWithCF(clientType -> new Params(clientType, Offloads.ALL,
                true, false, false, true,
                () -> userStrategy(customStrategyBuilder().offloadAll().build())));

        private final Function<ClientType, Params> paramsProvider;

        ClientStrategyCase(Function<ClientType, Params> paramsProvider) {
            this.paramsProvider = paramsProvider;
        }

        @Override
        public Params apply(ClientType clientType) {
            return paramsProvider.apply(clientType);
        }
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void blocking(ClientStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ClientType.Blocking)) {
            assertThat("Null params supplied", params, notNullValue());
            BlockingHttpClient blockingClient = params.client().asBlockingClient();
            HttpResponse response = blockingClient.request(blockingClient.get("/"));
            assertThat(response.payloadBody().toString(StandardCharsets.US_ASCII), is(GREETING));
            params.verifyOffloads();
        }
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void blockingStreaming(ClientStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ClientType.BlockingStreaming)) {
            assertThat("Null params supplied", params, notNullValue());
            BlockingStreamingHttpClient blockingClient = params.client().asBlockingStreamingClient();
            String response = buffersToResponse(blockingClient.request(blockingClient.get("/")).payloadBody(),
                    StandardCharsets.US_ASCII);
            assertThat(response, is(GREETING));
            params.verifyOffloads();
        }
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void streaming(ClientStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ClientType.AsyncStreaming)) {
            assertThat("Null params supplied", params, notNullValue());
            Collection<Buffer> buffers = params.client().request(params.client().get("/"))
                    .flatMapPublisher(StreamingHttpResponse::payloadBody).toFuture().get();
            String response = buffersToResponse(buffers, StandardCharsets.US_ASCII);
            assertThat(response, is(GREETING));
            params.verifyOffloads();
        }
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void async(ClientStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ClientType.Async)) {
            assertThat("Null params supplied", params, notNullValue());
            HttpClient httpClient = params.client().asClient();
            HttpResponse response = httpClient.request(httpClient.get("/")).toFuture().get();
            assertThat(response.payloadBody().toString(StandardCharsets.US_ASCII), is(GREETING));
            params.verifyOffloads();
        }
    }

    private static String buffersToResponse(Iterable<? extends Buffer> buffers, Charset charset) {
        return StreamSupport.stream(buffers.spliterator(), false).map(buffer -> {
                    byte[] bytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(bytes);
                    return bytes;
                }).reduce((orig, more) -> {
                    byte[] combined = Arrays.copyOf(orig, orig.length + more.length);
                    System.arraycopy(more, 0, combined, orig.length, more.length);
                    return combined;
                }).map(array -> new String(array, charset))
                .orElseThrow(() -> new AssertionError("No response"));
    }

    private static final class Params implements AutoCloseable {
        private static final String USER_STRATEGY_EXECUTOR_NAME_PREFIX = "user-strategy-executor";

        private final EnumSet<ClientOffloadPoint> offloadPoints;
        private final EnumSet<ClientOffloadPoint> nonOffloadPoints;
        private final Executor executor;
        private final InvokingThreadsRecorder<ClientOffloadPoint> invokingThreadsRecorder;
        private final boolean executorUsedForContext;

        /**
         * Create parameter instance for text case.
         *  @param clientType intended usage of the client
         * @param expectedOffloads which offloads are expected
         * @param executorUsedForContext if true then install an executor in the client execution context
         * @param addFilter Add a client filter that requires offloading
         * @param addLoadBalancer Add a load balancer
         * @param addConnectionFilter Add a connection filter
         * @param recorderSupplier Supplier for threads recorder
         */
        Params(final ClientType clientType, final Offloads expectedOffloads,
               final boolean executorUsedForContext,
               final boolean addFilter, final boolean addLoadBalancer, final boolean addConnectionFilter,
               final Supplier<InvokingThreadsRecorder<ClientOffloadPoint>> recorderSupplier) {
            this.executorUsedForContext = executorUsedForContext;
            this.executor = executorUsedForContext ?
                    newCachedThreadExecutor(new DefaultThreadFactory(USER_STRATEGY_EXECUTOR_NAME_PREFIX)) :
                    Executors.from(r -> fail("This executor was not to be used"));
            offloadPoints = expectedOffloads.expected(clientType);
            nonOffloadPoints = EnumSet.complementOf(offloadPoints);
            invokingThreadsRecorder = Objects.requireNonNull(recorderSupplier.get(), "recorderSupplier");
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        StreamingHttpClient client() {
            return invokingThreadsRecorder.client();
        }

        void verifyOffloads() {
            invokingThreadsRecorder.verifyOffloadCount();
            for (ClientOffloadPoint offloadPoint : offloadPoints) {
                invokingThreadsRecorder.assertOffload(offloadPoint);
            }
            for (ClientOffloadPoint offloadPoint : nonOffloadPoints) {
                invokingThreadsRecorder.assertNoOffload(offloadPoint);
            }
        }

        @Override
        public void close() throws Exception {
            invokingThreadsRecorder.close();
        }

        private void initState(final boolean addFilter, boolean addLoadBalancer, boolean addConnectionFilter) {
            HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            invokingThreadsRecorder.init((__, serverBuilder) ->
                            serverBuilder.listenBlocking((ctx, request, responseFactory) ->
                                    responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator()
                                            .fromAscii(GREETING))),
                    (ioExecutor, clientBuilder) -> {
                        if (strategy != null) {
                            clientBuilder.executionStrategy(strategy);
                        }
                        if (executorUsedForContext) {
                            clientBuilder.executor(executor);
                        }
                        clientBuilder.ioExecutor(ioExecutor);
                        clientBuilder.appendClientFilter(new ClientInvokingThreadRecorder(invokingThreadsRecorder));
                        if (addConnectionFilter) {
                            clientBuilder.appendConnectionFilter(new StreamingHttpConnectionFilterFactory() {
                                @Override
                                public StreamingHttpConnectionFilter create(
                                        final FilterableStreamingHttpConnection connection) {
                                    return new StreamingHttpConnectionFilter(connection) { };
                                }

                                @Override
                                public HttpExecutionStrategy requiredOffloads() {
                                    // require full offloading
                                    return offloadAll();
                                }
                            });
                        }
                        if (addLoadBalancer) {
                            clientBuilder.loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder
                                    .from(new LoadBalancerFactoryImpl()).build());
                        }
                        if (addFilter) {
                            // Our filter factory does not implement influenceStrategy, it will default to offload-all.
                            clientBuilder.appendClientFilter(new StreamingHttpClientFilterFactory() {
                                @Override
                                public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
                                    return new StreamingHttpClientFilter(client) { };
                                }

                                @Override
                                public HttpExecutionStrategy requiredOffloads() {
                                    // require full offloading
                                    return offloadAll();
                                }
                            });
                        }
                    });
        }
    }

    private static final class ClientInvokingThreadRecorder
            implements StreamingHttpClientFilterFactory {

        private final InvokingThreadsRecorder<ClientOffloadPoint> holder;

        ClientInvokingThreadRecorder(final InvokingThreadsRecorder<ClientOffloadPoint> holder) {
            this.holder = holder;
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // No influence since we do not block.
            return HttpExecutionStrategies.anyStrategy();
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    return delegate.request(request.transformPayloadBody(payload ->
                                    payload.beforeRequest(__ -> holder.recordThread(RequestPayloadSubscription))))
                            .beforeOnSuccess(__ -> holder.recordThread(ResponseMeta))
                            .map(resp -> resp.transformPayloadBody(payload ->
                                    payload.beforeOnNext(__ -> holder.recordThread(ResponseData))));
                }
            };
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
            return ExecutionStrategy.anyStrategy();
        }
    }

    private enum Offloads {
        NONE() {
            @Override
            EnumSet<ClientOffloadPoint> expected(ClientType clientType) {
                return EnumSet.noneOf(ClientOffloadPoint.class);
            }
        },
        DEFAULT() {
            @Override
            EnumSet<ClientOffloadPoint> expected(ClientType clientType) {
                switch (clientType) {
                    case Blocking:
                        return EnumSet.noneOf(ClientOffloadPoint.class);
                    case BlockingStreaming:
                        return EnumSet.of(RequestPayloadSubscription);
                    case Async:
                        return EnumSet.of(ResponseData);
                    case AsyncStreaming:
                        return EnumSet.allOf(ClientOffloadPoint.class);
                    default:
                        throw new IllegalStateException("unexpected case " + clientType);
                }
            }
        },
        ALL() {
            @Override
            EnumSet<ClientOffloadPoint> expected(ClientType clientType) {
                return EnumSet.allOf(ClientOffloadPoint.class);
            }
        };

        abstract EnumSet<ClientOffloadPoint> expected(ClientType clientType);
    }

    private enum ClientType {
        AsyncStreaming,
        BlockingStreaming,
        Blocking,
        Async
    }

    enum ClientOffloadPoint {
        RequestPayloadSubscription,
        ResponseMeta,
        ResponseData
    }
}
