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

import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Test;

import java.security.Principal;
import javax.annotation.Priority;
import javax.management.remote.JMXPrincipal;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.router.jersey.TestUtil.assertResponse;
import static io.servicetalk.http.router.jersey.TestUtil.newH11Request;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;

public class SecurityFilterTest extends AbstractJerseyHttpServiceTest {
    @Provider
    @Priority(AUTHENTICATION)
    public static class TestSecurityFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestCtx) {
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

    public static class TestApplication extends ResourceConfig {
        TestApplication() {
            super(
                    TestSecurityFilter.class,
                    SynchronousResources.class
            );
        }
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }

    @Test
    public void defaultSecurityContext() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, SynchronousResources.PATH + "/security-context");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, APPLICATION_JSON,
                jsonEquals("{\"authenticationScheme\":\"bar\",\"secure\":true,\"userPrincipal\":{\"name\":\"foo\"}}"),
                String::length);
    }
}
