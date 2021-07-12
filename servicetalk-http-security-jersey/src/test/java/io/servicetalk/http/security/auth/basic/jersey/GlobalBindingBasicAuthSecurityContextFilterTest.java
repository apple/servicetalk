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
import io.servicetalk.http.security.auth.basic.jersey.resources.GlobalBindingResource;
import io.servicetalk.http.security.auth.basic.jersey.resources.NameBindingResource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static java.util.Collections.singleton;

class GlobalBindingBasicAuthSecurityContextFilterTest extends AbstractBasicAuthSecurityContextFilterTest {

    @Override
    protected Application application(@Nullable final Key<BasicUserInfo> userInfoKey) {
        return new TestApplication() {
            @Override
            public Set<Object> getSingletons() {
                return singleton(userInfoKey != null ?
                        BasicAuthSecurityContextFilters.forGlobalBinding(userInfoKey).build() :
                        BasicAuthSecurityContextFilters.forGlobalBinding().build()
                );
            }
        };
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authenticated(final boolean withUserInfo) throws Exception {
        setUp(withUserInfo);
        assertBasicAuthSecurityContextPresent(GlobalBindingResource.PATH);
        assertBasicAuthSecurityContextPresent(NameBindingResource.PATH);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notAuthenticated(final boolean withUserInfo) throws Exception {
        setUp(withUserInfo);
        assertBasicAuthSecurityContextAbsent(GlobalBindingResource.PATH, false);
        assertBasicAuthSecurityContextAbsent(NameBindingResource.PATH, false);
    }
}
