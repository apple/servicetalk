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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.NORM_PRIORITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

public class PredicateRouterOffloadingTest {
    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";
    private static final String EXECUTOR_NAME_PREFIX = "router-executor";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutionContextRule executionContextRule = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY)),
            () -> newCachedThreadExecutor(new DefaultThreadFactory(EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY)));
    @Nullable
    protected ServerContext context;
    @Nullable
    private BlockingHttpClient client;
    protected ConcurrentMap<RouterOffloadPoint, Thread> invokingThreads;
    protected HttpServerBuilder builder;

    @Before
    public void setUp() {
        builder = HttpServers.forAddress(localAddress(0)).ioExecutor(executionContextRule.ioExecutor());
        invokingThreads = new ConcurrentHashMap<>();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        CompositeCloseable closeable = newCompositeCloseable();
        if (context != null) {
            closeable.append(context);
        }
        closeable.closeAsync().toFuture().get();
    }

    @Test
    public void predicateAndRouteAreOffloaded() throws Exception {
        HttpPredicateRouterBuilder routerBuilder = newRouteBuilder();
        routerBuilder.when(newPredicate()).thenRouteTo(newOffloadingService());
        BlockingHttpClient client = buildServer(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertPredicateOffloaded();
        assertRouteOffloaded();
    }

    @Test
    public void predicateOffloadedAndNotRoute() throws Exception {
        HttpPredicateRouterBuilder routerBuilder = newRouteBuilder();
        routerBuilder.when(newPredicate()).thenRouteTo(newNonOffloadingService());
        BlockingHttpClient client = buildServer(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertPredicateOffloadedAndNotRoute();
    }

    @Test
    public void routeOffloadedAndNotPredicate() throws Exception {
        HttpPredicateRouterBuilder routerBuilder = newRouteBuilder();
        routerBuilder.executionStrategy(noOffloadsStrategy());
        routerBuilder.when(newPredicate()).thenRouteTo(newOffloadingService());
        BlockingHttpClient client = buildServer(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteOffloadedAndNotPredicate();
    }

    @Test
    public void noOffloads() throws Exception {
        HttpPredicateRouterBuilder routerBuilder = newRouteBuilder();
        routerBuilder.executionStrategy(noOffloadsStrategy());
        routerBuilder.when(newPredicate()).thenRouteTo(newNonOffloadingService());
        BlockingHttpClient client = buildServer(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteAndPredicateNotOffloaded();
    }

    private HttpPredicateRouterBuilder newRouteBuilder() {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        routerBuilder.executionStrategy(defaultStrategy(executionContextRule.executor()));
        return routerBuilder;
    }

    private BlockingHttpClient buildServer(StreamingHttpService service) throws Exception {
        this.context = builder.listenStreamingAndAwait(service);
        client = HttpClients.forSingleAddress(serverHostAndPort(context))
                .buildBlocking();
        return client;
    }

    private void assertPredicateNotOffloaded() {
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Predicate,
                invokingThreads.get(RouterOffloadPoint.Predicate).getName(), startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    private void assertPredicateOffloadedAndNotRoute() {
        assertPredicateOffloaded();
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Route).getName(),
                // Since predicate is offloaded, route will be called on the predicate thread.
                startsWith(EXECUTOR_NAME_PREFIX));
    }

    private void assertRouteOffloadedAndNotPredicate() {
        assertPredicateNotOffloaded();
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Route).getName(),
                startsWith(EXECUTOR_NAME_PREFIX));
    }

    private void assertRouteAndPredicateNotOffloaded() {
        assertPredicateNotOffloaded();
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Route).getName(), startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    private void assertPredicateOffloaded() {
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Predicate,
                invokingThreads.get(RouterOffloadPoint.Predicate).getName(), startsWith(EXECUTOR_NAME_PREFIX));
    }

    private void assertRouteOffloaded() {
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Predicate).getName(), startsWith(EXECUTOR_NAME_PREFIX));
    }

    private void verifyAllOffloadPointsRecorded() {
        assertThat("Unexpected offload points recorded.", invokingThreads.size(), is(2));
    }

    private Predicate<StreamingHttpRequest> newPredicate() {
        return req -> {
            recordThread(RouterOffloadPoint.Predicate);
            return true;
        };
    }

    private StreamingHttpService newOffloadingService() {
        return new StreamingHttpServiceImpl(defaultStrategy(executionContextRule.executor()));
    }

    private StreamingHttpService newNonOffloadingService() {
        return new StreamingHttpServiceImpl(noOffloadsStrategy());
    }

    private void recordThread(final RouterOffloadPoint offloadPoint) {
        invokingThreads.put(offloadPoint, Thread.currentThread());
    }

    protected enum RouterOffloadPoint {
        Predicate,
        Route
    }

    private final class StreamingHttpServiceImpl implements StreamingHttpService {
        private final HttpExecutionStrategy strategy;

        StreamingHttpServiceImpl(final HttpExecutionStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {
            recordThread(RouterOffloadPoint.Route);
            return success(factory.ok());
        }

        @Override
        public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
            return strategy.merge(other);
        }
    }
}
