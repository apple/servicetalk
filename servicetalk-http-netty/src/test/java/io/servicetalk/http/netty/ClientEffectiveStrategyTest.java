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
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.RequestPayloadSubscription;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseData;
import static io.servicetalk.http.netty.ClientEffectiveStrategyTest.ClientOffloadPoint.ResponseMeta;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.defaultUserStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.noStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategy;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

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
        params.add(wrap("userStrategyNoFilter", ClientEffectiveStrategyTest::userStrategyNoFilter));
        params.add(wrap("userStrategyWithFilter", ClientEffectiveStrategyTest::userStrategyWithFilter));
        params.add(wrap("userStrategyNoExecutorNoFilter",
                ClientEffectiveStrategyTest::userStrategyNoExecutorNoFilter));
        params.add(wrap("userStrategyNoExecutorWithFilter",
                ClientEffectiveStrategyTest::userStrategyNoExecutorWithFilter));
        params.add(wrap("customUserStrategyNoFilter",
                ClientEffectiveStrategyTest::customUserStrategyNoFilter));
        params.add(wrap("customUserStrategyWithFilter",
                ClientEffectiveStrategyTest::customUserStrategyWithFilter));
        params.add(wrap("customUserStrategyNoExecutorNoFilter",
                ClientEffectiveStrategyTest::customUserStrategyNoExecutorNoFilter));
        params.add(wrap("customUserStrategyNoExecutorWithFilter",
                ClientEffectiveStrategyTest::customUserStrategyNoExecutorWithFilter));
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
        params.initStateHolderDefaultStrategy(false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params noUserStrategyWithFilter() {
        Params params = new Params();
        params.initStateHolderDefaultStrategy(true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategy(false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyWithFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategy(true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params userStrategyNoExecutorNoFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoExecutor(false);
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyNoExecutorWithFilter() {
        Params params = new Params();
        params.initStateHolderUserStrategyNoExecutor(true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorNoFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategyNoExecutor(false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategyNoExecutor(true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyNoFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategy(false);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    private static Params customUserStrategyWithFilter() {
        Params params = new Params();
        params.initStateHolderCustomUserStrategy(true);
        params.allPointsOffloadedForAllClients();
        return params;
    }

    @Test
    public void blockingClient() throws Exception {
        BlockingHttpClient blockingClient = params.client().asBlockingClient();
        blockingClient.request(blockingClient.get("/"));
        params.verifyOffloads(ClientType.Blocking);
    }

    @Test
    public void blockingStreamingClient() throws Exception {
        BlockingStreamingHttpClient blockingClient = params.client().asBlockingStreamingClient();
        BlockingIterator<Buffer> iter = blockingClient.request(blockingClient.get("/")).payloadBody().iterator();
        iter.forEachRemaining(__ -> { });
        iter.close();
        params.verifyOffloads(ClientType.BlockingStreaming);
    }

    @Test
    public void streamingClient() throws Exception {
        params.client().request(params.client().get("/"))
                .flatMapPublisher(StreamingHttpResponse::payloadBody).toFuture().get();
        params.verifyOffloads(ClientType.AsyncStreaming);
    }

    @Test
    public void client() throws Exception {
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
        private static final String USER_STRATEGY_EXECUTOR_NAME_PREFIX = "user-strategy-executor-";


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

        void defaultOffloadPoints() {
            addNonOffloadedPointFor(ClientType.Blocking, RequestPayloadSubscription, ResponseMeta, ResponseData);

            addNonOffloadedPointFor(ClientType.BlockingStreaming, ResponseMeta, ResponseData);
            addOffloadedPointFor(ClientType.BlockingStreaming, RequestPayloadSubscription);

            addNonOffloadedPointFor(ClientType.Async, RequestPayloadSubscription, ResponseData);
            addOffloadedPointFor(ClientType.Async, ResponseMeta);

            addOffloadedPointFor(ClientType.AsyncStreaming, RequestPayloadSubscription, ResponseMeta, ResponseData);
        }

        void initStateHolderDefaultStrategy(boolean addFilter) {
            executorUsedForStrategy = false;
            invokingThreadsRecorder = noStrategy();
            initState(addFilter);
        }

        void initStateHolderUserStrategy(boolean addFilter) {
            invokingThreadsRecorder = defaultUserStrategy(defaultStrategy(executor));
            initState(addFilter);
        }

        void initStateHolderUserStrategyNoExecutor(boolean addFilter) {
            executorUsedForStrategy = false;
            invokingThreadsRecorder = defaultUserStrategy(defaultStrategy());
            initState(addFilter);
        }

        void initStateHolderCustomUserStrategy(boolean addFilter) {
            verifyStrategyUsed = true;
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadAll().executor(executor).build());
            initState(addFilter);
        }

        void initStateHolderCustomUserStrategyNoExecutor(boolean addFilter) {
            executorUsedForStrategy = false;
            verifyStrategyUsed = true;
            invokingThreadsRecorder = userStrategy(customStrategyBuilder().offloadAll().build());
            initState(addFilter);
        }

        StreamingHttpClient client() {
            assert invokingThreadsRecorder != null;
            return invokingThreadsRecorder.client();
        }

        Executor executor() {
            return executor;
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

        private void initState(final boolean addFilter) {
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
                        clientBuilder.appendClientFilter((c, __) ->
                                new ClientInvokingThreadRecorder(c, invokingThreadsRecorder));
                        if (addFilter) {
                            // Here since we do not override mergeForEffectiveStrategy, it will default to offload-all.
                            clientBuilder.appendClientFilter((client, __) -> new StreamingHttpClientFilter(client));
                        }
                    });
        }
    }

    private static final class ClientInvokingThreadRecorder extends StreamingHttpClientFilter {

        private final InvokingThreadsRecorder<ClientOffloadPoint> holder;

        ClientInvokingThreadRecorder(final StreamingHttpClientFilter delegate,
                                     InvokingThreadsRecorder<ClientOffloadPoint> holder) {
            super(delegate);
            this.holder = requireNonNull(holder);
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            return delegate.request(strategy,
                    request.transformPayloadBody(payload ->
                            payload.doBeforeRequest(__ -> holder.recordThread(RequestPayloadSubscription))))
                    .doBeforeSuccess(__ -> holder.recordThread(ResponseMeta))
                    .map(resp -> resp.transformPayloadBody(payload ->
                            payload.doBeforeNext(__ -> holder.recordThread(ResponseData))));
        }

        @Override
        protected HttpExecutionStrategy mergeForEffectiveStrategy(
                final HttpExecutionStrategy mergeWith) {
            // Don't modify the effective strategy calculation
            return mergeWith;
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
