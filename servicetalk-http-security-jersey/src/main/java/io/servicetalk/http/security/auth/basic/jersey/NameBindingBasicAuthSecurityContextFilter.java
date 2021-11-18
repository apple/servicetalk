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

import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.context.api.ContextMap;

import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.Priorities.AUTHENTICATION;

@Provider
@BasicAuthenticated
@Priority(AUTHENTICATION)
final class NameBindingBasicAuthSecurityContextFilter<UserInfo>
        extends AbstractBasicAuthSecurityContextFilter<UserInfo> {

    NameBindingBasicAuthSecurityContextFilter(
            @Nullable final ContextMap.Key<UserInfo> userInfoKey,
            @Nullable final AsyncContextMap.Key<UserInfo> userInfoAcmKey,
            final BiFunction<ContainerRequestContext, UserInfo, SecurityContext> securityContextFunction) {
        super(userInfoKey, userInfoAcmKey, securityContextFunction);
    }
}
