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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceDefaultStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouterExecIdStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouterExecStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceServerExecStrategy;

import net.javacrumbs.jsonunit.JsonMatchers;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.ExpectedExecutor.METHOD;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.ExpectedExecutor.ROUTER;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.ExpectedExecutor.SERVER;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.TestMode.GET;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.TestMode.GET_NEVER_CHUNKED;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.TestMode.POST;
import static io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.TestMode.POST_CHUNKED;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.DEFAULT_EXECUTOR_ID;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.EXEC_NAME;
import static io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.THREAD_NAME;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Arrays.asList;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonPartMatches;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@RunWith(Parameterized.class)
public abstract class AbstractExecutionStrategyTest extends AbstractJerseyStreamingHttpServiceTest {
    @ClassRule
    public static final ExecutorRule EXEC1 = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("exec1-", true, NORM_PRIORITY)));

    @ClassRule
    public static final ExecutorRule EXEC2 = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("exec2-", true, NORM_PRIORITY)));

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            // We load all test resources to ensure there's no unexpected interactions between different strategies
            return new HashSet<>(asList(
                    ResourceDefaultStrategy.class,
                    ResourceServerExecStrategy.class,
                    ResourceRouterExecStrategy.class,
                    ResourceRouterExecIdStrategy.class
            ));
        }
    }

    protected enum ExpectedExecutor {
        SERVER, ROUTER, METHOD
    }

    protected enum TestMode {
        GET, GET_CHUNKED, GET_NEVER_CHUNKED, POST, POST_CHUNKED
    }

    private static final Map<String, ExpectedExecutor[]> ROOT_PATHS_EXPECTED_EXECS;

    static {
        ROOT_PATHS_EXPECTED_EXECS = new HashMap<>();
        ROOT_PATHS_EXPECTED_EXECS.put("/rsc-default", new ExpectedExecutor[]{SERVER, SERVER, ROUTER, METHOD});
        ROOT_PATHS_EXPECTED_EXECS.put("/rsc-srvr-exec", new ExpectedExecutor[]{SERVER, SERVER, ROUTER, METHOD});
        ROOT_PATHS_EXPECTED_EXECS.put("/rsc-rtr-exec", new ExpectedExecutor[]{ROUTER, SERVER, ROUTER, METHOD});
        ROOT_PATHS_EXPECTED_EXECS.put("/rsc-rtr-exec-id", new ExpectedExecutor[]{METHOD, SERVER, ROUTER, METHOD});
    }

    private static final String[] SUB_PATHS =
            {"/subrsc-default", "/subrsc-srvr-exec", "/subrsc-rtr-exec", "/subrsc-rtr-exec-id"};

    private static final Map<String, TestMode> SUB_SUB_PATH_TEST_MODES;

    static {
        SUB_SUB_PATH_TEST_MODES = new HashMap<>();
        SUB_SUB_PATH_TEST_MODES.put("", GET);
        SUB_SUB_PATH_TEST_MODES.put("-single", GET);
        SUB_SUB_PATH_TEST_MODES.put("-single-response", GET);
        SUB_SUB_PATH_TEST_MODES.put("-single-buffer", GET_NEVER_CHUNKED);
        SUB_SUB_PATH_TEST_MODES.put("-single-mapped", POST);
        SUB_SUB_PATH_TEST_MODES.put("-publisher-mapped", POST_CHUNKED);
    }

    private static final Map<String, Supplier<Executor>> EXEC_SUPPLIERS;

    static {
        EXEC_SUPPLIERS = new HashMap<>();
        EXEC_SUPPLIERS.put("server", SERVER_CTX::executor);
        EXEC_SUPPLIERS.put("immediate", Executors::immediate);
        EXEC_SUPPLIERS.put("exec1", EXEC1::getExecutor);
        EXEC_SUPPLIERS.put("exec2", EXEC2::getExecutor);
    }

    private final String path;
    private final Executor routerExecutor;
    private final Executor methodExecutor;
    private final ExpectedExecutor expectedExecutor;
    private final TestMode testMode;
    private final Matcher<String> serverThreadNameMatcher;
    private final Matcher<String> routerThreadNameMatcher;
    private final Matcher<String> methodThreadNameMatcher;

    protected AbstractExecutionStrategyTest(final String path,
                                            final String routerExecutorSupplierId,
                                            final String methodExecutorSupplierId,
                                            final ExpectedExecutor expectedExecutor,
                                            final TestMode testMode) throws Exception {
        this.path = path;
        this.routerExecutor = EXEC_SUPPLIERS.get(routerExecutorSupplierId).get();
        this.methodExecutor = EXEC_SUPPLIERS.get(methodExecutorSupplierId).get();
        this.expectedExecutor = expectedExecutor;
        this.testMode = testMode;

        // Create thread name matchers for the different executors at play in this test
        serverThreadNameMatcher = createExecutorMatcher(getServerExecutionContext().executor());
        routerThreadNameMatcher = createExecutorMatcher(routerExecutor);
        methodThreadNameMatcher = createExecutorMatcher(methodExecutor);
    }

    @Parameters(name = "{0} (r: {1}, m: {2})")
    public static Collection<Object[]> data() {
        // We try different variants to exercise various code paths that are sensitive to request/response entities
        final List<Object[]> data = new ArrayList<>();

        for (String routerExecutorSupplierId : new String[]{"exec1", "immediate"}) {
            for (String methodExecutorSupplierId : EXEC_SUPPLIERS.keySet()) {
                ROOT_PATHS_EXPECTED_EXECS.forEach((rootPath, expectedExecutors) -> {
                    for (int i = 0; i < SUB_PATHS.length; i++) {
                        final int j = i;
                        SUB_SUB_PATH_TEST_MODES.forEach((subSubPath, testMode) ->
                                data.add(new Object[]{
                                        rootPath + SUB_PATHS[j] + subSubPath,
                                        routerExecutorSupplierId,
                                        methodExecutorSupplierId,
                                        expectedExecutors[j],
                                        testMode,
                                }));
                    }
                });
            }
        }

        return data;
    }

    @Override
    protected HttpJerseyRouterBuilder configureBuilder(final HttpJerseyRouterBuilder builder) {
        final Map<String, Executor> executors = new HashMap<>(2);
        executors.put(DEFAULT_EXECUTOR_ID, routerExecutor);
        executors.put("test", methodExecutor);

        return super.configureBuilder(builder).setExecutorFactory(executors::get);
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }

    @Test
    public void testResource() {
        final Matcher<String> expectedExecInfo = getExecutorMatcher(expectedExecutor);
        switch (testMode) {
            case GET:
                sendAndAssertResponse(get(path), OK, APPLICATION_JSON,
                        expectedExecInfo, getJsonResponseContentLengthExtractor());
                break;
            case GET_CHUNKED:
                sendAndAssertResponse(get(path), OK, APPLICATION_JSON,
                        expectedExecInfo, __ -> null);
                break;
            case GET_NEVER_CHUNKED:
                sendAndAssertResponse(get(path), OK, APPLICATION_JSON,
                        expectedExecInfo, String::length);
                break;
            case POST:
                sendAndAssertResponse(post(path, "{\"foo\":\"bar\"}", APPLICATION_JSON), OK, APPLICATION_JSON,
                        expectedExecInfo, getJsonResponseContentLengthExtractor());
                break;
            case POST_CHUNKED:
                sendAndAssertResponse(post(path, "{\"foo\":\"bar\"}", APPLICATION_JSON), OK, APPLICATION_JSON,
                        expectedExecInfo, __ -> null);
                break;
            default:
                throw new IllegalArgumentException(testMode.toString());
        }
    }

    private Matcher<String> getExecutorMatcher(final ExpectedExecutor expectedExecutor) {
        switch (expectedExecutor) {
            case SERVER:
                return serverThreadNameMatcher;
            case ROUTER:
                return routerThreadNameMatcher;
            case METHOD:
                return methodThreadNameMatcher;
            default:
                throw new IllegalArgumentException(expectedExecutor.toString());
        }
    }

    private static Matcher<String> createExecutorMatcher(final Executor exec) throws Exception {
        if (exec.equals(immediate())) {
            return jsonPartMatches(EXEC_NAME, is(exec.toString()));
        }

        final String threadName = exec.submit(() -> Thread.currentThread().getName()).toFuture().get();
        final int i = threadName.indexOf('-');

        return both(JsonMatchers.<String>jsonPartMatches(THREAD_NAME,
                (i > 0 ? startsWith(threadName.substring(0, i)) : is(threadName))))
                .and(jsonPartMatches(EXEC_NAME, is(exec.toString())));
    }
}
