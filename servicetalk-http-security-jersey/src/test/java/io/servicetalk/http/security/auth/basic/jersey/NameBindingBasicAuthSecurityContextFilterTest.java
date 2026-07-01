/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.security.auth.basic.jersey.resources.GlobalBindingResource;
import io.servicetalk.http.security.auth.basic.jersey.resources.NameBindingResource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static java.util.Collections.singleton;

// application() uses the deprecated no-arg forNameBinding() for the no-user-info case on purpose.
@SuppressWarnings("deprecation")
class NameBindingBasicAuthSecurityContextFilterTest extends AbstractBasicAuthSecurityContextFilterTest {

    @Override
    protected Application application(@Nullable final ContextMap.Key<BasicUserInfo> userInfoKey) {
        return new TestApplication() {
            @Override
            public Set<Object> getSingletons() {
                return singleton(userInfoKey != null ?
                        BasicAuthSecurityContextFilters.forNameBinding(userInfoKey).build() :
                        BasicAuthSecurityContextFilters.forNameBinding().build()
                );
            }
        };
    }

    @Test
    void authenticatedWithUserInfo() throws Exception {
        setUp(true);
        assertBasicAuthSecurityContextAbsent(GlobalBindingResource.PATH, true);
        assertBasicAuthSecurityContextPresent(NameBindingResource.PATH);
    }

    /**
     * No userInfo key wired and no custom principal function: when the upstream {@code BasicAuthHttpServiceFilter}
     * authenticates the request it sets the {@code AUTHENTICATED} marker, so a {@code @BasicAuthenticated} resource
     * proceeds with a {@code null} user principal rather than rejecting an already-authenticated request.
     */
    @Test
    void noUserInfoProceedsWithNullPrincipalWhenAuthenticated() throws Exception {
        setUp(false);
        // Non-@BasicAuthenticated resource: filter does not run, default Jersey context applies.
        assertBasicAuthSecurityContextAbsent(GlobalBindingResource.PATH, true);
        // @BasicAuthenticated resource: filter runs, no identity to publish, proceeds with a null principal.
        assertBasicAuthSecurityContextAbsent(NameBindingResource.PATH, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] withUserInfo={0}, withNewKey={1}")
    @ValueSource(booleans = {true, false})
    void notAuthenticated(final boolean withUserInfo) throws Exception {
        setUp(withUserInfo);
        assertBasicAuthSecurityContextAbsent(GlobalBindingResource.PATH, false);
        assertBasicAuthSecurityContextAbsent(NameBindingResource.PATH, false);
    }
}
