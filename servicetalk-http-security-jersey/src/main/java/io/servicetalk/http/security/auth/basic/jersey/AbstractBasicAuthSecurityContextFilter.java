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
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;

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
    private static final AtomicBoolean NO_USER_INFO_WARNED = new AtomicBoolean();

    AbstractBasicAuthSecurityContextFilter(
            @Nullable final ContextMap.Key<UserInfo> userInfoKey,
            final BiFunction<ContainerRequestContext, UserInfo, SecurityContext> securityContextFunction) {
        this.userInfoKey = userInfoKey;
        this.securityContextFunction = securityContextFunction;
    }

    @Override
    public void filter(final ContainerRequestContext requestCtx) {
        if (!Boolean.TRUE.equals(AsyncContext.get(BasicAuthHttpServiceFilter.AUTHENTICATED))) {
            // Not authenticated by an upstream BasicAuthHttpServiceFilter (missing filter or disabled AsyncContext):
            // fail closed rather than exposing a @BasicAuthenticated resource to an unauthenticated caller.
            LOGGER.warn("Rejecting request to a @BasicAuthenticated resource with 401: the request was not " +
                    "authenticated by a BasicAuthHttpServiceFilter. Ensure BasicAuthHttpServiceFilter is " +
                    "installed upstream of the Jersey router (e.g. via HttpServerBuilder#appendServiceFilter) " +
                    "and that AsyncContext is enabled.");
            requestCtx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        final SecurityContext securityContext = securityContext(requestCtx);
        if (securityContext != null) {
            requestCtx.setSecurityContext(securityContext);
        } else if (userInfoKey != null && NO_USER_INFO_WARNED.compareAndSet(false, true)) {
            // Authenticated, but no user info under the configured key (usually a key mismatch); warn once
            LOGGER.warn("Request authenticated by BasicAuthHttpServiceFilter but no user info was found under the " +
                    "configured key in AsyncContext; proceeding with a null user principal. Verify that the key " +
                    "passed to BasicAuthSecurityContextFilters.forNameBinding/forGlobalBinding(...) matches " +
                    "BasicAuthHttpServiceFilter.Builder#userInfoAsyncContextKey(...). Further occurrences will not " +
                    "be logged.");
        }
        // No SecurityContext was resolved above, so the request proceeds with a null user principal. The
        // no-user-info builder never reaches here: its securityContextFunction always resolves an anonymous principal.
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
