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

import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static javax.ws.rs.core.Response.Status.PAYMENT_REQUIRED;
import static javax.ws.rs.core.Response.status;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;

class IllBehavedRequestFilterTest extends AbstractJerseyStreamingHttpServiceTest {

    @Provider
    @Priority(AUTHENTICATION)
    public static class IllBehavedRequestFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestCtx) throws IOException {
            // ContainerRequestFilter should replace the entity stream with a filtered one based on the original entity
            // stream (see AbstractFilterInterceptorTest for examples of well-behaved filters).
            int read = requestCtx.getEntityStream().read();
            if (read != 'x') {
                // 402 so it's distinguishable from 400 and 500 that the server could respond
                requestCtx.abortWith(status(PAYMENT_REQUIRED).build());
            }
        }
    }

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    IllBehavedRequestFilter.class,
                    SynchronousResources.class
            ));
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void inputStreamConsumingResource(RouterApi api) throws Exception {
        setUp(api);
        sendAndAssertResponse(post(SynchronousResources.PATH + "/json-mapin-pubout", "x{\"key\":\"val2\"}",
                APPLICATION_JSON), OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val2\",\"foo\":\"bar3\"}"), __ -> null);
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void publisherConsumingResource(RouterApi api) throws Exception {
        setUp(api);
        sendAndAssertNoResponse(post(SynchronousResources.PATH + "/json-pubin-pubout", "x{\"key\":\"val4\"}",
                APPLICATION_JSON), INTERNAL_SERVER_ERROR);
    }
}
