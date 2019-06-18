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
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@RunWith(Parameterized.class)
public class PredicateRouterOffloadingTest {
    private static final String IO_EXECUTOR_NAME_PREFIX = "io-executor";
    private static final String EXECUTOR_NAME_PREFIX = "router-executor";
    private static final String GLOBAL_EXECUTOR_NAME_PREFIX = "servicetalk-global";

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
    protected HttpServerBuilder serverBuilder;
    private final RouteServiceType routeServiceType;

    public PredicateRouterOffloadingTest(final RouteServiceType routeServiceType) {
        this.routeServiceType = routeServiceType;
    }

    @Parameters(name = "{index} - {0}")
    public static Collection<Object[]> routeServiceTypes() {
        return stream(RouteServiceType.values()).map(v -> new Object[]{v}).collect(toList());
    }

    @Before
    public void setUp() {
        serverBuilder = HttpServers.forAddress(localAddress(0)).ioExecutor(executionContextRule.ioExecutor());
        invokingThreads = new ConcurrentHashMap<>();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        final CompositeCloseable closeable = newCompositeCloseable();
        if (context != null) {
            closeable.append(context);
        }
        closeable.closeAsync().toFuture().get();
    }

    @Test
    public void predicateAndRouteAreOffloaded() throws Exception {
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(newPredicate()).executionStrategy(defaultStrategy(executionContextRule.executor())),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteAndPredicateOffloaded();
    }

    @Test
    public void predicateOffloadedAndNotRoute() throws Exception {
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(newPredicate()).executionStrategy(noOffloadsStrategy()),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        // Server is still offloaded, noOffloadsStrategy at route level isn't enough to disable offloading
        assertRouteAndPredicateOffloaded();
    }

    @Test
    public void routeOffloadedAndNotPredicate() throws Exception {
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        serverBuilder.executionStrategy(noOffloadsStrategy());
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(newPredicate()).executionStrategy(defaultStrategy(executionContextRule.executor())),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteOffloadedAndNotPredicate(EXECUTOR_NAME_PREFIX);
    }

    @Test
    public void routeDefaultAndPredicateNotOffloaded() throws Exception {
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        serverBuilder.executionStrategy(noOffloadsStrategy());
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(newPredicate()),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteOffloadedAndNotPredicate(GLOBAL_EXECUTOR_NAME_PREFIX);
    }

    @Test
    public void noOffloads() throws Exception {
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        serverBuilder.executionStrategy(noOffloadsStrategy());
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(newPredicate()).executionStrategy(noOffloadsStrategy()),
                this::recordThread);
        final BlockingHttpClient client = buildServerAndClient(routerBuilder.buildStreaming());
        client.request(client.get("/"));
        verifyAllOffloadPointsRecorded();
        assertRouteAndPredicateNotOffloaded();
    }

    @Test
    public void routeStrategySameAsRouter() throws Exception {
        final HttpPredicateRouterBuilder routerBuilder = newRouterBuilder();
        final HttpExecutionStrategy routerStrat = newMetaUnaffectingExecutionStrategy();
        final HttpExecutionStrategy routeStrat = newMetaUnaffectingExecutionStrategy();
        serverBuilder.executionStrategy(routerStrat);
        routeServiceType.addThreadRecorderService(
                routerBuilder.when(newPredicate()).executionStrategy(routeStrat),
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

    private Predicate<StreamingHttpRequest> newPredicate() {
        return req -> {
            recordThread(RouterOffloadPoint.Predicate);
            return true;
        };
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

        protected ThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
            this.threadRecorder = threadRecorder;
        }

        protected void recordThread() {
            threadRecorder.accept(RouterOffloadPoint.Route);
        }
    }

    private static final class StreamingThreadRecorderService extends ThreadRecorderService
            implements StreamingHttpService {

        protected StreamingThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
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

        protected StreamingAggregatedThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
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

        protected BlockingStreamingThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
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

        protected BlockingAggregatedThreadRecorderService(final Consumer<RouterOffloadPoint> threadRecorder) {
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
}
