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

import java.security.Principal;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.ws.rs.core.SecurityContext;

import static java.util.Objects.requireNonNull;

/**
 * A generic {@link SecurityContext} that wraps a user provided {@link Principal},
 * and which is designed for the {@link SecurityContext#BASIC_AUTH} authentication scheme.
 */
public final class BasicAuthSecurityContext implements SecurityContext {
    private final Principal principal;
    private final boolean secure;

    @Nullable
    private final Predicate<String> userInRolePredicate;

    /**
     * Creates a new instance, which has no support for roles.
     *
     * @param principal the wrapped {@link Principal}
     * @param secure {@code true} if the request was received over a secure channel
     * @see SecurityContext#isSecure()
     */
    public BasicAuthSecurityContext(final Principal principal,
                                    final boolean secure) {
        this(principal, secure, null);
    }

    /**
     * Creates a new instance.
     *
     * @param principal the wrapped {@link Principal}.
     * @param secure {@code true} if the request was received over a secure channel
     * @param userInRolePredicate the {@link Predicate} used to check if the user is in a role
     * @see SecurityContext#isSecure()
     */
    public BasicAuthSecurityContext(final Principal principal,
                                    final boolean secure,
                                    @Nullable final Predicate<String> userInRolePredicate) {

        this.principal = requireNonNull(principal);
        this.secure = secure;
        this.userInRolePredicate = userInRolePredicate;
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(final String role) {
        return userInRolePredicate != null && userInRolePredicate.test(role);
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getAuthenticationScheme() {
        return BASIC_AUTH;
    }
}
