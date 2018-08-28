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

import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.http.router.jersey.resources.SynchronousResourcesRouterExec;

import org.junit.ClassRule;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.DEFAULT_EXECUTOR_ID;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Arrays.asList;

public class SynchronousResourceRouterExecTest extends SynchronousResourceTest {
    @ClassRule
    public static final ExecutorRule ROUTER_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("rtr-", true, NORM_PRIORITY)));

    @Override
    protected HttpJerseyRouterBuilder configureBuilder(final HttpJerseyRouterBuilder builder) {
        return super.configureBuilder(builder)
                .setExecutorFactory(id -> DEFAULT_EXECUTOR_ID.equals(id) ? ROUTER_EXEC.getExecutor() : null);
    }

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestFilter.class,
                    SynchronousResourcesRouterExec.class
            ));
        }
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }
}
