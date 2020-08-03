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
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.IO_EXECUTOR_NAME_PREFIX;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.noStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategyNoVerify;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Character.isDigit;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public class ServerEffectiveStrategyTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final ParamsSupplier paramSupplier;
    @Nullable
    private Params params;

    public ServerEffectiveStrategyTest(final ParamsSupplier paramSupplier) {
        this.paramSupplier = paramSupplier;
    }

    @Before
    public void setUp() throws Exception {
        params = paramSupplier.newParams();
    }

    @After
    public void tearDown() throws Exception {
        if (params != null) {
            params.dispose();
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<ParamsSupplier> params() {
        List<ParamsSupplier> params = new ArrayList<>();
        params.add(wrap("noUserStrategyNoFilter", ServerEffectiveStrategyTest::noUserStrategyNoFilter));
        params.add(wrap("noUserStrategyWithFilter", ServerEffectiveStrategyTest::noUserStrategyWithFilter));
        params.add(wrap("userStrategyNoFilter", ServerEffectiveStrategyTest::userStrategyNoFilter));
        params.add(wrap("userStrategyWithFilter", ServerEffectiveStrategyTest::userStrategyWithFilter));
        params.add(wrap("userStrategyNoExecutorNoFilter",
                ServerEffectiveStrategyTest::userStrategyNoExecutorNoFilter));
        params.add(wrap("userStrategyNoExecutorWithFilter",
                ServerEffectiveStrategyTest::userStrategyNoExecutorWithFilter));
        params.add(wrap("userStrategyNoOffloadsNoExecutorNoFilter",
                ServerEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorNoFilter));
        params.add(wrap("userStrategyNoOffloadsNoExecutorWithFilter",
                ServerEffectiveStrategyTest::userStrategyNoOffloadsNoExecutorWithFilter));
        params.add(wrap("userStrategyNoOffloadsWithExecutorNoFilter",
                ServerEffectiveStrategyTest::userStrategyNoOffloadsWithExecutorNoFilter));
        params.add(wrap("userStrategyNoOffloadsWithExecutorWithFilter",
                ServerEffectiveStrategyTest::userStrategyNoOffloadsWithExecutorWithFilter));
        params.add(wrap("customUserStrategyNoFilter",
                ServerEffectiveStrategyTest::customUserStrategyNoFilter));
        params.add(wrap("customUserStrategyWithFilter",
                ServerEffectiveStrategyTest::customUserStrategyWithFilter));
        params.add(wrap("customUserStrategyNoExecutorNoFilter",
                ServerEffectiveStrategyTest::customUserStrategyNoExecutorNoFilter));
        params.add(wrap("customUserStrategyNoExecutorWithFilter",
                ServerEffectiveStrategyTest::customUserStrategyNoExecutorWithFilter));
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
        Params params = new Params(false);
        params.initStateHolderDefaultStrategy();
        params.defaultOffloadPoints();
        return params;
    }

    private static Params noUserStrategyWithFilter() {
        Params params = new Params(true);
        params.initStateHolderDefaultStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoFilter() {
        Params params = new Params(false);
        params.initStateHolderUserStrategy();
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyWithFilter() {
        Params params = new Params(true);
        params.initStateHolderUserStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorNoFilter() {
        Params params = new Params(false);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsNoExecutorWithFilter() {
        Params params = new Params(true);
        params.initStateHolderUserStrategyNoOffloadsNoExecutor();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsWithExecutorNoFilter() {
        Params params = new Params(false);
        params.initStateHolderUserStrategyNoOffloads();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoOffloadsWithExecutorWithFilter() {
        Params params = new Params(true);
        params.initStateHolderUserStrategyNoOffloads();
        params.noPointsOffloadedForAllServices();
        return params;
    }

    private static Params userStrategyNoExecutorNoFilter() {
        Params params = new Params(false);
        params.initStateHolderUserStrategyNoExecutor();
        params.defaultOffloadPoints();
        return params;
    }

    private static Params userStrategyNoExecutorWithFilter() {
        Params params = new Params(true);
        params.initStateHolderUserStrategyNoExecutor();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyNoFilter() {
        Params params = new Params(false);
        params.initStateHolderCustomUserStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyWithFilter() {
        Params params = new Params(true);
        params.initStateHolderCustomUserStrategy();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyNoExecutorNoFilter() {
        Params params = new Params(false);
        params.initStateHolderCustomUserStrategyNoExecutor();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    private static Params customUserStrategyNoExecutorWithFilter() {
        Params params = new Params(true);
        params.initStateHolderCustomUserStrategyNoExecutor();
        params.allPointsOffloadedForAllServices();
        return params;
    }

    @Test
    public void blocking() throws Exception {
        assert params != null;
        BlockingHttpClient client = params.startBlocking();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.Blocking);
    }

    @Test
    public void blockingStreaming() throws Exception {
        assert params != null;
        assumeThat("Ignoring no-offloads strategy for blocking-streaming.",
                params.isNoOffloadsStrategy(), is(false));
        BlockingHttpClient client = params.startBlockingStreaming();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.BlockingStreaming);
    }

    @Test
    public void asyncStreaming() throws Exception {
        assert params != null;
        BlockingHttpClient client = params.startAsyncStreaming();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.AsyncStreaming);
    }

    @Test
    public void async() throws Exception {
        assert params != null;
        BlockingHttpClient client = params.startAsync();
        client.request(client.get("/")
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        params.verifyOffloads(ServiceType.Async);
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
        private static final String SERVICE_EXECUTOR_NAME_PREFIX = "service-executor";


        private final Map<ServiceType, List<ServerOffloadPoint>> offloadPoints;
        private final Map<ServiceType, List<ServerOffloadPoint>> nonOffloadPoints;
        private final Executor executor;
        private final Executor serviceExecutor;
        private final boolean addFilter;
        @Nullable
        private InvokingThreadsRecorder<ServerOffloadPoint> invokingThreadsRecorder;
        private boolean executorUsedForStrategy = true;
        private boolean verifyStrategyUsed;
        private boolean noOffloadsStrategy;

        Params(boolean addFilter) {
            this.addFilter = addFilter;
            this.executor = newCachedThreadExecutor(new DefaultThreadFactory(USER_STRATEGY_EXECUTOR_NAME_PREFIX));
            serviceExecutor = newCachedThreadExecutor(new DefaultThreadFactory(SERVICE_EXECUTOR_NAME_PREFIX));
            offloadPoints = new EnumMap<>(ServiceType.class);
            for (ServiceType serviceType : ServiceType.values()) {
                offloadPoints.put(serviceType, new ArrayList<>());
            }
            nonOffloadPoints = new EnumMap<>(ServiceType.class);
            for (ServiceType serviceType : ServiceType.values()) {
                nonOffloadPoints.put(serviceType, new ArrayList<>());
            }
        }

        void addOffloadedPointFor(ServiceType serviceType, ServerOffloadPoint... points) {
            offloadPoints.get(serviceType).addAll(asList(points));
        }

        void addNonOffloadedPointFor(ServiceType serviceType, ServerOffloadPoint... points) {
            nonOffloadPoints.get(serviceType).addAll(asList(points));
        }

        void allPointsOffloadedForAllServices() {
            for (ServiceType serviceType : ServiceType.values()) {
                offloadPoints.get(serviceType).addAll(asList(ServerOffloadPoint.ServiceHandle,
                        ServerOffloadPoint.RequestPayload, ServerOffloadPoint.Response));
            }
        }

        void noPointsOffloadedForAllServices() {
            for (ServiceType serviceType : ServiceType.values()) {
                nonOffloadPoints.get(serviceType).addAll(asList(ServerOffloadPoint.ServiceHandle,
                        ServerOffloadPoint.RequestPayload, ServerOffloadPoint.Response));
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
            executorUsedForStrategy = false;
            invokingThreadsRecorder = noStrategy();
        }

        void initStateHolderUserStrategy() {
            verifyStrategyUsed = false;
            newRecorder(defaultStrategy(executor));
        }

        void initStateHolderUserStrategyNoExecutor() {
            verifyStrategyUsed = false;
            executorUsedForStrategy = false;
            newRecorder(defaultStrategy());
        }

        void initStateHolderCustomUserStrategy() {
            verifyStrategyUsed = !addFilter;
            newRecorder(customStrategyBuilder().offloadAll().executor(executor).build());
        }

        void initStateHolderUserStrategyNoOffloads() {
            noOffloadsStrategy = true;
            verifyStrategyUsed = !addFilter;
            newRecorder(customStrategyBuilder().offloadNone().executor(immediate()).build());
        }

        void initStateHolderUserStrategyNoOffloadsNoExecutor() {
            noOffloadsStrategy = true;
            verifyStrategyUsed = !addFilter;
            executorUsedForStrategy = false;
            newRecorder(noOffloadsStrategy());
        }

        void initStateHolderCustomUserStrategyNoExecutor() {
            verifyStrategyUsed = !addFilter;
            executorUsedForStrategy = false;
            newRecorder(customStrategyBuilder().offloadAll().build());
        }

        private void newRecorder(final HttpExecutionStrategy strategy) {
            invokingThreadsRecorder = verifyStrategyUsed ? userStrategy(strategy) : userStrategyNoVerify(strategy);
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
            if (verifyStrategyUsed) {
                invokingThreadsRecorder.assertStrategyUsedForServer();
            }
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
                newCompositeCloseable().appendAll(executor, serviceExecutor).close();
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
