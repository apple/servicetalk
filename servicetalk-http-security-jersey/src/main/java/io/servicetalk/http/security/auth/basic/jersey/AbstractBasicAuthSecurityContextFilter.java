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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;

import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;

abstract class AbstractBasicAuthSecurityContextFilter<UserInfo> implements ContainerRequestFilter {
    @Nullable
    private final Key<UserInfo> userInfoKey;
    private final BiFunction<ContainerRequestContext, UserInfo, SecurityContext> securityContextFunction;

    AbstractBasicAuthSecurityContextFilter(
            @Nullable final Key<UserInfo> userInfoKey,
            final BiFunction<ContainerRequestContext, UserInfo, SecurityContext> securityContextFunction) {
        this.userInfoKey = userInfoKey;
        this.securityContextFunction = securityContextFunction;
    }

    @Override
    public void filter(final ContainerRequestContext requestCtx) {
        final SecurityContext securityContext = securityContext(requestCtx);
        if (securityContext != null) {
            requestCtx.setSecurityContext(securityContext);
        }
    }

    @Nullable
    private SecurityContext securityContext(final ContainerRequestContext requestCtx) {
        if (userInfoKey == null) {
            return securityContextFunction.apply(requestCtx, null);
        }

        final UserInfo userInfo = AsyncContext.get(userInfoKey);
        if (userInfo == null) {
            return null;
        }

        return securityContextFunction.apply(requestCtx, userInfo);
    }
}
