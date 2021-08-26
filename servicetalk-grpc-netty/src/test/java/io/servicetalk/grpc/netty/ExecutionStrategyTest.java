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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.netty.ExecutionStrategyTestServices.ThreadInfo;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterServiceFilter;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcExecutionStrategies.defaultStrategy;
import static io.servicetalk.grpc.api.GrpcExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.CLASS_EXEC_ID_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.CLASS_EXEC_ID_STRATEGY_BLOCKING_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.DEFAULT_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.DEFAULT_STRATEGY_BLOCKING_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.METHOD_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.METHOD_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.NULL;
import static io.servicetalk.grpc.netty.ExecutionStrategyTestServices.ThreadInfo.parse;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

class ExecutionStrategyTest {

    private static final String CONTEXT_EXEC_NAME_PREFIX = "context-executor";
    private static final String FILTER_EXEC_NAME_PREFIX = "filter-executor";

    @RegisterExtension
    static final ExecutorExtension<Executor> CONTEXT_EXEC =
            ExecutorExtension.withCachedExecutor(CONTEXT_EXEC_NAME_PREFIX);

    @RegisterExtension
    static final ExecutorExtension<Executor> FILTER_EXEC =
            ExecutorExtension.withCachedExecutor(FILTER_EXEC_NAME_PREFIX);

    private static final TestRequest REQUEST = TestRequest.newBuilder().setName("name").build();

    private static final RouteExecutionStrategyFactory<GrpcExecutionStrategy> STRATEGY_FACTORY =
            new TestExecutionStrategyFactory();

    private static final class TestExecutionStrategyFactory
            implements RouteExecutionStrategyFactory<GrpcExecutionStrategy> {

        @Override
        public GrpcExecutionStrategy get(final String id) {
            switch (id) {
                case "route":
                    return defaultStrategy();
                case "filter":
                    return defaultStrategy(FILTER_EXEC.executor());
                default:
                    throw new IllegalArgumentException("Unknown id: " + id);
            }
        }
    }

    private enum ContextExecutionStrategy {
        DEFAULT {
            @Override
            void configureContextExecutionStrategy(GrpcServerBuilder builder) {
                // noop
            }
        },
        CUSTOM {
            @Override
            void configureContextExecutionStrategy(GrpcServerBuilder builder) {
                builder.executionStrategy(defaultStrategy(CONTEXT_EXEC.executor()));
            }
        },
        NO_OFFLOADS {
            @Override
            void configureContextExecutionStrategy(GrpcServerBuilder builder) {
                builder.executionStrategy(noOffloadsStrategy());
            }
        };

        abstract void configureContextExecutionStrategy(GrpcServerBuilder builder);
    }

    private enum RouteExecutionStrategy {
        ASYNC_DEFAULT {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(DEFAULT_STRATEGY_ASYNC_SERVICE, STRATEGY_FACTORY);
            }
        },
        ASYNC_CLASS_EXEC_ID {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_EXEC_ID_STRATEGY_ASYNC_SERVICE, STRATEGY_FACTORY);
            }
        },
        ASYNC_CLASS_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE, STRATEGY_FACTORY);
            }
        },
        ASYNC_METHOD_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(METHOD_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE, STRATEGY_FACTORY);
            }
        },
        ASYNC_SERVICE_FACTORY_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory.Builder(STRATEGY_FACTORY)
                        .test(noOffloadsStrategy(), DEFAULT_STRATEGY_ASYNC_SERVICE)
                        .testBiDiStream(noOffloadsStrategy(), DEFAULT_STRATEGY_ASYNC_SERVICE)
                        .testResponseStream(noOffloadsStrategy(), DEFAULT_STRATEGY_ASYNC_SERVICE)
                        .testRequestStream(noOffloadsStrategy(), DEFAULT_STRATEGY_ASYNC_SERVICE)
                        .build();
            }
        },
        BLOCKING_DEFAULT {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(DEFAULT_STRATEGY_BLOCKING_SERVICE, STRATEGY_FACTORY);
            }
        },
        BLOCKING_CLASS_EXEC_ID {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_EXEC_ID_STRATEGY_BLOCKING_SERVICE, STRATEGY_FACTORY);
            }
        },
        BLOCKING_CLASS_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE, STRATEGY_FACTORY);
            }
        },
        BLOCKING_METHOD_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(METHOD_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE, STRATEGY_FACTORY);
            }
        },
        BLOCKING_SERVICE_FACTORY_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory.Builder(STRATEGY_FACTORY)
                        .testBlocking(noOffloadsStrategy(), DEFAULT_STRATEGY_BLOCKING_SERVICE)
                        .testBiDiStreamBlocking(noOffloadsStrategy(), DEFAULT_STRATEGY_BLOCKING_SERVICE)
                        .testResponseStreamBlocking(noOffloadsStrategy(), DEFAULT_STRATEGY_BLOCKING_SERVICE)
                        .testRequestStreamBlocking(noOffloadsStrategy(), DEFAULT_STRATEGY_BLOCKING_SERVICE)
                        .build();
            }
        };

        abstract ServiceFactory getServiceFactory();
    }

    private enum RouteApi {
        TEST {
            @Override
            TestResponse execute(BlockingTesterClient client) throws Exception {
                return client.test(REQUEST);
            }
        },
        TEST_BI_DI_STREAM {
            @Override
            TestResponse execute(BlockingTesterClient client) throws Exception {
                return extractResponse(() -> client.testBiDiStream(singletonList(REQUEST)));
            }
        },
        TEST_RESPONSE_STREAM {
            @Override
            TestResponse execute(BlockingTesterClient client) throws Exception {
                return extractResponse(() -> client.testResponseStream(REQUEST));
            }
        },
        TEST_REQUEST_STREAM {
            @Override
            TestResponse execute(BlockingTesterClient client) throws Exception {
                return client.testRequestStream(singletonList(REQUEST));
            }
        };

        abstract TestResponse execute(BlockingTesterClient client) throws Exception;

        private static TestResponse extractResponse(Callable<BlockingIterable<TestResponse>> clientCall)
                throws Exception {
            try (BlockingIterator<TestResponse> iter = clientCall.call().iterator()) {
                TestResponse response = iter.next();
                assertThat("Unexpected null instead of response.", response, is(notNullValue()));
                assertThat("Unexpected number of response items in iterator.", iter.hasNext(), is(false));
                return response;
            }
        }
    }

    private enum FilterConfiguration {
        NO_FILTER {
            @Override
            void appendServiceFilter(final ServiceFactory serviceFactory) {
                // noop
            }
        },
        DEFAULT_FILTER {
            @Override
            void appendServiceFilter(final ServiceFactory serviceFactory) {
                // This filter doesn't do anything, it just delegates, but we want to verify that presence of the filter
                // does not break execution strategy configuration
                serviceFactory.appendServiceFilter(TesterServiceFilter::new);
            }
        },
        ANNOTATED_FILTER {
            @Override
            void appendServiceFilter(final ServiceFactory serviceFactory) {
                // This filter wraps the service with annotated class as an attempt to modify route's execution strategy
                // for the original service. We want to make sure that this annotation will be ignored
                serviceFactory.appendServiceFilter(ServiceFilterWithExecutionStrategy::new);
            }
        };

        abstract void appendServiceFilter(ServiceFactory serviceFactory);

        @io.servicetalk.router.api.RouteExecutionStrategy(id = "filter")
        private static final class ServiceFilterWithExecutionStrategy extends TesterServiceFilter {

            ServiceFilterWithExecutionStrategy(final TesterService delegate) {
                super(delegate);
            }
        }
    }

    @Nullable
    private ContextExecutionStrategy contextStrategy;
    @Nullable
    private RouteExecutionStrategy routeStrategy;
    @Nullable
    private RouteApi routeApi;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private BlockingTesterClient client;

    private void setUp(ContextExecutionStrategy contextStrategy,
                       RouteExecutionStrategy routeStrategy,
                       RouteApi routeApi,
                       FilterConfiguration filterConfiguration) throws Exception {
        this.contextStrategy = contextStrategy;
        this.routeStrategy = routeStrategy;
        this.routeApi = routeApi;
        GrpcServerBuilder builder = GrpcServers.forAddress(localAddress(0));
        contextStrategy.configureContextExecutionStrategy(builder);
        ServiceFactory serviceFactory = routeStrategy.getServiceFactory();
        filterConfiguration.appendServiceFilter(serviceFactory);
        serverContext = builder.listenAndAwait(serviceFactory);
        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .executionStrategy(noOffloadsStrategy())
                .buildBlocking(new ClientFactory());
    }

    static Collection<Arguments> data() {
        List<Arguments> parameters = new ArrayList<>();
        for (ContextExecutionStrategy builderEs : ContextExecutionStrategy.values()) {
            for (RouteExecutionStrategy routeEs : RouteExecutionStrategy.values()) {
                for (RouteApi routeApi : RouteApi.values()) {
                    for (FilterConfiguration filterConfiguration : FilterConfiguration.values()) {
                        parameters.add(Arguments.of(builderEs, routeEs, routeApi, filterConfiguration));
                    }
                }
            }
        }
        return unmodifiableList(parameters);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            Objects.requireNonNull(client, "client").close();
        } finally {
            Objects.requireNonNull(serverContext, "serverContext").close();
        }
    }

    private boolean isDeadlockConfig() {
        if (contextStrategy == ContextExecutionStrategy.NO_OFFLOADS) {
            switch (routeStrategy) {
                case BLOCKING_CLASS_NO_OFFLOADS:
                case BLOCKING_METHOD_NO_OFFLOADS:
                case BLOCKING_SERVICE_FACTORY_NO_OFFLOADS:
                    if (routeApi != RouteApi.TEST) {
                        return true;
                    }
                    break;
                default:
                    // noop
            }
        }
        return false;
    }

    @ParameterizedTest(name = "context={0} route={1}, api={2}, filterConfiguration={3}")
    @MethodSource("data")
    void testRoute(ContextExecutionStrategy contextExecutionStrategy,
                   RouteExecutionStrategy routeStrategy,
                   RouteApi routeApi,
                   FilterConfiguration filterConfiguration) throws Exception {
        setUp(contextExecutionStrategy, routeStrategy, routeApi, filterConfiguration);
        Assumptions.assumeFalse(isDeadlockConfig(), "BlockingStreaming + noOffloads = deadlock");

        final ThreadInfo threadInfo = parse(routeApi.execute(Objects.requireNonNull(client, "client")));
        final ThreadInfo expected;
        switch (contextExecutionStrategy) {
            case DEFAULT:
                switch (routeStrategy) {
                    case ASYNC_DEFAULT:
                    case ASYNC_CLASS_NO_OFFLOADS:
                    case ASYNC_METHOD_NO_OFFLOADS:
                    case ASYNC_SERVICE_FACTORY_NO_OFFLOADS:
                    case ASYNC_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), globalThreadName(),
                                        NULL, NULL, globalThreadName(), globalThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), globalThreadName(),
                                        globalThreadName(), globalThreadName(), globalThreadName(), globalThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_DEFAULT:
                    case BLOCKING_CLASS_NO_OFFLOADS:
                    case BLOCKING_METHOD_NO_OFFLOADS:
                    case BLOCKING_SERVICE_FACTORY_NO_OFFLOADS:
                    case BLOCKING_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), globalThreadName(),
                                        NULL, NULL, NULL, NULL);
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown route execution strategy: " + routeStrategy);
                }
                break;
            case CUSTOM:
                switch (routeStrategy) {
                    case ASYNC_DEFAULT:
                    case ASYNC_CLASS_NO_OFFLOADS:
                    case ASYNC_METHOD_NO_OFFLOADS:
                    case ASYNC_SERVICE_FACTORY_NO_OFFLOADS:
                    case ASYNC_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(contextExecutorName(), contextThreadName(),
                                        NULL, NULL, contextThreadName(), contextThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(contextExecutorName(), contextThreadName(),
                                        contextThreadName(), contextThreadName(),
                                        contextThreadName(), contextThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_DEFAULT:
                    case BLOCKING_CLASS_NO_OFFLOADS:
                    case BLOCKING_METHOD_NO_OFFLOADS:
                    case BLOCKING_SERVICE_FACTORY_NO_OFFLOADS:
                    case BLOCKING_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(contextExecutorName(), contextThreadName(),
                                        NULL, NULL, NULL, NULL);
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown route execution strategy: " + routeStrategy);
                }
                break;
            case NO_OFFLOADS:
                switch (routeStrategy) {
                    case ASYNC_DEFAULT:
                    case ASYNC_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), globalThreadName(),
                                        NULL, NULL, globalThreadName(), globalThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), globalThreadName(),
                                        globalThreadName(), globalThreadName(), globalThreadName(), globalThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case ASYNC_CLASS_NO_OFFLOADS:
                    case ASYNC_METHOD_NO_OFFLOADS:
                    case ASYNC_SERVICE_FACTORY_NO_OFFLOADS:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), ioThreadName(),
                                        NULL, NULL, ioThreadName(), ioThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), ioThreadName(),
                                        ioThreadName(), ioThreadName(), ioThreadName(), ioThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_DEFAULT:
                    case BLOCKING_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), globalThreadName(),
                                        NULL, NULL, NULL, NULL);
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_CLASS_NO_OFFLOADS:
                    case BLOCKING_METHOD_NO_OFFLOADS:
                    case BLOCKING_SERVICE_FACTORY_NO_OFFLOADS:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(globalExecutorName(), ioThreadName(),
                                        NULL, NULL, NULL, NULL);
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown route execution strategy: " + routeStrategy);
                }
                break;
            default:
                throw new IllegalStateException("Unknown context execution strategy: " + contextExecutionStrategy);
        }
        assertThat("Unexpected handleExecutorName.", threadInfo.handleExecutorName,
                equalTo(expected.handleExecutorName));
        assertThat("Unexpected handleThreadName.", threadInfo.handleThreadName,
                startsWith(expected.handleThreadName));
        assertThat("Unexpected requestOnSubscribeThreadName.", threadInfo.requestOnSubscribeThreadName,
                startsWith(expected.requestOnSubscribeThreadName));
        assertThat("Unexpected requestOnNextThreadName.", threadInfo.requestOnNextThreadName,
                startsWith(expected.requestOnNextThreadName));
        assertThat("Unexpected responseOnSubscribeThreadName.", threadInfo.responseOnSubscribeThreadName,
                startsWith(expected.responseOnSubscribeThreadName));
        assertThat("Unexpected responseOnNextThreadName.", threadInfo.responseOnNextThreadName,
                startsWith(expected.responseOnNextThreadName));
    }

    private static String globalExecutorName() {
        return globalExecutionContext().executor().toString();
    }

    private static String contextExecutorName() {
        return CONTEXT_EXEC.executor().toString();
    }

    private static String globalThreadName() {
        return "servicetalk-global-executor";
    }

    private static String contextThreadName() {
        return CONTEXT_EXEC_NAME_PREFIX;
    }

    private static String ioThreadName() {
        return "servicetalk-global-io-executor";
    }
}
