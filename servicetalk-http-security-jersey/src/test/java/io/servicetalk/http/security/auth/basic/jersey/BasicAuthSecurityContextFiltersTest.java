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
import io.servicetalk.http.security.auth.basic.jersey.BasicAuthSecurityContextFilters.NoUserInfoBuilder;
import io.servicetalk.http.security.auth.basic.jersey.BasicAuthSecurityContextFilters.UserInfoBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.URI;
import java.security.Principal;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.http.security.auth.basic.jersey.BasicAuthSecurityContextFilters.ANONYMOUS_PRINCIPAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class BasicAuthSecurityContextFiltersTest {
    private static final Principal TEST_PRINCIPAL = () -> "test-name";
    private static final String TEST_USER_INFO = "test-user-info";
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ContainerRequestContext requestCtx;

    @Mock
    private UriInfo uriInfo;

    private final boolean globalFilter;

    public BasicAuthSecurityContextFiltersTest(final boolean globalFilter) {
        this.globalFilter = globalFilter;
    }

    @Parameters(name = " {index} global filter? {0}")
    public static Object[] params() {
        return new Object[]{true, false};
    }

    @Before
    public void setupMocks() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://0.0.0.0"));
        when(requestCtx.getUriInfo()).thenReturn(uriInfo);
    }

    @Test
    public void principalNoUserInfo() throws Exception {
        final ContainerRequestFilter filter = newFilterBuilder().build();

        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(ANONYMOUS_PRINCIPAL)));
    }

    @Test
    public void principalUserInfo() throws Exception {
        final Key<Principal> userInfoKey = newKey("basicPrincipal");
        final ContainerRequestFilter filter = newFilterBuilder(userInfoKey).build();

        filter.filter(requestCtx);
        verifyZeroInteractions(requestCtx);

        AsyncContext.put(userInfoKey, TEST_PRINCIPAL);
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(TEST_PRINCIPAL)));
    }

    @Test
    public void customPrincipalFunctionNoUserInfo() throws Exception {
        final ContainerRequestFilter filter = newFilterBuilder()
                .principalFunction(__ -> TEST_PRINCIPAL)
                .build();

        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(TEST_PRINCIPAL)));
    }

    @Test
    public void customPrincipalFunctionUserInfo() throws Exception {
        final Key<String> userInfoKey = newKey("basicPrincipal");
        final ContainerRequestFilter filter = newFilterBuilder(userInfoKey)
                .principalFunction((__, userInfo) -> TEST_USER_INFO.equals(userInfo) ? TEST_PRINCIPAL : null)
                .build();

        filter.filter(requestCtx);
        verifyZeroInteractions(requestCtx);

        AsyncContext.put(userInfoKey, TEST_USER_INFO);
        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue().getUserPrincipal(), is(sameInstance(TEST_PRINCIPAL)));
    }

    @Test
    public void customSecurityContextFunctionNoUserInfo() throws Exception {
        final SecurityContext securityContext = mock(SecurityContext.class);

        final ContainerRequestFilter filter = newFilterBuilder()
                .securityContextFunction(__ -> securityContext)
                .build();

        final ArgumentCaptor<SecurityContext> securityCtxCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        filter.filter(requestCtx);

        verify(requestCtx).setSecurityContext(securityCtxCaptor.capture());
        assertThat(securityCtxCaptor.getValue(), is(sameInstance(securityContext)));
    }

    @Test
    public void customSecurityContextFunctionUserInfo() throws Exception {
        final SecurityContext securityContext = mock(SecurityContext.class);

        final Key<String> userInfoKey = newKey("basicPrincipal");
        final ContainerRequestFilter filter = newFilterBuilder(userInfoKey)
                .securityContextFunction((__, userInfo) -> TEST_USER_INFO.equals(userInfo) ? securityContext : null)
                .build();

        filter.filter(requestCtx);
        verifyZeroInteractions(requestCtx);

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

    private <UserInfo> UserInfoBuilder<UserInfo> newFilterBuilder(final Key<UserInfo> userInfoKey) {
        return globalFilter ? BasicAuthSecurityContextFilters.forGlobalBinding(userInfoKey) :
                BasicAuthSecurityContextFilters.forNameBinding(userInfoKey);
    }
}
