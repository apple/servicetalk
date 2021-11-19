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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
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
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.EnumSet;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.IO_EXECUTOR_NAME_PREFIX;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.noStrategy;
import static io.servicetalk.http.netty.InvokingThreadsRecorder.userStrategy;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Character.isDigit;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ServerEffectiveStrategyTest {

    enum ServerStrategyCase implements Function<ServiceType, Params> {
        defaultStrategyNoFilter(serviceType ->
                new Params(serviceType, false, null, Offloads.DEFAULT)),
        defaultStrategyWithFilter(serviceType ->
                new Params(serviceType, true, null, Offloads.ALL)),
        userStrategyNoFilter(serviceType ->
                new Params(serviceType, false, defaultStrategy(), Offloads.DEFAULT)),
        userStrategyWithFilter(serviceType ->
                new Params(serviceType, true, defaultStrategy(), Offloads.ALL)),
        userStrategyNoOffloadsNoFilter(serviceType ->
                new Params(serviceType, false, noOffloadsStrategy(), Offloads.NONE)),
        userStrategyNoOffloadsWithFilter(serviceType ->
                new Params(serviceType, true, noOffloadsStrategy(), Offloads.NONE)),
        customUserStrategyNoFilter(serviceType ->
                new Params(serviceType, false, customStrategyBuilder().offloadAll().build(), Offloads.ALL)),
        customUserStrategyWithFilter(serviceType ->
                new Params(serviceType, true, customStrategyBuilder().offloadAll().build(), Offloads.ALL));

        private final Function<ServiceType, Params> paramsProvider;

        ServerStrategyCase(Function<ServiceType, Params> paramsProvider) {
            this.paramsProvider = paramsProvider;
        }

        @Override
        public Params apply(ServiceType serviceType) {
            return paramsProvider.apply(serviceType);
        }
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void blocking(final ServerStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ServiceType.Blocking)) {
            assertThat("Null params supplied", params, notNullValue());
            BlockingHttpClient client = params.startBlocking();
            client.request(client.get("/")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
            params.verifyOffloads();
        }
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void blockingStreaming(final ServerStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ServiceType.BlockingStreaming)) {
            assertThat("Null params supplied", params, notNullValue());
            assumeFalse(params.isNoOffloadsStrategy(), "Ignoring no-offloads strategy for blocking-streaming.");
            BlockingHttpClient client = params.startBlockingStreaming();
            client.request(client.get("/")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
            params.verifyOffloads();
        }
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void asyncStreaming(final ServerStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ServiceType.AsyncStreaming)) {
            assertThat("Null params supplied", params, notNullValue());
            BlockingHttpClient client = params.startAsyncStreaming();
            client.request(client.get("/")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
            params.verifyOffloads();
        }
    }

    @ParameterizedTest
    @EnumSource(ServerStrategyCase.class)
    void async(final ServerStrategyCase strategyCase) throws Exception {
        try (Params params = strategyCase.apply(ServiceType.Async)) {
            assertThat("Null params supplied", params, notNullValue());
            BlockingHttpClient client = params.startAsync();
            client.request(client.get("/")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
            params.verifyOffloads();
        }
    }

    private static final class Params implements AutoCloseable {
        private final EnumSet<ServerOffloadPoint> offloadPoints;
        private final EnumSet<ServerOffloadPoint> nonOffloadPoints;
        private final boolean addFilter;
        private final InvokingThreadsRecorder<ServerOffloadPoint> invokingThreadsRecorder;

        Params(final ServiceType serviceType, boolean addFilter,
               @Nullable final HttpExecutionStrategy strategy, final Offloads expectedOffloads) {
            this.addFilter = addFilter;
            this.invokingThreadsRecorder = null == strategy ? noStrategy() : userStrategy(strategy);
            offloadPoints = expectedOffloads.forServiceType(serviceType);
            nonOffloadPoints = EnumSet.complementOf(offloadPoints);
        }

        boolean isNoOffloadsStrategy() {
            HttpExecutionStrategy strategy = invokingThreadsRecorder.executionStrategy();
            return (null != strategy && !strategy.hasOffloads()) ||
                    (null == strategy && !defaultStrategy().hasOffloads());
        }

        BlockingHttpClient startBlocking() {
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

        void verifyOffloads() {
            invokingThreadsRecorder.verifyOffloadCount();
            offloadPoints.forEach(invokingThreadsRecorder::assertOffload);
            for (ServerOffloadPoint offloadPoint : nonOffloadPoints) {
                if (offloadPoint == ServerOffloadPoint.Response) {
                    if (offloadPoints.contains(ServerOffloadPoint.ServiceHandle)) {
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

        @Override
        public void close() throws Exception {
            invokingThreadsRecorder.close();
        }

        private void initState(Function<HttpServerBuilder, Single<ServerContext>> serverStarter) {
            invokingThreadsRecorder.init((ioExecutor, serverBuilder) -> {
                serverBuilder.ioExecutor(ioExecutor)
                        .appendServiceFilter(new ServiceInvokingThreadRecorder(invokingThreadsRecorder));
                if (addFilter) {
                    serverBuilder.appendServiceFilter(new StreamingHttpServiceFilterFactory() {
                        @Override
                        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                            return new StreamingHttpServiceFilter(service);
                        }

                        @Override
                        public HttpExecutionStrategy requiredOffloads() {
                            // require full offloading
                            return offloadAll();
                        }
                    });
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
            implements ExecutionStrategyInfluencer<HttpExecutionStrategy>, StreamingHttpServiceFilterFactory {

        private final InvokingThreadsRecorder<ServerOffloadPoint> recorder;

        ServiceInvokingThreadRecorder(InvokingThreadsRecorder<ServerOffloadPoint> recorder) {
            this.recorder = requireNonNull(recorder);
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            // No influence since we do not block.
            return HttpExecutionStrategies.anyStrategy();
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

    private enum Offloads {
        NONE() {
            @Override
            EnumSet<ServerOffloadPoint> forServiceType(ServiceType serviceType) {
                return EnumSet.noneOf(ServerOffloadPoint.class);
            }
        },
        DEFAULT() {
            @Override
            EnumSet<ServerOffloadPoint> forServiceType(ServiceType serviceType) {
                switch (serviceType) {
                    case Blocking:
                        return EnumSet.of(ServerOffloadPoint.ServiceHandle, ServerOffloadPoint.RequestPayload);
                    case BlockingStreaming:
                        return EnumSet.of(ServerOffloadPoint.ServiceHandle);
                    case Async:
                    case AsyncStreaming:
                        return EnumSet.allOf(ServerOffloadPoint.class);
                    default:
                        throw new IllegalStateException("unexpected case " + serviceType);
                }
            }
        },
        ALL() {
            @Override
            EnumSet<ServerOffloadPoint> forServiceType(ServiceType serviceType) {
                return EnumSet.allOf(ServerOffloadPoint.class);
            }
        };

        abstract EnumSet<ServerOffloadPoint> forServiceType(ServiceType clientType);
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
