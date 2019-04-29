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
import io.servicetalk.http.security.auth.basic.jersey.resources.AnnotatedResource;
import io.servicetalk.http.security.auth.basic.jersey.resources.NotAnnotatedResource;

import org.junit.Test;

import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static java.util.Collections.singleton;

public class NameBindingBasicAuthSecurityContextFilterTest extends AbstractBasicAuthSecurityContextFilterTest {
    public NameBindingBasicAuthSecurityContextFilterTest(final boolean withUserInfo) {
        super(withUserInfo);
    }

    @Override
    protected Application application(@Nullable final Key<BasicUserInfo> userInfoKey) {
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
    public void authenticated() throws Exception {
        assertBasicAuthSecurityContextPresent(AnnotatedResource.PATH);
        assertBasicAuthSecurityContextAbsent(NotAnnotatedResource.PATH, true);
    }

    @Test
    public void notAuthenticated() throws Exception {
        assertBasicAuthSecurityContextAbsent(AnnotatedResource.PATH, false);
        assertBasicAuthSecurityContextAbsent(NotAnnotatedResource.PATH, false);
    }
}
