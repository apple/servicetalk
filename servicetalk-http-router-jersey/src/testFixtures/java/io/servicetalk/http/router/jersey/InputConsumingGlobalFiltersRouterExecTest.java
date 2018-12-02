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
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.http.router.jersey.resources.AsynchronousResourcesRouterExec;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceRouterExecIdStrategy;
import io.servicetalk.http.router.jersey.resources.SynchronousResourcesRouterExec;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.DEFAULT_EXECUTOR_ID;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Arrays.asList;

public class InputConsumingGlobalFiltersRouterExecTest extends AbstractFilterInterceptorTest {
    @ClassRule
    public static final ExecutorRule TEST_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("rtr-", true, NORM_PRIORITY)));

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestInputConsumingGlobalFilter.class,
                    SynchronousResourcesRouterExec.class,
                    AsynchronousResourcesRouterExec.class,
                    ResourceRouterExecIdStrategy.class
            ));
        }
    }

    @Override
    protected HttpJerseyRouterBuilder configureBuilder(final HttpJerseyRouterBuilder builder) {
        Map<String, Executor> executors = new HashMap<>();
        executors.put(DEFAULT_EXECUTOR_ID, immediate());
        executors.put("test", TEST_EXEC.getExecutor());
        return super.configureBuilder(builder).executorFactory(executors::get);
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }

    @Test
    public void offloadingToExecutorFails() {
        sendAndAssertResponse(get("/rsc-rtr-exec-id/subrsc-default"), INTERNAL_SERVER_ERROR, null, "");
    }
}
