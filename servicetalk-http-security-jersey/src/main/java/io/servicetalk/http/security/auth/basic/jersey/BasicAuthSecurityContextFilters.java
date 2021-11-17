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

import java.security.Principal;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.SecurityContext;

import static java.util.Objects.requireNonNull;

/**
 * Factory methods for building {@link ContainerRequestFilter} that establish {@link SecurityContext}s for requests
 * authenticated with the Basic HTTP Authentication Scheme (<a href="https://tools.ietf.org/html/rfc7617">RFC7617</a>).
 */
public final class BasicAuthSecurityContextFilters {
    /**
     * A builder for {@link ContainerRequestFilter} instances.
     *
     * @param <B> the concrete builder type
     * @param <PF> the principal function type
     * @param <SCF> the security context function type
     */
    @SuppressWarnings("rawtypes")
    public abstract static class AbstractBuilder<B extends AbstractBuilder, PF, SCF> {
        private final Function<PF, SCF> principalToSecurityContextFunction;

        private SCF securityContextFunction;

        AbstractBuilder(final Function<PF, SCF> principalToSecurityContextFunction,
                        final SCF securityContextFunction) {
            this.principalToSecurityContextFunction = principalToSecurityContextFunction;
            this.securityContextFunction = securityContextFunction;
        }

        SCF securityContextFunction() {
            return securityContextFunction;
        }

        /**
         * Specify a custom function the filter will use to create {@link Principal} instances.
         * <p>
         * This value will override a function specified with {@link AbstractBuilder#securityContextFunction}.
         *
         * @param principalFunction the custom {@link Principal} function
         * @return this
         */
        @SuppressWarnings("unchecked")
        public B principalFunction(final PF principalFunction) {
            securityContextFunction(principalToSecurityContextFunction.apply(requireNonNull(principalFunction)));
            return (B) this;
        }

        /**
         * Specify a custom function the filter will use to use to create {@link SecurityContext} instances.
         * <p>
         * This value will override a function specified with {@link AbstractBuilder#principalFunction}.
         *
         * @param securityContextFunction the custom {@link SecurityContext} function
         * @return this.
         */
        @SuppressWarnings("unchecked")
        public B securityContextFunction(final SCF securityContextFunction) {
            this.securityContextFunction = requireNonNull(securityContextFunction);
            return (B) this;
        }

        /**
         * Build a new {@link ContainerRequestFilter} instance.
         *
         * @return a new {@link ContainerRequestFilter} instance.
         */
        public abstract ContainerRequestFilter build();
    }

    /**
     * A builder that can be used when user info is stored in {@link AsyncContext}.
     * <p>
     * For example, it can be used with {@code io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter} configured
     * with {@code userInfoAsyncContextKey(ContextMap.Key)} at the builder.
     *
     * @param <UserInfo> the type of user info object expected in {@link AsyncContext}'s {@code userInfoKey} entry
     */
    public abstract static class UserInfoBuilder<UserInfo> extends AbstractBuilder<UserInfoBuilder<UserInfo>,
            BiFunction<ContainerRequestContext, UserInfo, Principal>,
            BiFunction<ContainerRequestContext, UserInfo, SecurityContext>> {

        UserInfoBuilder() {
            super(BasicAuthSecurityContextFilters::asSecurityContextBiFunction,
                    BasicAuthSecurityContextFilters::newSecurityContext);
        }
    }

    /**
     * A builder that can be used when no user info is stored in {@link AsyncContext}.
     */
    public abstract static class NoUserInfoBuilder extends AbstractBuilder<NoUserInfoBuilder,
            Function<ContainerRequestContext, Principal>,
            Function<ContainerRequestContext, SecurityContext>> {

        NoUserInfoBuilder() {
            super(BasicAuthSecurityContextFilters::asSecurityContextFunction,
                    BasicAuthSecurityContextFilters::newAnonymousSecurityContext);
        }
    }

    // Visible for testing
    static final Principal ANONYMOUS_PRINCIPAL = () -> "ANONYMOUS";

    private BasicAuthSecurityContextFilters() {
        // no instances
    }

    /**
     * Creates a new {@link UserInfoBuilder} instance for building a {@link ContainerRequestFilter} that needs to be
     * globally bound to the JAX-RS {@link Application}.
     *
     * @param userInfoKey the {@link ContextMap.Key} to use to get the user info from {@link AsyncContext}
     * @param <UserInfo> the type of user info object expected in {@link AsyncContext}'s {@code userInfoKey} entry
     * @return a new {@link UserInfoBuilder} instance
     */
    public static <UserInfo> UserInfoBuilder<UserInfo> forGlobalBinding(final ContextMap.Key<UserInfo> userInfoKey) {
        requireNonNull(userInfoKey);
        return new UserInfoBuilder<UserInfo>() {
            @Override
            public ContainerRequestFilter build() {
                return new GlobalBindingBasicAuthSecurityContextFilter<>(userInfoKey, securityContextFunction());
            }
        };
    }

    /**
     * Creates a new {@link NoUserInfoBuilder} instance for building a {@link ContainerRequestFilter} that needs to be
     * globally bound to the JAX-RS {@link Application}.
     *
     * @return a new {@link NoUserInfoBuilder} instance
     */
    public static NoUserInfoBuilder forGlobalBinding() {
        return new NoUserInfoBuilder() {
            @Override
            public ContainerRequestFilter build() {
                return new GlobalBindingBasicAuthSecurityContextFilter<Void>(null,
                        asSecurityContextBiFunction(securityContextFunction()));
            }
        };
    }

    /**
     * Creates a new {@link UserInfoBuilder} instance for building a {@link ContainerRequestFilter} that needs to be
     * explicitly bound to resources via the {@link BasicAuthenticated} annotation.
     *
     * @param userInfoKey the {@link ContextMap.Key} to use to get the user info from {@link AsyncContext}
     * @param <UserInfo> the type of user info object expected in {@link AsyncContext}'s {@code userInfoKey} entry
     * @return a new {@link UserInfoBuilder} instance
     */
    public static <UserInfo> UserInfoBuilder<UserInfo> forNameBinding(final ContextMap.Key<UserInfo> userInfoKey) {
        requireNonNull(userInfoKey);
        return new UserInfoBuilder<UserInfo>() {
            @Override
            public ContainerRequestFilter build() {
                return new NameBindingBasicAuthSecurityContextFilter<>(userInfoKey, securityContextFunction());
            }
        };
    }

    /**
     * Creates a new {@link NoUserInfoBuilder} instance for building a {@link ContainerRequestFilter} that needs to be
     * explicitly bound to resources via the {@link BasicAuthenticated} annotation.
     *
     * @return a new {@link NoUserInfoBuilder} instance
     */
    public static NoUserInfoBuilder forNameBinding() {
        return new NoUserInfoBuilder() {
            @Override
            public ContainerRequestFilter build() {
                return new NameBindingBasicAuthSecurityContextFilter<Void>(null,
                        asSecurityContextBiFunction(securityContextFunction()));
            }
        };
    }

    private static SecurityContext newAnonymousSecurityContext(final ContainerRequestContext requestCtx) {
        return new BasicAuthSecurityContext(ANONYMOUS_PRINCIPAL, isRequestSecure(requestCtx));
    }

    private static <UserInfo> SecurityContext newSecurityContext(final ContainerRequestContext requestCtx,
                                                                 final UserInfo userInfo) {
        return new BasicAuthSecurityContext(newPrincipal(requestCtx, userInfo), isRequestSecure(requestCtx));
    }

    private static Function<ContainerRequestContext, SecurityContext> asSecurityContextFunction(
            final Function<ContainerRequestContext, Principal> principalFunction) {
        return requestCtx -> new BasicAuthSecurityContext(principalFunction.apply(requestCtx),
                isRequestSecure(requestCtx));
    }

    private static <UserInfo> BiFunction<ContainerRequestContext, UserInfo, SecurityContext>
    asSecurityContextBiFunction(final BiFunction<ContainerRequestContext, UserInfo, Principal> principalFunction) {

        return (requestCtx, userInfo) -> new BasicAuthSecurityContext(principalFunction.apply(requestCtx, userInfo),
                isRequestSecure(requestCtx));
    }

    private static <UserInfo> BiFunction<ContainerRequestContext, UserInfo, SecurityContext>
    asSecurityContextBiFunction(final Function<ContainerRequestContext, SecurityContext> securityContextFunction) {

        return (requestCtx, __) -> securityContextFunction.apply(requestCtx);
    }

    private static <UserInfo> Principal newPrincipal(final ContainerRequestContext __,
                                                     final UserInfo userInfo) {
        return userInfo instanceof Principal ? (Principal) userInfo : new BasicAuthPrincipal<>(userInfo);
    }

    private static boolean isRequestSecure(final ContainerRequestContext requestCtx) {
        return "https".equalsIgnoreCase(requestCtx.getUriInfo().getRequestUri().getScheme());
    }
}
