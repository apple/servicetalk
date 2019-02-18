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
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestApplication;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceInvalidExecStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceUnsupportedAsync;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.asFactory;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;

public class ExecutionStrategyConfigurationFailuresTest {
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @ClassRule
    public static final ExecutorRule TEST_EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("test-", true, NORM_PRIORITY)));

    @Test
    public void invalidStrategies() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(both(containsString("emptyId()"))
                .and(containsString("ResourceInvalidExecStrategy")));

        new HttpJerseyRouterBuilder().build(new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return singleton(ResourceInvalidExecStrategy.class);
            }
        });
    }

    @Test
    public void missingRouteExecId() {
        expected.expect(IllegalArgumentException.class);
        expected.expectMessage(both(containsString("subResourceDefault()"))
                .and(containsString("subResourceRouteExecId()")));

        new HttpJerseyRouterBuilder().build(new TestApplication());
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
                .routeExecutionStrategyFactory(asFactory(
                        singletonMap("test", defaultStrategy(TEST_EXEC.executor()))))
                .build(new Application() {
                    @Override
                    public Set<Class<?>> getClasses() {
                        return singleton(ResourceUnsupportedAsync.class);
                    }
                });
    }
}
