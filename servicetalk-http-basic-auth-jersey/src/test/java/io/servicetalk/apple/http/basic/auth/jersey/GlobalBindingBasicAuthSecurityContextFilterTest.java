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
package io.servicetalk.apple.http.basic.auth.jersey;

import io.servicetalk.apple.http.basic.auth.jersey.resources.AnnotatedResource;
import io.servicetalk.apple.http.basic.auth.jersey.resources.NotAnnotatedResource;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;

import org.junit.Test;

import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static java.util.Collections.singleton;

public class GlobalBindingBasicAuthSecurityContextFilterTest extends AbstractBasicAuthSecurityContextFilterTest {
    public GlobalBindingBasicAuthSecurityContextFilterTest(final boolean withUserInfo) {
        super(withUserInfo);
    }

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

    @Test
    public void authenticated() throws Exception {
        assertBasicAuthSecurityContextPresent(AnnotatedResource.PATH);
        assertBasicAuthSecurityContextPresent(NotAnnotatedResource.PATH);
    }

    @Test
    public void notAuthenticated() throws Exception {
        assertBasicAuthSecurityContextAbsent(AnnotatedResource.PATH, false);
        assertBasicAuthSecurityContextAbsent(NotAnnotatedResource.PATH, false);
    }
}
