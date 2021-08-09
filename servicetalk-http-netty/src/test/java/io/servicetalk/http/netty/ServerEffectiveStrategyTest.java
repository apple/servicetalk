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
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.IO_EXECUTOR_NAME_PREFIX;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.noStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategyNoVerify;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Character.isDigit;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ServerEffectiveStrategyTest {

    @Nullable
    private Params params;

    @AfterEach
    void tearDown() throws Exception {
        if (params != null) {
            params.dispose();
        }
    }

    enum ServerStrategyCase implements Supplier<Params> {
       noUserStrategyNoFilter(ServerEffectiveStrategyTest::noUserStrategyNoFilter),
       noUserStrategyWithFilter(ServerEffectiveStrategyTest::noUserStrategyWithFilter),
       userStrategyNoFilter(ServerEffectiveStrategyTest::userStrategyNoFilter),
       userStrategyWithFilter(ServerEffectiveStrategyTest::userStrategyWithFilter),
       userStrategyNoExecutorNoFilter(ServerEffectiveStrategyTest::userStrategyNoExecutorNoFilter),
       userStrategyNoExecutorWithFilter(ServerEffectiveStrategyTest::userStrategyNoExecutorWithFilter),
       userStrategyNoOffloadsNoExecutorNoFilter(
               ServerEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorNoFilter),
       userStrategyNoOffloadsNoExecutorWithFilter(
               ServerEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithFilter),
       userStrategyNoOffloadsWithExecutorNoFilter(
               ServerEffectiveStrategyTest::userStrategyNoOffloadsWithExecutorNoFilter),
       userStrategyNoOffloadsWithExecutorWithFilter(
               ServerEffectiveStrategyTest::userStrategyNoOffloadsWithExecutorWithFilter),
       customUserStrategyNoFilter(ServerEffectiveStrategyTest::customUserStrategyNoFilter),
       customUserStrategyWithFilter(ServerEffectiveStrategyTest::customUserStrategyWithFilter),
       customUserStrategyNoExecutorNoFilter(ServerEffectiveStrategyTest::customUserStrategyNoExecutorNoFilter),
       customUserStrategyNoExecutorWithFilter(ServerEffectiveStrategyTest::customUserStrategyNoExecutorWithFilter);

        private final Supplier<Params> paramsSupplier;

        ServerStrategyCase(Supplier<Params> paramsSupplier) {
            this.paramsSupplier = paramsSupplier;
        }

        @Override
        public Params get() {
            return paramsSupplier.get();
        }
    }

    private static Params noUserStrategyNoFilter() {
        Params params = new Params(false, false, false);
        params.initStateHolderDefaultStrategy();
        params.defaultOffloadPoints();
        return params;
    }

    private static Params noUserStrategyWithFilter() {
        Params params = new Params(false, false, true);
        params.initStateHolderDefaultStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoFilter() {
        Params params = new Params(true, false, false);
        params.initStateHolderUserStrategy();
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyWithFilter() {
        Params params = new Params(true, false, true);
        params.initStateHolderUserStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorNoFilter() {
        Params params = new Params(true, true, false);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithFilter() {
        Params params = new Params(true, true, true);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsWithExecutorNoFilter() {
        Params params = new Params(true, true, false);
        params.initStateHolderUserStrategyNoOffloads();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsWithExecutorWithFilter() {
        Params params = new Params(true, true, true);
        params.initStateHolderUserStrategyNoOffloads();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoExecutorNoFilter() {
        Params params = new Params(false, false, false);
        params.initStateHolderUserStrategyNoExecutor();
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyNoExecutorWithFilter() {
        Params params = new Params(false, false, true);
        params.initStateHolderUserStrategyNoExecutor();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyNoFilter() {
        Params params = new Params(true, false, false);
        params.initStateHolderCustomUserStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyWithFilter() {
        Params params = new Params(true, false, true);
        params.initStateHolderCustomUserStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyNoExecutorNoFilter() {
        Params params = new Params(false, false, false);
        params.initStateHolderCustomUserStrategyNoExecutor();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithFilter() {
        Params params = new Params(false, false, true);
        params.initStateHolderCustomUserStrategyNoExecutor();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void blocking(final ServerStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        BlockingHttpClient client = params.startBlocking();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.Blocking);
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void blockingStreaming(final ServerStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        assumeFalse(params.isNoOffloadsStrategy(), "Ignoring no-offloads strategy for blocking-streaming.");
        BlockingHttpClient client = params.startBlockingStreaming();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.BlockingStreaming);
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void asyncStreaming(final ServerStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        BlockingHttpClient client = params.startAsyncStreaming();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.AsyncStreaming);
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void async(final ServerStrategyCase strategyCase) throws Exception {
        params = strategyCase.get();
        assertThat("Null params supplied", params, notNullValue());
        BlockingHttpClient client = params.startAsync();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.Async);
    }

    private static final class Params {
        private static final String USER_STRATEGY_EXECUTOR_NAME_PREFIX = "user-strategy-executor";

        private final Map<ServiceType, EnumSet<ServerOffloadPoint>> offloadPoints =
                new EnumMap<>(ServiceType.class);
        private final Map<ServiceType, EnumSet<ServerOffloadPoint>> nonOffloadPoints =
                new EnumMap<>(ServiceType.class);
        private final Executor executor;
        private final boolean addFilter;
        @Nullable
        private InvokingThreadsRecorder<ServerOffloadPoint> invokingThreadsRecorder;
        private final boolean executorUsedForStrategy;
        private final boolean noOffloadsStrategy;

        Params(boolean executorUsedForStrategy, final boolean noOffloadsStrategy, boolean addFilter) {
            this.executorUsedForStrategy = executorUsedForStrategy;
            this.noOffloadsStrategy = noOffloadsStrategy;
            this.addFilter = addFilter;
            this.executor = executorUsedForStrategy ?
                    newCachedThreadExecutor(new DefaultThreadFactory(USER_STRATEGY_EXECUTOR_NAME_PREFIX)) :
                    Executors.from(r -> fail("This executor was not to be used"));
            for (ServiceType serviceType : ServiceType.values()) {
                offloadPoints.put(serviceType, EnumSet.noneOf(ServerOffloadPoint.class));
            }
            for (ServiceType serviceType : ServiceType.values()) {
                nonOffloadPoints.put(serviceType, EnumSet.noneOf(ServerOffloadPoint.class));
            }
        }

        void addOffloadedPointFor(ServiceType serviceType, ServerOffloadPoint... points) {
            EnumSet<ServerOffloadPoint> offloads = offloadPoints.get(serviceType);
            Collections.addAll(offloads, points);
        }

        void addNonOffloadedPointFor(ServiceType serviceType, ServerOffloadPoint... points) {
            EnumSet<ServerOffloadPoint> nonOffloads = nonOffloadPoints.get(serviceType);
            Collections.addAll(nonOffloads, points);
        }

        void allPointsOffloadedForAllServices() {
            EnumSet<ServerOffloadPoint> all = EnumSet.allOf(ServerOffloadPoint.class);
            for (ServiceType serviceType : ServiceType.values()) {
                offloadPoints.get(serviceType).addAll(all);
            }
        }

        void noPointsOffloadedForAllServices() {
            EnumSet<ServerOffloadPoint> all = EnumSet.allOf(ServerOffloadPoint.class);
            for (ServiceType serviceType : ServiceType.values()) {
                nonOffloadPoints.get(serviceType).addAll(all);
            }
        }

        void defaultOffloadPoints() {
            addOffloadedPointFor(ServiceType.Blocking, ServerOffloadPoint.ServiceHandle,
                    ServerOffloadPoint.RequestPayload);
            addNonOffloadedPointFor(ServiceType.Blocking, ServerOffloadPoint.Response);

            addOffloadedPointFor(ServiceType.BlockingStreaming, ServerOffloadPoint.ServiceHandle);
            addNonOffloadedPointFor(ServiceType.BlockingStreaming, ServerOffloadPoint.RequestPayload,
                    ServerOffloadPoint.Response);

            addOffloadedPointFor(ServiceType.Async, ServerOffloadPoint.ServiceHandle, ServerOffloadPoint.Response,
                    ServerOffloadPoint.RequestPayload);

            addOffloadedPointFor(ServiceType.AsyncStreaming, ServerOffloadPoint.ServiceHandle,
                    ServerOffloadPoint.Response, ServerOffloadPoint.RequestPayload);
        }

        void initStateHolderDefaultStrategy() {
            invokingThreadsRecorder = noStrategy();
        }

        void initStateHolderUserStrategy() {
            newRecorder(defaultStrategy(executor));
        }

        void initStateHolderUserStrategyNoExecutor() {
            newRecorder(defaultStrategy());
        }

        void initStateHolderCustomUserStrategy() {
            newRecorder(customStrategyBuilder().offloadAll().executor(executor).build());
        }

        void initStateHolderUserStrategyNoOffloads() {
            newRecorder(customStrategyBuilder().offloadNone().executor(immediate()).build());
        }

        void initStateHolderUserStrategyNoOffloadsNoExecutor() {
            newRecorder(noOffloadsStrategy());
        }

        void initStateHolderCustomUserStrategyNoExecutor() {
            newRecorder(customStrategyBuilder().offloadAll().build());
        }

        private void newRecorder(final HttpExecutionStrategy strategy) {
            invokingThreadsRecorder = userStrategyNoVerify(strategy);
        }

        boolean isNoOffloadsStrategy() {
            return noOffloadsStrategy;
        }

        BlockingHttpClient startBlocking() {
            assert invokingThreadsRecorder != null;
            final HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            initState(builder -> {
                if (strategy != null) {
                    builder.executionStrategy(strategy);
                }
                return builder.listenBlocking((ctx, request, factory) -> {
                    invokingThreadsRecorder.recordThread(ServerOffloadPoint.ServiceHandle);
                    return factory.ok().payloadBody(request.payloadBody());
                });
            });
            return invokingThreadsRecorder.client().asBlockingClient();
        }

        BlockingHttpClient startBlockingStreaming() {
            assert invokingThreadsRecorder != null;
            final HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            initState(builder -> {
                if (strategy != null) {
                    builder.executionStrategy(strategy);
                }
                return builder.listenBlockingStreaming((ctx, request, response) -> {
                    invokingThreadsRecorder.recordThread(ServerOffloadPoint.ServiceHandle);
                    try (PayloadWriter<Buffer> payloadWriter = response.sendMetaData()) {
                        request.payloadBody().forEach(buffer -> {
                            try {
                                payloadWriter.write(buffer);
                            } catch (IOException e) {
                                throwException(e);
                            }
                        });
                    } catch (IOException e) {
                        throwException(e);
                    }
                });
            });
            return invokingThreadsRecorder.client().asBlockingClient();
        }

        BlockingHttpClient startAsync() {
            assert invokingThreadsRecorder != null;
            final HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            initState(builder -> {
                if (strategy != null) {
                    builder.executionStrategy(strategy);
                }
                return builder.listen((ctx, request, factory) -> {
                    invokingThreadsRecorder.recordThread(ServerOffloadPoint.ServiceHandle);
                    HttpResponse response = factory.ok().payloadBody(request.payloadBody());
                    return succeeded(response);
                });
            });
            return invokingThreadsRecorder.client().asBlockingClient();
        }

        BlockingHttpClient startAsyncStreaming() {
            assert invokingThreadsRecorder != null;
            final HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            initState(builder -> {
                if (strategy != null) {
                    builder.executionStrategy(strategy);
                }
                return builder.listenStreaming((ctx, request, factory) -> {
                    invokingThreadsRecorder.recordThread(ServerOffloadPoint.ServiceHandle);
                    StreamingHttpResponse response = factory.ok().payloadBody(request.payloadBody());
                    return succeeded(response);
                });
            });
            return invokingThreadsRecorder.client().asBlockingClient();
        }

        void verifyOffloads(final ServiceType serviceType) {
            assert invokingThreadsRecorder != null;
            invokingThreadsRecorder.verifyOffloadCount();
            for (ServerOffloadPoint offloadPoint : offloadPoints.get(serviceType)) {
                if (executorUsedForStrategy) {
                    invokingThreadsRecorder.assertOffload(offloadPoint, USER_STRATEGY_EXECUTOR_NAME_PREFIX);
                } else {
                    invokingThreadsRecorder.assertOffload(offloadPoint);
                }
            }
            for (ServerOffloadPoint offloadPoint : nonOffloadPoints.get(serviceType)) {
                if (offloadPoint == ServerOffloadPoint.Response) {
                    if (offloadPoints.get(serviceType).contains(ServerOffloadPoint.ServiceHandle)) {
                        Thread serviceInvoker =
                                invokingThreadsRecorder.invokingThread(ServerOffloadPoint.ServiceHandle);
                        Thread responseInvoker = invokingThreadsRecorder.invokingThread(ServerOffloadPoint.Response);
                        // If service#handle is offloaded, and response is not then response may be requested
                        // synchronously from service#handle
                        final String namePrefix = stripTrailingDigits(serviceInvoker.getName());
                        assertThat("Unexpected thread for response (not-offloaded)",
                                responseInvoker.getName(), either(startsWith(namePrefix))
                                        .or(startsWith(IO_EXECUTOR_NAME_PREFIX)));
                    }
                } else {
                    invokingThreadsRecorder.assertNoOffload(offloadPoint);
                }
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

        private void initState(
                Function<HttpServerBuilder, Single<ServerContext>> serverStarter) {
            assert invokingThreadsRecorder != null;
            invokingThreadsRecorder.init((ioExecutor, serverBuilder) -> {
                serverBuilder.ioExecutor(ioExecutor)
                        .appendServiceFilter(new ServiceInvokingThreadRecorder(invokingThreadsRecorder));
                if (addFilter) {
                    serverBuilder.appendServiceFilter(StreamingHttpServiceFilter::new);
                }
                return serverStarter.apply(serverBuilder);
            }, (__, ___) -> { });
        }

        private static String stripTrailingDigits(final String str) {
            String stripped = str;
            while (isDigit(stripped.charAt(stripped.length() - 1))) {
                stripped = stripped.substring(0, stripped.length() - 1);
            }
            return stripped;
        }
    }

    private static final class ServiceInvokingThreadRecorder
            implements HttpExecutionStrategyInfluencer, StreamingHttpServiceFilterFactory {

        private final InvokingThreadsRecorder<ServerOffloadPoint> recorder;

        ServiceInvokingThreadRecorder(InvokingThreadsRecorder<ServerOffloadPoint> recorder) {
            this.recorder = requireNonNull(recorder);
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            return strategy;
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    return delegate().handle(ctx,
                            request.transformPayloadBody(publisher ->
                                    publisher.beforeOnNext(__ ->
                                            recorder.recordThread(ServerOffloadPoint.RequestPayload))),
                            responseFactory)
                            .map(resp -> resp.transformPayloadBody(pub ->
                                    pub.beforeRequest(__ -> recorder.recordThread(ServerOffloadPoint.Response))));
                }
            };
        }
    }

    private enum ServiceType {
        AsyncStreaming,
        BlockingStreaming,
        Blocking,
        Async
    }

    private enum ServerOffloadPoint {
        ServiceHandle,
        RequestPayload,
        Response
    }
}
