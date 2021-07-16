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
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpServerResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.predicate.dsl.RouteContinuation;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.predicate.PredicateRouterOffloadingTest.RouteServiceType.ASYNC_AGGREGATED;
import static io.servicetalk.http.router.predicate.PredicateRouterOffloadingTest.RouteServiceType.BLOCKING_AGGREGATED;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.Thread.NORM_PRIORITY;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PredicateRouterOffloadingTest {
    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";
    private static final String EXECUTOR_NAME_PREFIX = "router-executor";
    private static final String GLOBAL_EXECUTOR_NAME_PREFIX = "servicetalk-global";

    @RegisterExtension
    final ExecutionContextExtension executionContextRule = new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(new DefaultThreadFactory(IO_EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY)),
            () -> newCachedThreadExecutor(new DefaultThreadFactory(EXECUTOR_NAME_PREFIX, true, NORM_PRIORITY)));
    @Nullable
    private ServerContext context;
    @Nullable
    private BlockingHttpClient client;
    private ConcurrentMap<RouterOffloadPoint, Thread> invokingThreads;
    private HttpServerBuilder serverBuilder;
    private RouteServiceType routeServiceType;

    @BeforeEach
    void setUp() {
        serverBuilder = HttpServers.forAddress(localAddress(0)).ioExecutor(executionContextRule.ioExecutor());
        invokingThreads = new ConcurrentHashMap<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        final CompositeCloseable closeable = newCompositeCloseable();
        if (context != null) {
            closeable.append(context);
        }
        closeable.closeAsync().toFuture().get();
    }

    @ParameterizedTest
    @EnumSource(RouteServiceType.class)
    void predicateAndRouteAreOffloaded(RouteServiceType routeServiceType) throws Exception {
        this.routeServiceType = routeServiceType;
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(this::recordRouterThread)
                        .executionStrategy(defaultStrategy(executionContextRule.executor())),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteAndPredicateOffloaded();
    }

    @ParameterizedTest
    @EnumSource(RouteServiceType.class)
    void predicateOffloadedAndNotRoute(RouteServiceType routeServiceType) throws Exception {
        this.routeServiceType = routeServiceType;
        assumeSafeToDisableOffloading(routeServiceType);
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(this::recordRouterThread).executionStrategy(noOffloadsStrategy()),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        // Server is still offloaded, noOffloadsStrategy at route level isn't enough to disable offloading
        assertRouteAndPredicateOffloaded();
    }

    @ParameterizedTest
    @EnumSource(RouteServiceType.class)
    void routeOffloadedAndNotPredicate(RouteServiceType routeServiceType) throws Exception {
        this.routeServiceType = routeServiceType;
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        serverBuilder.executionStrategy(noOffloadsStrategy());
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(this::recordRouterThread)
                        .executionStrategy(defaultStrategy(executionContextRule.executor())),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteOffloadedAndNotPredicate(EXECUTOR_NAME_PREFIX);
    }

    @ParameterizedTest
    @EnumSource(RouteServiceType.class)
    void routeDefaultAndPredicateNotOffloaded(RouteServiceType routeServiceType) throws Exception {
        this.routeServiceType = routeServiceType;
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        serverBuilder.executionStrategy(noOffloadsStrategy());
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(this::recordRouterThread),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteOffloadedAndNotPredicate(GLOBAL_EXECUTOR_NAME_PREFIX);
    }

    @ParameterizedTest
    @EnumSource(RouteServiceType.class)
    void noOffloads(RouteServiceType routeServiceType) throws Exception {
        this.routeServiceType = routeServiceType;
        assumeSafeToDisableOffloading(routeServiceType);
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        serverBuilder.executionStrategy(noOffloadsStrategy());
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(this::recordRouterThread).executionStrategy(noOffloadsStrategy()),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteAndPredicateNotOffloaded();
    }

    @ParameterizedTest
    @EnumSource(RouteServiceType.class)
    void routeStrategySameAsRouter(RouteServiceType routeServiceType) throws Exception {
        this.routeServiceType = routeServiceType;
        assumeSafeToDisableOffloading(routeServiceType);
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        final HttpExecutionStrategy routerStrat = newMetaUnaffectingExecutionStrategy();
        final HttpExecutionStrategy routeStrat = newMetaUnaffectingExecutionStrategy();
        serverBuilder.executionStrategy(routerStrat);
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(this::recordRouterThread).executionStrategy(routeStrat),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteAndPredicateNotOffloaded();
    }

    private HttpExecutionStrategy newMetaUnaffectingExecutionStrategy() {
        return (routeServiceType == ASYNC_AGGREGATED || routeServiceType == BLOCKING_AGGREGATED ?
                customStrategyBuilder().offloadSend() : customStrategyBuilder().offloadReceiveData()).build();
    }

    private HttpPredicateRouterBuilder newRouterBuilder() {
        serverBuilder.executionStrategy(defaultStrategy(executionContextRule.executor()));
        return new HttpPredicateRouterBuilder();
    }

    private BlockingHttpClient buildServerAndClient(final StreamingHttpService service) throws Exception {
        this.context = serverBuilder.listenStreamingAndAwait(service);
        client = HttpClients.forSingleAddress(serverHostAndPort(context))
                .buildBlocking();
        return client;
    }

    private void assertPredicateNotOffloaded() {
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Predicate,
                invokingThreads.get(RouterOffloadPoint.Predicate).getName(), startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    private void assertRouteOffloadedAndNotPredicate(final String executorNamePrefix) {
        assertPredicateNotOffloaded();
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Route).getName(),
                startsWith(executorNamePrefix));
    }

    private void assertRouteAndPredicateNotOffloaded() {
        assertPredicateNotOffloaded();
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Route).getName(), startsWith(IO_EXECUTOR_NAME_PREFIX));
    }

    private void assertRouteAndPredicateOffloaded() {
        assertPredicateOffloaded();
        assertRouteOffloaded();
    }

    private void assertPredicateOffloaded() {
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Predicate,
                invokingThreads.get(RouterOffloadPoint.Predicate).getName(), startsWith(EXECUTOR_NAME_PREFIX));
    }

    private void assertRouteOffloaded() {
        assertThat("Unexpected thread for point: " + RouterOffloadPoint.Route,
                invokingThreads.get(RouterOffloadPoint.Route).getName(), startsWith(EXECUTOR_NAME_PREFIX));
    }

    private void verifyAllOffloadPointsRecorded() {
        assertThat("Unexpected offload points recorded.", invokingThreads.size(), is(2));
    }

    private boolean recordRouterThread(StreamingHttpRequest req) {
        recordThread(RouterOffloadPoint.Predicate);
        return true;
    }

    private void recordThread(final RouterOffloadPoint offloadPoint) {
        invokingThreads.put(offloadPoint, currentThread());
    }

    protected enum RouterOffloadPoint {
        Predicate,
        Route
    }

    enum RouteServiceType {
        ASYNC_STREAMING {
            @Override
            void addThreadRecorderService(final RouteContinuation routeContinuation,
                                          final Consumer<RouterOffloadPoint> threadRecorder) {
                routeContinuation.thenRouteTo(new StreamingThreadRecorderService(threadRecorder));
            }
        },
        ASYNC_AGGREGATED {
            @Override
            void addThreadRecorderService(final RouteContinuation routeContinuation,
                                          final Consumer<RouterOffloadPoint> threadRecorder) {
                routeContinuation.thenRouteTo(new StreamingAggregatedThreadRecorderService(threadRecorder));
            }
        },
        BLOCKING_STREAMING {
            @Override
            void addThreadRecorderService(final RouteContinuation routeContinuation,
                                          final Consumer<RouterOffloadPoint> threadRecorder) {
                routeContinuation.thenRouteTo(new BlockingStreamingThreadRecorderService(threadRecorder));
            }
        },
        BLOCKING_AGGREGATED {
            @Override
            void addThreadRecorderService(final RouteContinuation routeContinuation,
                                          final Consumer<RouterOffloadPoint> threadRecorder) {
                routeContinuation.thenRouteTo(new BlockingAggregatedThreadRecorderService(threadRecorder));
            }
        };

        abstract void addThreadRecorderService(RouteContinuation routeContinuation,
                                               Consumer<RouterOffloadPoint> threadRecorder);
    }

    private abstract static class ThreadRecorderService {
        private final Consumer<RouterOffloadPoint> threadRecorder;

        ThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
            this.threadRecorder = threadRecorder;
        }

        void recordThread() {
            threadRecorder.accept(RouterOffloadPoint.Route);
        }
    }

    private static final class StreamingThreadRecorderService extends ThreadRecorderService
            implements StreamingHttpService {

        StreamingThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
            super(threadRecorder);
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {
            recordThread();
            return succeeded(factory.ok());
        }
    }

    private static final class StreamingAggregatedThreadRecorderService extends ThreadRecorderService
            implements HttpService {

        StreamingAggregatedThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
            super(threadRecorder);
        }

        @Override
        public Single<HttpResponse> handle(final HttpServiceContext ctx,
                                           final HttpRequest request,
                                           final HttpResponseFactory factory) {
            recordThread();
            return succeeded(factory.ok());
        }
    }

    private static final class BlockingStreamingThreadRecorderService extends ThreadRecorderService
            implements BlockingStreamingHttpService {

        BlockingStreamingThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
            super(threadRecorder);
        }

        @Override
        public void handle(final HttpServiceContext ctx,
                           final BlockingStreamingHttpRequest request,
                           final BlockingStreamingHttpServerResponse response) throws IOException {
            recordThread();
            response.status(OK).sendMetaData().close();
        }
    }

    private static final class BlockingAggregatedThreadRecorderService extends ThreadRecorderService
            implements BlockingHttpService {

        BlockingAggregatedThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
            super(threadRecorder);
        }

        @Override
        public HttpResponse handle(final HttpServiceContext ctx,
                                   final HttpRequest request,
                                   final HttpResponseFactory factory) {
            recordThread();
            return factory.ok();
        }
    }

    private void assumeSafeToDisableOffloading(final RouteServiceType api) {
        assumeFalse(api == RouteServiceType.BLOCKING_STREAMING, "BlockingStreaming + noOffloads = deadlock");
    }
}
