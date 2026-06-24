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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

abstract class AbstractBasicAuthSecurityContextFilter<UserInfo> implements ContainerRequestFilter {
    @Nullable
    private final ContextMap.Key<UserInfo> userInfoKey;
    private final BiFunction<ContainerRequestContext, UserInfo, SecurityContext> securityContextFunction;

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBasicAuthSecurityContextFilter.class);
    private static final AtomicBoolean MISCONFIG_WARNED = new AtomicBoolean();

    AbstractBasicAuthSecurityContextFilter(
            @Nullable final ContextMap.Key<UserInfo> userInfoKey,
            final BiFunction<ContainerRequestContext, UserInfo, SecurityContext> securityContextFunction) {
        this.userInfoKey = userInfoKey;
        this.securityContextFunction = securityContextFunction;
    }

    @Override
    public void filter(final ContainerRequestContext requestCtx) {
        final SecurityContext securityContext = securityContext(requestCtx);
        if (securityContext != null) {
            requestCtx.setSecurityContext(securityContext);
        } else {
            if (MISCONFIG_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("Rejecting request to a @BasicAuthenticated resource with 401: " +
                        "no UserInfo present in AsyncContext. This usually means " +
                        "BasicAuthHttpServiceFilter is not installed upstream of the Jersey router, " +
                        "or its userInfoAsyncContextKey does not match the key passed to " +
                        "BasicAuthSecurityContextFilters.forNameBinding/forGlobalBinding(...). " +
                        "Further occurrences will not be logged.");
            }
            requestCtx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    @Nullable
    private SecurityContext securityContext(final ContainerRequestContext requestCtx) {
        if (userInfoKey != null) {
            final UserInfo userInfo = AsyncContext.get(userInfoKey);
            return userInfo == null ? null : securityContextFunction.apply(requestCtx, userInfo);
        } else {
            return securityContextFunction.apply(requestCtx, null);
        }
    }
}
