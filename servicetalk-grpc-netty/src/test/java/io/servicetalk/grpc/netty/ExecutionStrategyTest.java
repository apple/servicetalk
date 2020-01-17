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
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.netty.ExecutionStrategyTestServices.ThreadInfo;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterClient;
import io.servicetalk.grpc.netty.TesterProto.Tester.ClientFactory;
import io.servicetalk.grpc.netty.TesterProto.Tester.ServiceFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;

@RunWith(Parameterized.class)
public class ExecutionStrategyTest {

    private static final String BUILDER_EXEC_NAME_PREFIX = "builder-executor-";
    private static final String ROUTE_EXEC_NAME_PREFIX = "route-executor-";

    @ClassRule
    public static final ExecutorRule<Executor> BUILDER_EXEC = ExecutorRule.withNamePrefix(BUILDER_EXEC_NAME_PREFIX);

    @ClassRule
    public static final ExecutorRule<Executor> ROUTE_EXEC = ExecutorRule.withNamePrefix(ROUTE_EXEC_NAME_PREFIX);

    private static final TestRequest REQUEST = TestRequest.newBuilder().setName("name").build();

    private enum BuilderExecutionStrategy {
        DEFAULT {
            @Override
            void configureBuilderExecutionStrategy(GrpcServerBuilder builder) {
                // noop
            }
        },
        CUSTOM {
            @Override
            void configureBuilderExecutionStrategy(GrpcServerBuilder builder) {
                builder.executionStrategy(defaultStrategy(BUILDER_EXEC.executor()));
            }
        },
        NO_OFFLOADS {
            @Override
            void configureBuilderExecutionStrategy(GrpcServerBuilder builder) {
                builder.executionStrategy(noOffloadsStrategy());
            }
        };

        abstract void configureBuilderExecutionStrategy(GrpcServerBuilder builder);
    }

    private enum RouteExecutionStrategy {
        ASYNC_DEFAULT {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(DEFAULT_STRATEGY_ASYNC_SERVICE);
            }
        },
        ASYNC_CLASS_EXEC_ID {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_EXEC_ID_STRATEGY_ASYNC_SERVICE,
                        __ -> defaultStrategy(ROUTE_EXEC.executor()));
            }
        },
        ASYNC_CLASS_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE);
            }
        },
        ASYNC_METHOD_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(METHOD_NO_OFFLOADS_STRATEGY_ASYNC_SERVICE);
            }
        },
        ASYNC_SERVICE_FACTORY_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory.Builder()
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
                return new ServiceFactory(DEFAULT_STRATEGY_BLOCKING_SERVICE);
            }
        },
        BLOCKING_CLASS_EXEC_ID {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_EXEC_ID_STRATEGY_BLOCKING_SERVICE,
                        __ -> defaultStrategy(ROUTE_EXEC.executor()));
            }
        },
        BLOCKING_CLASS_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(CLASS_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE);
            }
        },
        BLOCKING_METHOD_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory(METHOD_NO_OFFLOADS_STRATEGY_BLOCKING_SERVICE);
            }
        },
        BLOCKING_SERVICE_FACTORY_NO_OFFLOADS {
            @Override
            ServiceFactory getServiceFactory() {
                return new ServiceFactory.Builder()
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

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final BuilderExecutionStrategy builderStrategy;
    private final RouteExecutionStrategy routeStrategy;
    private final RouteApi routeApi;
    private final ServerContext serverContext;
    private final BlockingTesterClient client;

    public ExecutionStrategyTest(BuilderExecutionStrategy builderStrategy,
                                 RouteExecutionStrategy routeStrategy,
                                 RouteApi routeApi) throws Exception {
        this.builderStrategy = builderStrategy;
        this.routeStrategy = routeStrategy;
        this.routeApi = routeApi;
        GrpcServerBuilder builder = GrpcServers.forAddress(localAddress(0));
        builderStrategy.configureBuilderExecutionStrategy(builder);
        serverContext = builder.listenAndAwait(routeStrategy.getServiceFactory());
        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .executionStrategy(noOffloadsStrategy())
                .buildBlocking(new ClientFactory());
    }

    @Parameterized.Parameters(name = "builder={0} route={1}, api={2}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();
        for (BuilderExecutionStrategy builderEs : BuilderExecutionStrategy.values()) {
            for (RouteExecutionStrategy routeEs : RouteExecutionStrategy.values()) {
                for (RouteApi routeApi : RouteApi.values()) {
                    parameters.add(new Object[] {builderEs, routeEs, routeApi});
                }
            }
        }
        return unmodifiableList(parameters);
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    private boolean isDeadlockConfig() {
        if (builderStrategy == BuilderExecutionStrategy.NO_OFFLOADS) {
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

    @Test
    public void testRoute() throws Exception {
        assumeFalse("BlockingStreaming + noOffloads = deadlock", isDeadlockConfig());

        final ThreadInfo threadInfo = parse(routeApi.execute(client));
        final ThreadInfo expected;
        switch (builderStrategy) {
            case DEFAULT:
                switch (routeStrategy) {
                    case ASYNC_DEFAULT:
                    case ASYNC_CLASS_NO_OFFLOADS:
                    case ASYNC_METHOD_NO_OFFLOADS:
                    case ASYNC_SERVICE_FACTORY_NO_OFFLOADS:
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
                    case ASYNC_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
                                        NULL, NULL, routeThreadName(), routeThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
                                        routeThreadName(), routeThreadName(), routeThreadName(), routeThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_DEFAULT:
                    case BLOCKING_CLASS_NO_OFFLOADS:
                    case BLOCKING_METHOD_NO_OFFLOADS:
                    case BLOCKING_SERVICE_FACTORY_NO_OFFLOADS:
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
                    case BLOCKING_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
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
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(builderExecutorName(), builderThreadName(),
                                        NULL, NULL, builderThreadName(), builderThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(builderExecutorName(), builderThreadName(),
                                        builderThreadName(), builderThreadName(),
                                        builderThreadName(), builderThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case ASYNC_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
                                        NULL, NULL, routeThreadName(), routeThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
                                        routeThreadName(), routeThreadName(), routeThreadName(), routeThreadName());
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_DEFAULT:
                    case BLOCKING_CLASS_NO_OFFLOADS:
                    case BLOCKING_METHOD_NO_OFFLOADS:
                    case BLOCKING_SERVICE_FACTORY_NO_OFFLOADS:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(builderExecutorName(), builderThreadName(),
                                        NULL, NULL, NULL, NULL);
                                break;
                            default:
                                throw new IllegalStateException("Unknown route API: " + routeApi);
                        }
                        break;
                    case BLOCKING_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
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
                    case ASYNC_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
                                        NULL, NULL, routeThreadName(), routeThreadName());
                                break;
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
                                        routeThreadName(), routeThreadName(), routeThreadName(), routeThreadName());
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
                    case BLOCKING_CLASS_EXEC_ID:
                        switch (routeApi) {
                            case TEST:
                            case TEST_RESPONSE_STREAM:
                            case TEST_BI_DI_STREAM:
                            case TEST_REQUEST_STREAM:
                                expected = new ThreadInfo(routeExecutorName(), routeThreadName(),
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
                throw new IllegalStateException("Unknown builder execution strategy: " + builderStrategy);
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

    private static String builderExecutorName() {
        return BUILDER_EXEC.executor().toString();
    }

    private static String routeExecutorName() {
        return ROUTE_EXEC.executor().toString();
    }

    private static String globalThreadName() {
        return "servicetalk-global-executor";
    }

    private static String builderThreadName() {
        return BUILDER_EXEC_NAME_PREFIX;
    }

    private static String routeThreadName() {
        return ROUTE_EXEC_NAME_PREFIX;
    }

    private static String ioThreadName() {
        return "servicetalk-global-io-executor";
    }
}
