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
import io.servicetalk.http.security.auth.basic.jersey.BasicAuthSecurityContextFilters.NoUserInfoBuilder;
import io.servicetalk.http.security.auth.basic.jersey.BasicAuthSecurityContextFilters.UserInfoBuilder;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.security.Principal;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.security.auth.basic.jersey.BasicAuthSecurityContextFilters.ANONYMOUS_PRINCIPAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasicAuthSecurityContextFiltersTest {
    private static final Principal TEST_PRINCIPAL = () -> "test-name";
    private static final String TEST_USER_INFO = "test-user-info";

    @Mock
    private ContainerRequestContext requestCtx;

    @Mock
    private UriInfo uriInfo;

    private boolean globalFilter;

    @BeforeEach
    void setupMocks() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://0.0.0.0"));
        when(requestCtx.getUriInfo()).thenReturn(uriInfo);
    }

    @AfterEach
    void clearAsyncContext() {
        AsyncContext.clear();
    }

    /**
     * Marks the current request as authenticated, mimicking what {@code BasicAuthHttpServiceFilter} does upstream on a
     * successful authentication.
     */
    private static void authenticate() {
        AsyncContext.put(BasicAuthHttpServiceFilter.AUTHENTICATED, Boolean.TRUE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notAuthenticatedAbortsWith401(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final ContainerRequestFilter filter = newFilterBuilder().build();

        // No AUTHENTICATED marker in AsyncContext: BasicAuthHttpServiceFilter did not run upstream.
        filter.filter(requestCtx);

        final ArgumentCaptor<Response> respCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestCtx).abortWith(respCaptor.capture());
        verify(requestCtx, never()).setSecurityContext(any(SecurityContext.class));
        assertThat(respCaptor.getValue().getStatus(),
                is(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authenticatedNoUserInfoInstallsAnonymousPrincipal(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final ContainerRequestFilter filter = newFilterBuilder().build();
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);

        authenticate();
        filter.filter(requestCtx);

        // Authenticated upstream and no user info to publish: install the anonymous principal.
        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(ANONYMOUS_PRINCIPAL)));
        verify(requestCtx, never()).abortWith(any(Response.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void principalUserInfo(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final ContextMap.Key<Principal> userInfoKey = newKey("basicPrincipal", Principal.class);
        final ContainerRequestFilter filter = newFilterBuilder(userInfoKey).build();

        // Authenticated, but no user info is available under the configured key: proceed with a null principal.
        authenticate();
        filter.filter(requestCtx);
        verify(requestCtx, never()).setSecurityContext(any(SecurityContext.class));
        verify(requestCtx, never()).abortWith(any(Response.class));
        clearInvocations(requestCtx);

        AsyncContext.put(userInfoKey, TEST_PRINCIPAL);
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(TEST_PRINCIPAL)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void customPrincipalFunctionNoUserInfo(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final ContainerRequestFilter filter = newFilterBuilder()
                .principalFunction(__ -> TEST_PRINCIPAL)
                .build();

        authenticate();
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(TEST_PRINCIPAL)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void customPrincipalFunctionUserInfo(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final ContextMap.Key<String> userInfoKey = newKey("basicPrincipal", String.class);
        final ContainerRequestFilter filter = newFilterBuilder(userInfoKey)
                .principalFunction((__, userInfo) -> TEST_USER_INFO.equals(userInfo) ? TEST_PRINCIPAL : null)
                .build();

        authenticate();
        filter.filter(requestCtx);
        verify(requestCtx, never()).setSecurityContext(any(SecurityContext.class));
        clearInvocations(requestCtx);

        AsyncContext.put(userInfoKey, TEST_USER_INFO);
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(TEST_PRINCIPAL)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void customSecurityContextFunctionNoUserInfo(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final SecurityContext securityContext = mock(SecurityContext.class);

        final ContainerRequestFilter filter = newFilterBuilder()
                .securityContextFunction(__ -> securityContext)
                .build();

        authenticate();
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue(), is(sameInstance(securityContext)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void customSecurityContextFunctionUserInfo(final boolean globalFilter) throws Exception {
        this.globalFilter = globalFilter;
        final SecurityContext securityContext = mock(SecurityContext.class);

        final ContextMap.Key<String> userInfoKey = newKey("basicPrincipal", String.class);
        final ContainerRequestFilter filter = newFilterBuilder(userInfoKey)
                .securityContextFunction((__, userInfo) -> TEST_USER_INFO.equals(userInfo) ? securityContext : null)
                .build();

        authenticate();
        filter.filter(requestCtx);
        verify(requestCtx, never()).setSecurityContext(any(SecurityContext.class));
        clearInvocations(requestCtx);

        AsyncContext.put(userInfoKey, TEST_USER_INFO);
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue(), is(sameInstance(securityContext)));
    }

    private NoUserInfoBuilder newFilterBuilder() {
        return globalFilter ? BasicAuthSecurityContextFilters.forGlobalBinding() :
                BasicAuthSecurityContextFilters.forNameBinding();
    }

    private <UserInfo> UserInfoBuilder<UserInfo> newFilterBuilder(final ContextMap.Key<UserInfo> userInfoKey) {
        return globalFilter ? BasicAuthSecurityContextFilters.forGlobalBinding(userInfoKey) :
                BasicAuthSecurityContextFilters.forNameBinding(userInfoKey);
    }
}
