/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import static java.util.Arrays.asList;

@RunWith(Parameterized.class)
public class ClientEffectiveStrategyTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ParamsSupplier paramSupplier;
    private Params params;

    public ClientEffectiveStrategyTest(final ParamsSupplier paramSupplier) {
        this.paramSupplier = paramSupplier;
    }

    @Before
    public void setUp() throws Exception {
        params = paramSupplier.newParams();
    }

    @After
    public void tearDown() throws Exception {
        params.dispose();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<ParamsSupplier> params() {
        List<ParamsSupplier> params = new ArrayList<>();
        params.add(wrap("noUserStrategyNoFilter", ClientEffectiveStrategyTest::noUserStrategyNoFilter));
        params.add(wrap("noUserStrategyWithFilter", ClientEffectiveStrategyTest::noUserStrategyWithFilter));
        params.add(wrap("noUserStrategyWithLB", ClientEffectiveStrategyTest::noUserStrategyWithLB));
        params.add(wrap("noUserStrategyWithCF", ClientEffectiveStrategyTest::noUserStrategyWithCF));
        params.add(wrap("userStrategyNoFilter", ClientEffectiveStrategyTest::userStrategyNoFilter));
        params.add(wrap("userStrategyWithFilter", ClientEffectiveStrategyTest::userStrategyWithFilter));
        params.add(wrap("userStrategyWithLB", ClientEffectiveStrategyTest::userStrategyWithLB));
        params.add(wrap("userStrategyWithCF", ClientEffectiveStrategyTest::userStrategyWithCF));
        params.add(wrap("userStrategyNoExecutorNoFilter",
                ClientEffectiveStrategyTest::userStrategyNoExecutorNoFilter));
        params.add(wrap("userStrategyNoExecutorWithFilter",
                ClientEffectiveStrategyTest::userStrategyNoExecutorWithFilter));
        params.add(wrap("userStrategyNoExecutorWithLB",
                ClientEffectiveStrategyTest::userStrategyNoExecutorWithLB));
        params.add(wrap("userStrategyNoExecutorWithCF",
                ClientEffectiveStrategyTest::userStrategyNoExecutorWithCF));
        params.add(wrap("userStrategyNoOffloadsNoFilter",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoFilter));
        params.add(wrap("userStrategyNoOffloadsWithFilter",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsWithFilter));
        params.add(wrap("userStrategyNoOffloadsWithLB",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsWithLB));
        params.add(wrap("userStrategyNoOffloadsWithCF",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsWithCF));
        params.add(wrap("userStrategyNoOffloadsNoExecutorNoFilter",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorNoFilter));
        params.add(wrap("userStrategyNoOffloadsNoExecutorWithFilter",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithFilter));
        params.add(wrap("userStrategyNoOffloadsNoExecutorWithLB",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithLB));
        params.add(wrap("userStrategyNoOffloadsNoExecutorWithCF",
                ClientEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithCF));
        params.add(wrap("customUserStrategyNoFilter",
                ClientEffectiveStrategyTest::customUserStrategyNoFilter));
        params.add(wrap("customUserStrategyWithFilter",
                ClientEffectiveStrategyTest::customUserStrategyWithFilter));
        params.add(wrap("customUserStrategyWithLB",
                ClientEffectiveStrategyTest::customUserStrategyWithLB));
        params.add(wrap("customUserStrategyWithCF",
                ClientEffectiveStrategyTest::customUserStrategyWithCF));
        params.add(wrap("customUserStrategyNoExecutorNoFilter",
                ClientEffectiveStrategyTest::customUserStrategyNoExecutorNoFilter));
        params.add(wrap("customUserStrategyNoExecutorWithFilter",
                ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithFilter));
        params.add(wrap("customUserStrategyNoExecutorWithLB",
                ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithLB));
        params.add(wrap("customUserStrategyNoExecutorWithCF",
                ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithCF));
        return params;
    }

    static ParamsSupplier wrap(String name, Supplier<Params> supplier) {
        return new ParamsSupplier(name) {
            @Override
            Params newParams() {
                return supplier.get();
            }
        };
    }

    private static Params noUserStrategyNoFilter() {
        Params params = new Params();
        params.initStateHolderDefaultStrategy(false, false, false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params noUserStrategyWithFilter() {
        Params params = new Params();
        params.initStateHolderDefaultStrategy(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params noUserStrategyWithLB() {
        Params params = new Params();
        params.initStateHolderDefaultStrategy(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params noUserStrategyWithCF() {
        Params params = new Params();
        params.initStateHolderDefaultStrategy(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategy(false, false, false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyWithFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategy(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyWithLB() {
        Params params = new Params();
        params.initStateHolderUserStrategy(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyWithCF() {
        Params params = new Params();
        params.initStateHolderUserStrategy(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorNoFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoExecutor(false, false, false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyNoExecutorWithFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoExecutor(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorWithLB() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoExecutor(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorWithCF() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoExecutor(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloads(false, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsWithFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloads(true, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsWithLB() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloads(false, true, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsWithCF() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloads(false, false, true);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorNoFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(false, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(true, false, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithLB() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(false, true, false);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithCF() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoOffloadsNoExecutor(false, false, true);
        params.noPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorNoFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategyNoExecutor(false, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategyNoExecutor(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithLB() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategyNoExecutor(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithCF() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategyNoExecutor(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategy(false, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategy(true, false, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithLB() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategy(false, true, false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithCF() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategy(false, false, true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    @Test
    public void blocking() throws Exception {
        BlockingHttpClient blockingClient = params.client().asBlockingClient();
        blockingClient.request(blockingClient.get("/"));
        params.verifyOffloads(ClientType.Blocking);
    }

    @Test
    public void blockingStreaming() throws Exception {
        BlockingStreamingHttpClient blockingClient = params.client().asBlockingStreamingClient();
        BlockingIterator<Buffer> iter = blockingClient.request(blockingClient.get("/")).payloadBody().iterator();
        iter.forEachRemaining(__ -> { });
        iter.close();
        params.verifyOffloads(ClientType.BlockingStreaming);
    }

    @Test
    public void streaming() throws Exception {
        params.client().request(params.client().get("/"))
                .flatMapPublisher(StreamingHttpResponse::payloadBody).toFuture().get();
        params.verifyOffloads(ClientType.AsyncStreaming);
    }

    @Test
    public void async() throws Exception {
        HttpClient httpClient = params.client().asClient();
        httpClient.request(httpClient.get("/")).toFuture().get();
        params.verifyOffloads(ClientType.Async);
    }

    private abstract static class ParamsSupplier {
        private final String name;

        ParamsSupplier(final String name) {
            this.name = name;
        }

        abstract Params newParams() throws Exception;

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class Params {
        private static final String USER_STRATEGY_EXECUTOR_NAME_PREFIX = "user-strategy-executor";


        private final Map<ClientType, List<ClientOffloadPoint>> offloadPoints;
        private final Map<ClientType, List<ClientOffloadPoint>> nonOffloadPoints;
        private final Executor executor;
        @Nullable
        private InvokingThreadsRecorder<ClientOffloadPoint> invokingThreadsRecorder;
        private boolean executorUsedForStrategy = true;
        private boolean verifyStrategyUsed;

        Params() {
            this.executor = newCachedThreadExecutor(new DefaultThreadFactory(USER_STRATEGY_EXECUTOR_NAME_PREFIX));
            offloadPoints = new EnumMap<>(ClientType.class);
            for (ClientType clientType : ClientType.values()) {
                offloadPoints.put(clientType, new ArrayList<>());
            }
            nonOffloadPoints = new EnumMap<>(ClientType.class);
            for (ClientType clientType : ClientType.values()) {
                nonOffloadPoints.put(clientType, new ArrayList<>());
            }
        }

        void addOffloadedPointFor(ClientType clientType, ClientOffloadPoint... points) {
            offloadPoints.get(clientType).addAll(asList(points));
        }

        void addNonOffloadedPointFor(ClientType clientType, ClientOffloadPoint... points) {
            nonOffloadPoints.get(clientType).addAll(asList(points));
        }

        void allPointsOffloadedForAllClients() {
            for (ClientType clientType : ClientType.values()) {
                offloadPoints.get(clientType).addAll(asList(ResponseData,
                        ResponseMeta, RequestPayloadSubscription));
            }
        }

        void noPointsOffloadedForAllClients() {
            for (ClientType clientType : ClientType.values()) {
                nonOffloadPoints.get(clientType).addAll(asList(ResponseData,
                        ResponseMeta, RequestPayloadSubscription));
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
            executorUsedForStrategy = false;
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
            executorUsedForStrategy = false;
            invokingThreadsRecorder = userStrategyNoVerify(defaultStrategy());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderUserStrategyNoOffloads(boolean addFilter, boolean addLoadBalancer,
                                                   boolean addConnectionFilter) {
            executorUsedForStrategy = false;
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadNone().executor(immediate()).build());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderUserStrategyNoOffloadsNoExecutor(boolean addFilter, boolean addLoadBalancer,
                                                             boolean addConnectionFilter) {
            executorUsedForStrategy = false;
            invokingThreadsRecorder = userStrategy(noOffloadsStrategy());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderCustomUserStrategy(boolean addFilter, boolean addLoadBalancer,
                                               boolean addConnectionFilter) {
            verifyStrategyUsed = true;
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadAll().executor(executor).build());
            initState(addFilter, addLoadBalancer, addConnectionFilter);
        }

        void initStateHolderCustomUserStrategyNoExecutor(boolean addFilter, boolean addLoadBalancer,
                                                         boolean addConnectionFilter) {
            executorUsedForStrategy = false;
            verifyStrategyUsed = true;
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
