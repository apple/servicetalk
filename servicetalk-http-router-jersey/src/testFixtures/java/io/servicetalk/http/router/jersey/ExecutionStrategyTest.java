/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceDefaultStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouteExecIdStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouteNoOffloadsStrategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestExecutorStrategy.DEFAULT;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestExecutorStrategy.EXEC;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestExecutorStrategy.NO_OFFLOADS;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestMode.GET;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestMode.GET_RS;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestMode.POST_RS;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.EXEC_NAME;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.RS_THREAD_NAME;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.THREAD_NAME;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.lang.String.format;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public final class ExecutionStrategyTest extends AbstractJerseyStreamingHttpServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ClassRule
    public static final ExecutorRule ROUTER_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("router-", true, NORM_PRIORITY)));

    @ClassRule
    public static final ExecutorRule ROUTE_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("route-", true, NORM_PRIORITY)));

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
            void configureRouterBuilder(final HttpJerseyRouterBuilder builder, final Executor ignored) {
                // noop
            }
        },
        EXEC {
            @Override
            void configureRouterBuilder(final HttpJerseyRouterBuilder builder, final Executor executor) {
                builder.executionStrategy(defaultStrategy(executor));
            }
        },
        NO_OFFLOADS {
            @Override
            void configureRouterBuilder(final HttpJerseyRouterBuilder builder, final Executor ignored) {
                builder.executionStrategy(noOffloadsStrategy());
            }
        };

        abstract void configureRouterBuilder(HttpJerseyRouterBuilder builder, Executor executor);
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

    private final TestExecutorStrategy routerExecutionStrategy;

    public ExecutionStrategyTest(final TestExecutorStrategy routerExecutionStrategy) {
        this.routerExecutionStrategy = routerExecutionStrategy;
    }

    @Parameters
    public static Collection<Object[]> data() {
        return stream(TestExecutorStrategy.values()).map(e -> new Object[]{e}).collect(toList());
    }

    static Function<String, HttpExecutionStrategy> asFactory(
            Map<String, HttpExecutionStrategy> executionStrategies) {
        return executionStrategies::get;
    }

    @Override
    protected HttpJerseyRouterBuilder configureBuilder(final HttpJerseyRouterBuilder builder) {
        routerExecutionStrategy.configureRouterBuilder(builder, ROUTER_EXEC.getExecutor());

        return builder.routeExecutionStrategyFactory(
                asFactory(singletonMap("test", defaultStrategy(ROUTE_EXEC.getExecutor()))));
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }

    @Test
    public void allResources() {
        ROOT_PATHS_EXEC_STRATS.forEach((rootPath, classExecutionStrategy) -> {
            SUB_PATHS_EXEC_STRATS.forEach((subPath, methodExecutionStrategy) -> {
                SUB_SUB_PATH_TEST_MODES.forEach((subSubPath, testMode) -> {
                    String path = rootPath + subPath + subSubPath;
                    testResource(classExecutionStrategy, methodExecutionStrategy, testMode, path);
                });
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void testResource(final TestExecutorStrategy classExecutionStrategy,
                              final TestExecutorStrategy methodExecutionStrategy,
                              final TestMode testMode,
                              final String path) {

        final String resBody = testMode.sendTestRequest(path, this);
        final Map<String, String> threadingInfo;
        try {
            threadingInfo = OBJECT_MAPPER.readValue(resBody, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to test: " + path, e);
        }

        final String context = format("path=%s, router=%s, class=%s, method=%s, mode=%s : %s", path,
                routerExecutionStrategy, classExecutionStrategy, methodExecutionStrategy, testMode,
                resBody);

        switch (routerExecutionStrategy) {
            case DEFAULT:
                switch (classExecutionStrategy) {
                    case DEFAULT:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                                assertGlobalExecutor(testMode, context, threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isGlobalExecutorThread(), threadingInfo);
                                return;
                        }
                    case EXEC:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isGlobalExecutorThread(), threadingInfo);
                                return;
                        }
                    case NO_OFFLOADS:
                        switch (methodExecutionStrategy) {
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isGlobalExecutorThread(), threadingInfo);
                                return;
                        }
                }

            case EXEC:
                switch (classExecutionStrategy) {
                    case DEFAULT:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                                assertRouterExecutor(testMode, context, threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isRouterExecutorThread(), threadingInfo);
                                return;
                        }
                    case EXEC:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isRouterExecutorThread(), threadingInfo);
                                return;
                        }
                    case NO_OFFLOADS:
                        switch (methodExecutionStrategy) {
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                                return;
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isRouterExecutorThread(), threadingInfo);
                                return;
                        }
                }

            case NO_OFFLOADS:
                switch (classExecutionStrategy) {
                    case DEFAULT:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isIoExecutorThread(), threadingInfo);
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
                                assertOffloadsExecutor(testMode, context, isIoExecutorThread(), threadingInfo);
                                return;
                        }
                    case NO_OFFLOADS:
                        switch (methodExecutionStrategy) {
                            case DEFAULT:
                            case NO_OFFLOADS:
                                assertOffloadsExecutor(testMode, context, isIoExecutorThread(), threadingInfo);
                                return;
                            case EXEC:
                                assertRouteExecutor(testMode, context, threadingInfo);
                        }
                }
        }
    }

    private void assertGlobalExecutor(final TestMode testMode, final String context,
                                      Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isGlobalExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isGlobalExecutorThread());
        if (testMode.rs) {
            assertThat(context, threadingInfo.get(RS_THREAD_NAME), isGlobalExecutorThread());
        }
    }

    private void assertRouteExecutor(final TestMode testMode, final String context,
                                     Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isRouteExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isRouteExecutorThread());
        if (testMode.rs) {
            assertThat(context, threadingInfo.get(RS_THREAD_NAME), isRouteExecutorThread());
        }
    }

    private void assertRouterExecutor(final TestMode testMode, final String context,
                                      Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isRouterExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), isRouterExecutorThread());
        if (testMode.rs) {
            assertThat(context, threadingInfo.get(RS_THREAD_NAME), isRouterExecutorThread());
        }
    }

    private void assertOffloadsExecutor(final TestMode testMode, final String context,
                                        Matcher<String> routerExecutorThreadMatcher,
                                        Map<String, String> threadingInfo) {
        assertThat(context, threadingInfo.get(EXEC_NAME), isImmediateExecutor());
        assertThat(context, threadingInfo.get(THREAD_NAME), routerExecutorThreadMatcher);
        if (testMode.rs) {
            assertThat(context, threadingInfo.get(RS_THREAD_NAME),
                    testMode == POST_RS ? isIoExecutorThread() : routerExecutorThreadMatcher);
        }
    }

    private static Matcher<String> isImmediateExecutor() {
        return is(immediate().toString());
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
        return is(ROUTE_EXEC.getExecutor().toString());
    }

    private static Matcher<String> isRouteExecutorThread() {
        return startsWith("route-");
    }

    private static Matcher<String> isRouterExecutor() {
        return is(ROUTER_EXEC.getExecutor().toString());
    }

    private static Matcher<String> isRouterExecutorThread() {
        return startsWith("router-");
    }
}
