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
package io.servicetalk.http.security.auth.basic.jersey;

import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.http.security.auth.basic.jersey.resources.RoleProtectedResource;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpResponseStatus.FORBIDDEN;

public class RoleProtectedBasicAuthSecurityContextFilterTest extends AbstractBasicAuthSecurityContextFilterTest {
    public RoleProtectedBasicAuthSecurityContextFilterTest(final boolean withUserInfo) {
        super(withUserInfo);
    }

    @Override
    protected Application application(@Nullable final Key<BasicUserInfo> userInfoKey) {
        return new ResourceConfig(RoleProtectedResource.class)
                .register(RolesAllowedDynamicFeature.class)
                .registerInstances(userInfoKey != null ?
                        BasicAuthSecurityContextFilters.forGlobalBinding(userInfoKey)
                                .securityContextFunction((__, userInfo) ->
                                        new BasicAuthSecurityContext(new BasicAuthPrincipal<>(userInfo), false,
                                                role -> userInfo.roles().contains(role)))
                                .build() :
                        BasicAuthSecurityContextFilters.forGlobalBinding()
                                .securityContextFunction(__ ->
                                        new BasicAuthSecurityContext(() -> "N/A", false, "USER"::equals))
                                .build());
    }

    @Test
    public void authorized() throws Exception {
        assertBasicAuthSecurityContextPresent(RoleProtectedResource.PATH + "/user");
    }

    @Test
    public void notAuthorized() throws Exception {
        assertBasicAuthSecurityContextAbsent(RoleProtectedResource.PATH + "/admin", true, FORBIDDEN);
    }

    @Test
    public void notAuthenticated() throws Exception {
        assertBasicAuthSecurityContextAbsent(RoleProtectedResource.PATH + "/user", false);
        assertBasicAuthSecurityContextAbsent(RoleProtectedResource.PATH + "/admin", false);
    }
}
