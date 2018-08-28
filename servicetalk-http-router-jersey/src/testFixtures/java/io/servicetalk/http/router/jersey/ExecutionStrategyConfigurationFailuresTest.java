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
import io.servicetalk.http.router.jersey.AbstractExecutionStrategyTest.TestApplication;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceInvalidExecStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceUnsupportedAsync;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.DEFAULT_EXECUTOR_ID;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Collections.singleton;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;

public class ExecutionStrategyConfigurationFailuresTest {
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @ClassRule
    public static final ExecutorRule ROUTER_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("rtr-", true, NORM_PRIORITY)));

    @ClassRule
    public static final ExecutorRule TEST_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("test-", true, NORM_PRIORITY)));

    @Test
    public void invalidStrategies() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(both(containsString("defaultStrategy()"))
                .and(containsString("idWithServerExec()")));

        new HttpJerseyRouterBuilder().build(new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return singleton(ResourceInvalidExecStrategy.class);
            }
        });
    }

    @Test
    public void missingRouterExec() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(both(containsString("subResourceDefault()"))
                .and(containsString("subResourceRouterExec()")));

        new HttpJerseyRouterBuilder()
                .setExecutorFactory(id -> "test".equals(id) ? TEST_EXEC.getExecutor() : null)
                .build(new TestApplication());
    }

    @Test
    public void missingRouterExecId() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(both(containsString("subResourceDefault()"))
                .and(containsString("subResourceRouterExecId()")));

        new HttpJerseyRouterBuilder()
                .setExecutorFactory(id -> DEFAULT_EXECUTOR_ID.equals(id) ? ROUTER_EXEC.getExecutor() : null)
                .build(new TestApplication());
    }

    @Test
    public void jaxRsAsync() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(allOf(
                containsString("suspended("),
                containsString("sse("),
                containsString("managed()"),
                containsString("cf()")));

        new HttpJerseyRouterBuilder()
                .setExecutorFactory(id -> DEFAULT_EXECUTOR_ID.equals(id) ? ROUTER_EXEC.getExecutor() : null)
                .build(new Application() {
                    @Override
                    public Set<Class<?>> getClasses() {
                        return singleton(ResourceUnsupportedAsync.class);
                    }
                });
    }
}
