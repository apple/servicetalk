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

import org.junit.Test;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Priority;
import javax.management.remote.JMXPrincipal;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;

public class SecurityFilterTest extends AbstractJerseyStreamingHttpServiceTest {
    @Provider
    @Priority(AUTHENTICATION)
    public static class TestSecurityFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestCtx) {
            if ("true".equals(requestCtx.getUriInfo().getQueryParameters().getFirst("none"))) {
                requestCtx.setSecurityContext(new SecurityContext() {
                    @Override
                    public Principal getUserPrincipal() {
                        return new JMXPrincipal("none");
                    }

                    @Override
                    public boolean isUserInRole(final String role) {
                        return false;
                    }

                    @Override
                    public boolean isSecure() {
                        return false;
                    }

                    @Override
                    public String getAuthenticationScheme() {
                        return "none";
                    }
                });
                return;
            }
            requestCtx.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return new JMXPrincipal("foo");
                }

                @Override
                public boolean isUserInRole(final String role) {
                    return false;
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public String getAuthenticationScheme() {
                    return "bar";
                }
            });
        }
    }

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestSecurityFilter.class,
                    SynchronousResources.class
            ));
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @Test
    public void defaultSecurityContext() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get(SynchronousResources.PATH + "/security-context"), OK, APPLICATION_JSON,
                    jsonEquals("{\"authenticationScheme\":\"bar\",\"secure\":true," +
                            "\"userPrincipal\":{\"name\":\"foo\"}}"), getJsonResponseContentLengthExtractor());

            sendAndAssertResponse(get(SynchronousResources.PATH + "/security-context?none=true"), OK, APPLICATION_JSON,
                    jsonEquals("{\"authenticationScheme\":\"none\",\"secure\":false," +
                            "\"userPrincipal\":{\"name\":\"none\"}}"), getJsonResponseContentLengthExtractor());
        });
    }
}
