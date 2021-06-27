/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceDefaultStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouteExecIdStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouteNoOffloadsStrategy;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest.RouterApi.BLOCKING_STREAMING;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestExecutorStrategy.DEFAULT;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestExecutorStrategy.EXEC;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestExecutorStrategy.NO_OFFLOADS;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestMode.GET;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestMode.GET_RS;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestMode.POST_RS;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.EXEC_NAME;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.RS_THREAD_NAME;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.THREAD_NAME;
import static io.servicetalk.router.utils.internal.DefaultRouteExecutionStrategyFactory.getUsingDefaultStrategyFactory;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

final class ExecutionStrategyTest extends AbstractJerseyStreamingHttpServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @RegisterExtension
    static final ExecutorExtension<Executor> ROUTER_EXEC = ExecutorExtension.withCachedExecutor("router");

    @RegisterExtension
    static final ExecutorExtension<Executor> ROUTE_EXEC = ExecutorExtension.withCachedExecutor("route");

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            // We load all test resources to ensure there's no unexpected interactions between different strategies
            return new HashSet<>(asList(
                    ResourceDefaultStrategy.class,
                    ResourceRouteExecIdStrategy.class,
                    ResourceRouteNoOffloadsStrategy.class
            ));
        }
    }

    protected enum TestExecutorStrategy {
        DEFAULT {
            @Override
            void configureRouterBuilder(final HttpServerBuilder builder, final Executor ignored) {
                // noop
            }
        },
        EXEC {
            @Override
            void configureRouterBuilder(final HttpServerBuilder builder, final Executor executor) {
                builder.executionStrategy(defaultStrategy(executor));
            }
        },
        NO_OFFLOADS {
            @Override
            void configureRouterBuilder(final HttpServerBuilder builder, final Executor ignored) {
                builder.executionStrategy(noOffloadsStrategy());
            }
        };

        abstract void configureRouterBuilder(HttpServerBuilder builder, Executor executor);
    }

    private static final Map<String, TestExecutorStrategy> ROOT_PATHS_EXEC_STRATS;

    static {
        ROOT_PATHS_EXEC_STRATS = new HashMap<>();
        ROOT_PATHS_EXEC_STRATS.put("/rsc-default", DEFAULT);
        ROOT_PATHS_EXEC_STRATS.put("/rsc-rte-exec-id", EXEC);
        ROOT_PATHS_EXEC_STRATS.put("/rsc-rte-no-offloads", NO_OFFLOADS);
    }

    private static final Map<String, TestExecutorStrategy> SUB_PATHS_EXEC_STRATS;

    static {
        SUB_PATHS_EXEC_STRATS = new HashMap<>();
        SUB_PATHS_EXEC_STRATS.put("/subrsc-default", DEFAULT);
        SUB_PATHS_EXEC_STRATS.put("/subrsc-rte-exec-id", EXEC);
        SUB_PATHS_EXEC_STRATS.put("/subrsc-rte-no-offloads", NO_OFFLOADS);
    }

    protected enum TestMode {
        GET(false) {
            @Override
            String sendTestRequest(final String path,
                                   final AbstractJerseyStreamingHttpServiceTest reqHelper) {
                return reqHelper.sendAndAssertStatusOnly(reqHelper.get(path), OK);
            }
        },
        GET_RS(true) {
            @Override
            String sendTestRequest(final String path,
                                   final AbstractJerseyStreamingHttpServiceTest reqHelper) {
                return GET.sendTestRequest(path, reqHelper);
            }
        },
        POST_RS(true) {
            @Override
            String sendTestRequest(final String path,
                                   final AbstractJerseyStreamingHttpServiceTest reqHelper) {
                return reqHelper.sendAndAssertStatusOnly(reqHelper.post(path, "{\"foo\":\"bar\"}", APPLICATION_JSON),
                        OK);
            }
        };

        private final boolean rs;

        TestMode(final boolean rs) {
            this.rs = rs;
        }

        abstract String sendTestRequest(String path, AbstractJerseyStreamingHttpServiceTest reqHelper);
    }

    private static final Map<String, TestMode> SUB_SUB_PATH_TEST_MODES;

    static {
        SUB_SUB_PATH_TEST_MODES = new HashMap<>();
        SUB_SUB_PATH_TEST_MODES.put("", GET);
        SUB_SUB_PATH_TEST_MODES.put("-single", GET_RS);
        SUB_SUB_PATH_TEST_MODES.put("-single-response", GET_RS);
        SUB_SUB_PATH_TEST_MODES.put("-single-buffer", GET_RS);
        SUB_SUB_PATH_TEST_MODES.put("-single-mapped", POST_RS);
        SUB_SUB_PATH_TEST_MODES.put("-publisher-mapped", POST_RS);
    }

    private TestExecutorStrategy routerExecutionStrategy;
    private TestExecutorStrategy classExecutionStrategy;
    private TestExecutorStrategy methodExecutionStrategy;
    private TestMode testMode;
    private String path;

    void setUp(final TestExecutorStrategy routerExecutionStrategy,
               final TestExecutorStrategy classExecutionStrategy,
               final TestExecutorStrategy methodExecutionStrategy,
               final TestMode testMode,
               final String path,
               final RouterApi api) {
        this.routerExecutionStrategy = routerExecutionStrategy;
        this.classExecutionStrategy = classExecutionStrategy;
        this.methodExecutionStrategy = methodExecutionStrategy;
        this.testMode = testMode;
        this.path = path;
        assumeFalse(routerExecutionStrategy == NO_OFFLOADS && api == BLOCKING_STREAMING, "Don't deadlock");
        assertDoesNotThrow(() -> super.setUp(api));
    }

    static Collection<Arguments> data() {
        final List<Arguments> parameters = new ArrayList<>();
        stream(AbstractJerseyStreamingHttpServiceTest.RouterApi.values())
                .forEach(api -> stream(TestExecutorStrategy.values())
                        .forEach(routerExecutionStrategy -> ROOT_PATHS_EXEC_STRATS
                                .forEach((rootPath, classExecutionStrategy) -> SUB_PATHS_EXEC_STRATS
                                        .forEach((subPath, methodExecutionStrategy) -> SUB_SUB_PATH_TEST_MODES
                                                .forEach((subSubPath, testMode) -> {
                        final String path = rootPath + subPath + subSubPath;
                        parameters.add(Arguments.of(routerExecutionStrategy, classExecutionStrategy,
                                methodExecutionStrategy, testMode, path, api));
                    })))));
        return parameters;
    }

    static RouteExecutionStrategyFactory<HttpExecutionStrategy> asFactory(
            final Map<String, HttpExecutionStrategy> executionStrategies) {
        return id -> {
            final HttpExecutionStrategy stored = executionStrategies.get(id);
            return stored != null ? stored : getUsingDefaultStrategyFactory(id);
        };
    }

    @Override
    void configureBuilders(final HttpServerBuilder serverBuilder,
                           final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        // We do not call super.configureBuilders here because some strategies expect the default serverBuilder
        routerExecutionStrategy.configureRouterBuilder(serverBuilder, ROUTER_EXEC.executor());

        jerseyRouterBuilder.routeExecutionStrategyFactory(
                asFactory(singletonMap("test", defaultStrategy(ROUTE_EXEC.executor()))));
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @ParameterizedTest(name = "{5} {4} :: r={0}, c={1}, m={2} {3}")
    @MethodSource("data")
    void testResource(final TestExecutorStrategy routerExecutionStrategy,
                      final TestExecutorStrategy classExecutionStrategy,
                      final TestExecutorStrategy methodExecutionStrategy,
                      final TestMode testMode,
                      final String path,
                      final RouterApi api) {
        setUp(routerExecutionStrategy, classExecutionStrategy, methodExecutionStrategy, testMode, path, api);
        runTwiceToEnsureEndpointCache(this::runTest);
    }

    @SuppressWarnings("unchecked")
    private void runTest() {
        final String resBody = testMode.sendTestRequest(path, this);
        final Map<String, String> threadingInfo;
        try {
            threadingInfo = OBJECT_MAPPER.readValue(resBody, Map.class);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to test: " + path, e);
        }

        final String context = format("path=%s, router=%s, class=%s, method=%s, mode=%s : %s", path,
                routerExecutionStrategy, classExecutionStrategy, methodExecutionStrategy, testMode,
                resBody);

        switch (routerExecutionStrategy) {
            case DEFAULT:
                switch (classExecutionStrategy) {
                    case DEFAULT:
                    case NO_OFFLOADS:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertGlobalExecutor(testMode, context, threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                        }
                    case EXEC:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertGlobalExecutor(testMode, context, threadingInfo);
                                return;
                        }
                }

            case EXEC:
                switch (classExecutionStrategy) {
                    case DEFAULT:
                    case NO_OFFLOADS:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertRouterExecutor(testMode, context, threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                        }
                    case EXEC:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertRouterExecutor(testMode, context, threadingInfo);
                                return;
                        }
                }

            case NO_OFFLOADS:
                switch (classExecutionStrategy) {
                    case DEFAULT:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                                assertGlobalExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertDefaultNoOffloadsExecutor(testMode, context, threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                        }
                    case EXEC:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertDefaultNoOffloadsExecutor(testMode, context, threadingInfo);
                                return;
                        }
                    case NO_OFFLOADS:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertDefaultNoOffloadsExecutor(testMode, context, threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                        }
                }
        }
    }

    private void assertGlobalExecutor(final TestMode testMode, final String context,
                                      final Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isGlobalExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isGlobalExecutorThread());
        if (testMode.rs) {
            if (testMode == POST_RS && api == BLOCKING_STREAMING) {
                assertThat(context, threadingInfo.get(RS_THREAD_NAME), isIoExecutorThread());
            } else {
                assertThat(context, threadingInfo.get(RS_THREAD_NAME), isGlobalExecutorThread());
            }
        }
    }

    private static void assertRouteExecutor(final TestMode testMode, final String context,
                                            final Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isRouteExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isRouteExecutorThread());
        if (testMode.rs) {
            assertThat(context, threadingInfo.get(RS_THREAD_NAME), isRouteExecutorThread());
        }
    }

    private void assertRouterExecutor(final TestMode testMode, final String context,
                                      final Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isRouterExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isRouterExecutorThread());
        if (testMode.rs) {
            if (testMode == POST_RS && api == BLOCKING_STREAMING) {
                assertThat(context, threadingInfo.get(RS_THREAD_NAME), isIoExecutorThread());
            } else {
                assertThat(context, threadingInfo.get(RS_THREAD_NAME), isRouterExecutorThread());
            }
        }
    }

    private static void assertDefaultNoOffloadsExecutor(final TestMode testMode, final String context,
                                                        final Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isGlobalExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isIoExecutorThread());
        if (testMode.rs) {
            assertThat(context, threadingInfo.get(RS_THREAD_NAME), isIoExecutorThread());
        }
    }

    private static Matcher<String> isGlobalExecutor() {
        return is(globalExecutionContext().executor().toString());
    }

    private static Matcher<String> isGlobalExecutorThread() {
        return startsWith("servicetalk-global-executor");
    }

    private static Matcher<String> isIoExecutorThread() {
        return startsWith("stserverio-");
    }

    private static Matcher<String> isRouteExecutor() {
        return is(ROUTE_EXEC.executor().toString());
    }

    private static Matcher<String> isRouteExecutorThread() {
        return startsWith("route-");
    }

    private static Matcher<String> isRouterExecutor() {
        return is(ROUTER_EXEC.executor().toString());
    }

    private static Matcher<String> isRouterExecutorThread() {
        return startsWith("router-");
    }
}
