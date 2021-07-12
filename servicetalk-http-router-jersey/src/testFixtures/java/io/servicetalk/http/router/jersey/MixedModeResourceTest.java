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
package io.servicetalk.http.router.jersey;

import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.jersey.resources.MixedModeResources;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.AbstractResourceTest.assumeSafeToDisableOffloading;
import static java.util.Collections.singleton;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;

class MixedModeResourceTest extends AbstractJerseyStreamingHttpServiceTest {
    protected void setUp(final RouterApi api) throws Exception {
        super.setUp(api);
        assumeSafeToDisableOffloading(true, api);
    }

    @Override
    protected Application application() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return singleton(MixedModeResources.class);
            }
        };
    }

    @Override
    void configureBuilders(final HttpServerBuilder serverBuilder,
                           final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        super.configureBuilders(serverBuilder, jerseyRouterBuilder);
        serverBuilder.executionStrategy(noOffloadsStrategy());
    }

    @Override
    ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                              final HttpService router) throws Exception {
        return httpServerBuilder.listenStreamingAndAwait(new HttpPredicateRouterBuilder()
                // No-offloads can not be used with CompletionStage responses (and also with @Suspended AsyncResponse
                // and SSE), so we override the strategy for this particular path by simply routing to it from
                // the predicate router, which will use the appropriate default strategy.
                .whenPathEquals(MixedModeResources.PATH + "/cs-string")
                .thenRouteTo(router)
                .when(__ -> true)
                .executionStrategy(noOffloadsStrategy())
                .thenRouteTo(router)
                .buildStreaming());
    }

    @Override
    ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                              final StreamingHttpService router) throws Exception {
        return httpServerBuilder.listenStreamingAndAwait(new HttpPredicateRouterBuilder()
                // No-offloads can not be used with CompletionStage responses (and also with @Suspended AsyncResponse
                // and SSE), so we override the strategy for this particular path by simply routing to it from
                // the predicate router, which will use the appropriate default strategy.
                .whenPathEquals(MixedModeResources.PATH + "/cs-string")
                .thenRouteTo(router)
                .when(__ -> true)
                .executionStrategy(noOffloadsStrategy())
                .thenRouteTo(router)
                .buildStreaming());
    }

    @Override
    ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                              final BlockingHttpService router) throws Exception {
        return httpServerBuilder.listenStreamingAndAwait(new HttpPredicateRouterBuilder()
                // No-offloads can not be used with CompletionStage responses (and also with @Suspended AsyncResponse
                // and SSE), so we override the strategy for this particular path by simply routing to it from
                // the predicate router, which will use the appropriate default strategy.
                .whenPathEquals(MixedModeResources.PATH + "/cs-string")
                .thenRouteTo(router)
                .when(__ -> true)
                .executionStrategy(noOffloadsStrategy())
                .thenRouteTo(router)
                .buildStreaming());
    }

    @Override
    ServerContext buildRouter(final HttpServerBuilder httpServerBuilder,
                              final BlockingStreamingHttpService router) throws Exception {
        return httpServerBuilder.listenStreamingAndAwait(new HttpPredicateRouterBuilder()
                // No-offloads can not be used with CompletionStage responses (and also with @Suspended AsyncResponse
                // and SSE), so we override the strategy for this particular path by simply routing to it from
                // the predicate router, which will use the appropriate default strategy.
                .whenPathEquals(MixedModeResources.PATH + "/cs-string")
                .thenRouteTo(router)
                .when(__ -> true)
                .executionStrategy(noOffloadsStrategy())
                .thenRouteTo(router)
                .buildStreaming());
    }

    @Override
    protected String testUri(final String path) {
        return MixedModeResources.PATH + path;
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void noOffloadsIsSupported(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/string"), OK, TEXT_PLAIN,
                    stringContainsInOrder(singleton("stserverio")), String::length);

            sendAndAssertResponse(get("/single-string"), OK, TEXT_PLAIN,
                    stringContainsInOrder(singleton("stserverio")), String::length);
        });
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void noOffloadsOverrideIsSupported(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/cs-string"), OK, TEXT_PLAIN,
                        not(stringContainsInOrder(singleton("stserverio"))), String::length));
    }
}
