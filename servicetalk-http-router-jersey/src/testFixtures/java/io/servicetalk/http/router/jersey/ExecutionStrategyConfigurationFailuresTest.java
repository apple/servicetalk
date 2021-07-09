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
import io.servicetalk.http.router.jersey.ExecutionStrategyTest.TestApplication;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceInvalidExecStrategy;
import io.servicetalk.http.router.jersey.resources.ExecutionStrategyResources.ResourceUnsupportedAsync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.router.jersey.ExecutionStrategyTest.asFactory;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionStrategyConfigurationFailuresTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> TEST_EXEC = ExecutorExtension.withCachedExecutor("test");

    @Test
    void invalidStrategies() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new HttpJerseyRouterBuilder().buildStreaming(new Application() {
                @Override
                public Set<Class<?>> getClasses() {
                    return singleton(ResourceInvalidExecStrategy.class);
                }
            }));

        assertThat(ex.getMessage(), both(containsString("emptyId()"))
            .and(containsString("ResourceInvalidExecStrategy")));
    }

    @Test
    void missingRouteExecId() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new HttpJerseyRouterBuilder().buildStreaming(new TestApplication()));
        assertThat(ex.getMessage(), both(containsString("subResourceDefault()"))
            .and(containsString("subResourceRouteExecId()")));
    }

    @Test
    void jaxRsAsync() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new HttpJerseyRouterBuilder()
                .routeExecutionStrategyFactory(asFactory(
                    singletonMap("test", defaultStrategy(TEST_EXEC.executor()))))
                .buildStreaming(new Application() {
                    @Override
                    public Set<Class<?>> getClasses() {
                        return singleton(ResourceUnsupportedAsync.class);
                    }
                }));

        assertThat(ex.getMessage(), allOf(
            containsString("suspended("),
            containsString("sse("),
            containsString("managed()"),
            containsString("cf()")));
    }
}
