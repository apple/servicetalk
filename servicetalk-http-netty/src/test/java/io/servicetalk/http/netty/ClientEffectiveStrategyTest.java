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
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.RequestPayloadSubscription;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseData;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseMeta;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.noStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategyNoVerify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

class ClientEffectiveStrategyTest {

    @Nullable
    private Params params;

    @AfterEach
    void tearDown() throws Exception {
        if (params != null) {
            params.dispose();
        }
    }

    enum ClientStrategyCase implements Supplier<Params> {
        noUserStrategyNoFilter(ClientEffectiveStrategyTest::noUserStrategyNoFilter),
        noUserStrategyWithFilter(ClientEffectiveStrategyTest::noUserStrategyWithFilter),
        noUserStrategyWithLB(ClientEffectiveStrategyTest::noUserStrategyWithLB),
        noUserStrategyWithCF(ClientEffectiveStrategyTest::noUserStrategyWithCF),
        userStrategyNoFilter(ClientEffectiveStrategyTest::userStrategyNoFilter),
        userStrategyWithFilter(ClientEffectiveStrategyTest::userStrategyWithFilter),
        userStrategyWithLB(ClientEffectiveStrategyTest::userStrategyWithLB),
        userStrategyWithCF(ClientEffectiveStrategyTest::userStrategyWithCF),
        userStrategyNoExecutorNoFilter(ClientEffectiveStrategyTest::userStrategyNoExecutorNoFilter),
        userStrategyNoExecutorWithFilter(ClientEffectiveStrategyTest::userStrategyNoExecutorWithFilter),
        userStrategyNoExecutorWithLB(ClientEffectiveStrategyTest::userStrategyNoExecutorWithLB),
        userStrategyNoExecutorWithCF(ClientEffectiveStrategyTest::userStrategyNoExecutorWithCF),
        userStrategyNoOffloadsNoFilter(ClientEffectiveStrategyTest::userStrategyNoOffloadsNoFilter),
        userStrategyNoOffloadsWithFilter(ClientEffectiveStrategyTest::userStrategyNoOffloadsWithFilter),
        userStrategyNoOffloadsWithLB(ClientEffectiveStrategyTest::userStrategyNoOffloadsWithLB),
        userStrategyNoOffloadsWithCF(ClientEffectiveStrategyTest::userStrategyNoOffloadsWithCF),
        userStrategyNoOffloadsNoExecutorNoFilter(
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorNoFilter),
        userStrategyNoOffloadsNoExecutorWithFilter(
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithFilter),
        userStrategyNoOffloadsNoExecutorWithLB(ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithLB),
        userStrategyNoOffloadsNoExecutorWithCF(ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithCF),
        customUserStrategyNoFilter(ClientEffectiveStrategyTest::customUserStrategyNoFilter),
        customUserStrategyWithFilter(ClientEffectiveStrategyTest::customUserStrategyWithFilter),
        customUserStrategyWithLB(ClientEffectiveStrategyTest::customUserStrategyWithLB),
        customUserStrategyWithCF(ClientEffectiveStrategyTest::customUserStrategyWithCF),
        customUserStrategyNoExecutorNoFilter(ClientEffectiveStrategyTest::customUserStrategyNoExecutorNoFilter),
        customUserStrategyNoExecutorWithFilter(ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithFilter),
        customUserStrategyNoExecutorWithLB(ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithLB),
        customUserStrategyNoExecutorWithCF(ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithCF);

        private final Supplier<Params> paramsSupplier;

        ClientStrategyCase(Supplier<Params> paramsSupplier) {
            this.paramsSupplier = paramsSupplier;
        }

        @Override
        public Params get() {
            return paramsSupplier.get();
        }
    }

    private static Params noUserStrategyNoFilter() {
        Params params = new Params(false, false);
        params.initStateHolderDefaultStrategy(false, false, false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params noUserStrategyWithFilter() {
        Params params = new Params(false, false);
        params.initStateHolderDefaultStrategy(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params noUserStrategyWithLB() {
        Params params = new Params(false, false);
        params.initStateHolderDefaultStrategy(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params noUserStrategyWithCF() {
        Params params = new Params(false, false);
        params.initStateHolderDefaultStrategy(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoFilter() {
        Params params = new Params(true, false);
        params.initStateHolderUserStrategy(false, false, false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyWithFilter() {
        Params params = new Params(true, false);
        params.initStateHolderUserStrategy(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyWithLB() {
        Params params = new Params(true, false);
        params.initStateHolderUserStrategy(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyWithCF() {
        Params params = new Params(true, false);
        params.initStateHolderUserStrategy(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorNoFilter() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoExecutor(false, false, false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyNoExecutorWithFilter() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoExecutor(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorWithLB() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoExecutor(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorWithCF() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoExecutor(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoFilter() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloads(false, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsWithFilter() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloads(true, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsWithLB() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloads(false, true, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsWithCF() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloads(false, false, true);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorNoFilter() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(false, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithFilter() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(true, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithLB() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(false, true, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithCF() {
        Params params = new Params(false, false);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(false, false, true);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorNoFilter() {
        Params params = new Params(false, true);
        params.initStateHolderCustomUserStrategyNoExecutor(false, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithFilter() {
        Params params = new Params(false, true);
        params.initStateHolderCustomUserStrategyNoExecutor(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithLB() {
        Params params = new Params(false, true);
        params.initStateHolderCustomUserStrategyNoExecutor(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithCF() {
        Params params = new Params(false, true);
        params.initStateHolderCustomUserStrategyNoExecutor(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoFilter() {
        Params params = new Params(true, true);
        params.initStateHolderCustomUserStrategy(false, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithFilter() {
        Params params = new Params(true, true);
        params.initStateHolderCustomUserStrategy(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithLB() {
        Params params = new Params(true, true);
        params.initStateHolderCustomUserStrategy(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithCF() {
        Params params = new Params(true, true);
        params.initStateHolderCustomUserStrategy(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void blocking(ClientStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        BlockingHttpClient blockingClient = params.client().asBlockingClient();
        blockingClient.request(blockingClient.get("/"));
        params.verifyOffloads(ClientType.Blocking);
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void blockingStreaming(ClientStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        BlockingStreamingHttpClient blockingClient = params.client().asBlockingStreamingClient();
        BlockingIterator<Buffer> iter = blockingClient.request(blockingClient.get("/")).payloadBody().iterator();
        iter.forEachRemaining(__ -> { });
        iter.close();
        params.verifyOffloads(ClientType.BlockingStreaming);
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void streaming(ClientStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        params.client().request(params.client().get("/"))
                .flatMapPublisher(StreamingHttpResponse::payloadBody).toFuture().get();
        params.verifyOffloads(ClientType.AsyncStreaming);
    }

    @ParameterizedTest
    @EnumSource(ClientStrategyCase.class)
    void async(ClientStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        HttpClient httpClient = params.client().asClient();
        httpClient.request(httpClient.get("/")).toFuture().get();
        params.verifyOffloads(ClientType.Async);
    }

    private static final class Params {
        private static final String USER_STRATEGY_EXECUTOR_NAME_PREFIX = "user-strategy-executor";

        private final EnumMap<ClientType, EnumSet<ClientOffloadPoint>> offloadPoints =
                new EnumMap<>(ClientType.class);
        private final EnumMap<ClientType, EnumSet<ClientOffloadPoint>> nonOffloadPoints =
                new EnumMap<>(ClientType.class);
        private final Executor executor;
        @Nullable
        private InvokingThreadsRecorder<ClientOffloadPoint> invokingThreadsRecorder;
        private final boolean executorUsedForStrategy;
        private final boolean verifyStrategyUsed;

        Params(boolean executorUsedForStrategy, boolean verifyStrategyUsed) {
            for (ClientType clientType : ClientType.values()) {
                offloadPoints.put(clientType, EnumSet.noneOf(ClientOffloadPoint.class));
            }
            for (ClientType clientType : ClientType.values()) {
                nonOffloadPoints.put(clientType, EnumSet.noneOf(ClientOffloadPoint.class));
            }
            this.executorUsedForStrategy = executorUsedForStrategy;
            this.executor = executorUsedForStrategy ?
                    newCachedThreadExecutor(new DefaultThreadFactory(USER_STRATEGY_EXECUTOR_NAME_PREFIX)) :
                    Executors.from(r -> fail("This executor was not to be used"));
            this.verifyStrategyUsed = verifyStrategyUsed;
        }

        void addOffloadedPointFor(ClientType clientType, ClientOffloadPoint... points) {
            EnumSet<ClientOffloadPoint> offloads = offloadPoints.get(clientType);
            Collections.addAll(offloads, points);
        }

        void addNonOffloadedPointFor(ClientType clientType, ClientOffloadPoint... points) {
            EnumSet<ClientOffloadPoint> nonOffloads = nonOffloadPoints.get(clientType);
            Collections.addAll(nonOffloads, points);
        }

        void allPointsOffloadedForAllClients() {
            EnumSet<ClientOffloadPoint> all = EnumSet.allOf(ClientOffloadPoint.class);
            for (ClientType clientType : ClientType.values()) {
                offloadPoints.get(clientType).addAll(all);
            }
        }

        void noPointsOffloadedForAllClients() {
            EnumSet<ClientOffloadPoint> all = EnumSet.allOf(ClientOffloadPoint.class);
            for (ClientType clientType : ClientType.values()) {
                nonOffloadPoints.get(clientType).addAll(all);
            }
        }

        void defaultOffloadPoints() {
            addNonOffloadedPointFor(ClientType.Blocking, RequestPayloadSubscription, ResponseMeta, ResponseData);

            addNonOffloadedPointFor(ClientType.BlockingStreaming, ResponseMeta, ResponseData);
            addOffloadedPointFor(ClientType.BlockingStreaming, RequestPayloadSubscription);

            addNonOffloadedPointFor(ClientType.Async, RequestPayloadSubscription, ResponseMeta);
            addOffloadedPointFor(ClientType.Async, ResponseData);

            addOffloadedPointFor(ClientType.AsyncStreaming, RequestPayloadSubscription, ResponseMeta, ResponseData);
        }

        void initStateHolderDefaultStrategy(boolean addFilter, boolean addLoadBalancer,
                                            boolean addConnectionFilter) {
            invokingThreadsRecorder = noStrategy();
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderUserStrategy(boolean addFilter, boolean addLoadBalancer,
                                         boolean addConnectionFilter) {
            invokingThreadsRecorder = userStrategyNoVerify(defaultStrategy(executor));
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderUserStrategyNoExecutor(boolean addFilter, boolean addLoadBalancer,
                                                   boolean addConnectionFilter) {
            invokingThreadsRecorder = userStrategyNoVerify(defaultStrategy());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderUserStrategyNoOffloads(boolean addFilter, boolean addLoadBalancer,
                                                   boolean addConnectionFilter) {
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadNone().executor(immediate()).build());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderUserStrategyNoOffloadsNoExecutor(boolean addFilter, boolean addLoadBalancer,
                                                             boolean addConnectionFilter) {
            invokingThreadsRecorder = userStrategy(noOffloadsStrategy());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderCustomUserStrategy(boolean addFilter, boolean addLoadBalancer,
                                               boolean addConnectionFilter) {
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadAll().executor(executor).build());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderCustomUserStrategyNoExecutor(boolean addFilter, boolean addLoadBalancer,
                                                         boolean addConnectionFilter) {
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadAll().build());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        StreamingHttpClient client() {
            assert invokingThreadsRecorder != null;
            return invokingThreadsRecorder.client();
        }

        void verifyOffloads(final ClientType clientType) {
            assert invokingThreadsRecorder != null;
            if (verifyStrategyUsed) {
                invokingThreadsRecorder.assertStrategyUsedForClient();
            }
            invokingThreadsRecorder.verifyOffloadCount();
            for (ClientOffloadPoint offloadPoint : offloadPoints.get(clientType)) {
                if (executorUsedForStrategy) {
                    invokingThreadsRecorder.assertOffload(offloadPoint, USER_STRATEGY_EXECUTOR_NAME_PREFIX);
                } else {
                    invokingThreadsRecorder.assertOffload(offloadPoint);
                }
            }
            for (ClientOffloadPoint offloadPoint : nonOffloadPoints.get(clientType)) {
                invokingThreadsRecorder.assertNoOffload(offloadPoint);
            }
        }

        void dispose() throws Exception {
            assert invokingThreadsRecorder != null;
            try {
                invokingThreadsRecorder.dispose();
            } finally {
                executor.closeAsync().toFuture().get();
            }
        }

        private void initState(final boolean addFilter, boolean addLoadBalancer, boolean addConnectionFilter) {
            assert invokingThreadsRecorder != null;
            HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            invokingThreadsRecorder.init((__, serverBuilder) ->
                            serverBuilder.listenBlocking((ctx, request, responseFactory) ->
                                    responseFactory.ok().payloadBody(ctx.executionContext().bufferAllocator()
                                            .fromAscii("Hello"))),
                    (ioExecutor, clientBuilder) -> {
                        if (strategy != null) {
                            clientBuilder.executionStrategy(strategy);
                        }
                        clientBuilder.ioExecutor(ioExecutor);
                        clientBuilder.appendClientFilter(new ClientInvokingThreadRecorder(invokingThreadsRecorder));
                        if (addConnectionFilter) {
                            clientBuilder.appendConnectionFilter(connection ->
                                    new StreamingHttpConnectionFilter(connection) { });
                        }
                        if (addLoadBalancer) {
                            clientBuilder.loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder
                                    .from(new LoadBalancerFactoryImpl()).build());
                        }
                        if (addFilter) {
                            // Here since we do not override mergeForEffectiveStrategy, it will default to offload-all.
                            clientBuilder.appendClientFilter(client -> new StreamingHttpClientFilter(client) { });
                        }
                    });
        }
    }

    private static final class ClientInvokingThreadRecorder
            implements StreamingHttpClientFilterFactory, HttpExecutionStrategyInfluencer {

        private final InvokingThreadsRecorder<ClientOffloadPoint> holder;

        ClientInvokingThreadRecorder(final InvokingThreadsRecorder<ClientOffloadPoint> holder) {
            this.holder = holder;
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            // Don't influence strategy
            return strategy;
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {

                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return delegate.request(strategy,
                            request.transformPayloadBody(payload ->
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
        public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T>
        newLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<InetSocketAddress>> eventPublisher,
                        final ConnectionFactory<InetSocketAddress, T> connectionFactory) {
            return RoundRobinLoadBalancer.<InetSocketAddress,
                    FilterableStreamingHttpLoadBalancedConnection>newRoundRobinFactory()
                    .newLoadBalancer(eventPublisher, connectionFactory);
        }
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
