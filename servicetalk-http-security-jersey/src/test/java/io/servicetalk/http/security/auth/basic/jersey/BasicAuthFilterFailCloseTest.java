/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder;
import io.servicetalk.http.security.auth.basic.jersey.resources.GlobalBindingResource;
import io.servicetalk.http.security.auth.basic.jersey.resources.NameBindingResource;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.CredentialsVerifier;

import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Arrays.asList;
import static java.util.Base64.getEncoder;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the fail-close pattern when either auth filter is missing or wrongly
 * configured:
 *
 * <ol>
 *   <li>{@link AbstractBasicAuthSecurityContextFilter#filter} calls
 *       {@code abortWith()}, so when {@code BasicAuthHttpServiceFilter} is missing
 *       upstream the request never proceeds with Jersey's default
 *       {@link SecurityContext}.</li>
 *   <li>The {@code NoUserInfoBuilder} default never installs a {@link SecurityContext}
 *       whose principal is a non-null anonymous principal, defeating any
 *       {@code getUserPrincipal() != null} guard.</li>
 * </ol>
 *
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasicAuthFilterFailCloseTest {

    @Mock
    private ContainerRequestContext requestCtx;

    @Mock
    private UriInfo uriInfo;

    private ServerContext serverContext;
    private BlockingHttpClient httpClient;

    @AfterEach
    void teardown() throws Exception {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } finally {
            if (serverContext != null) {
                serverContext.close();
            }
        }
    }

    /**
     * When the filter is configured with a {@code userInfoKey} but
     * {@code AsyncContext} contains no {@code UserInfo} (e.g. because
     * {@code BasicAuthHttpServiceFilter} is not installed upstream), the filter
     * must fail closed. The expected behavior is to call
     * {@link ContainerRequestContext#abortWith(Response)} with a {@code 401}.
     */
    @Test
    void nameBindingFilterAbortsWith401WhenUserInfoMissing() throws IOException {
        stubUriInfo();

        final ContextMap.Key<Principal> userInfoKey = newKey("userInfo", Principal.class);
        final ContainerRequestFilter filter = BasicAuthSecurityContextFilters.forNameBinding(userInfoKey).build();

        filter.filter(requestCtx);

        verify(requestCtx, never()).setSecurityContext(any(SecurityContext.class));

        final ArgumentCaptor<Response> respCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestCtx).abortWith(respCaptor.capture());
        assertThat(respCaptor.getValue().getStatus(),
                is(equalTo(Response.Status.UNAUTHORIZED.getStatusCode())));
    }

    /**
     * Same as above but for the global-binding variant — the fail-close path is
     * shared via {@link AbstractBasicAuthSecurityContextFilter}.
     */
    @Test
    void globalBindingFilterAbortsWith401WhenUserInfoMissing() throws IOException {
        stubUriInfo();

        final ContextMap.Key<Principal> userInfoKey = newKey("userInfo", Principal.class);
        final ContainerRequestFilter filter = BasicAuthSecurityContextFilters.forGlobalBinding(userInfoKey).build();

        filter.filter(requestCtx);

        verify(requestCtx, never()).setSecurityContext(any(SecurityContext.class));
        final ArgumentCaptor<Response> respCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestCtx).abortWith(respCaptor.capture());
        assertThat(respCaptor.getValue().getStatus(),
                is(equalTo(Response.Status.UNAUTHORIZED.getStatusCode())));
    }

    /**
     * The {@code NoUserInfoBuilder} default never installs a {@link SecurityContext}
     * with a non-null e.g. {@code ANONYMOUS_PRINCIPAL}. Any caller gating on
     * {@code getUserPrincipal() != null} won't treat the request as authenticated.
     *
     * <p>No {@code SecurityContext} is installed (fail-closed
     * via {@code abortWith}) so that
     * {@code getUserPrincipal() != null} must NOT pass.
     */
    @Test
    void noUserInfoDefaultDoesNotInstallNonNullPrincipal() throws IOException {
        stubUriInfo();

        final ContainerRequestFilter filter = BasicAuthSecurityContextFilters.forNameBinding().build();
        filter.filter(requestCtx);

        final ArgumentCaptor<SecurityContext> scCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(requestCtx, atMost(1)).setSecurityContext(scCaptor.capture());

        final List<SecurityContext> installed = scCaptor.getAllValues();
        for (final SecurityContext sc : installed) {
            assertThat("default no-userInfo path must not install a non-null principal",
                    sc.getUserPrincipal(), is(nullValue()));
        }
    }

    /**
     * Reproduces the deployment misconfiguration: a {@code @BasicAuthenticated}
     * resource is registered together with the Jersey filter, but the upstream
     * {@code BasicAuthHttpServiceFilter} is missing. The desired post-fix
     * behavior is {@code 401 Unauthorized}. The request must not reach the
     * resource by terminating early with abort & UNAUTHORIZED status code.
     */
    @Test
    void nameBoundResourceReturns401WhenUpstreamAuthFilterMissing() throws Exception {
        final ContextMap.Key<Principal> userInfoKey = newKey("userInfo", Principal.class);
        final Application app = new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return new HashSet<>(asList(GlobalBindingResource.class, NameBindingResource.class));
            }

            @Override
            public Set<Object> getSingletons() {
                final Set<Object> singletons = new HashSet<>();
                // Jersey filter is registered, but the upstream
                // BasicAuthHttpServiceFilter is NOT installed on the server.
                singletons.add(BasicAuthSecurityContextFilters.forNameBinding(userInfoKey).build());
                return singletons;
            }
        };

        serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait(new HttpJerseyRouterBuilder().buildStreaming(app));
        httpClient = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlocking();

        // Even with a syntactically valid header, the upstream filter that
        // would validate it is not installed, so AsyncContext stays empty.
        final String authHeader = "Basic " +
                getEncoder().encodeToString("any:any".getBytes(ISO_8859_1));
        final HttpResponse res = httpClient.request(httpClient.get(NameBindingResource.PATH)
                .appendPathSegments("security-context")
                .setHeader(AUTHORIZATION, authHeader));

        assertThat("request to a @BasicAuthenticated resource must fail closed when " +
                "BasicAuthHttpServiceFilter is missing upstream",
                res.status(), is(UNAUTHORIZED));
    }

    @Test
    void jerseyFilterFailsClosedWhenUpstreamPublishesUnderDifferentKey() throws Exception {
        // Upstream filter publishes here.
        final ContextMap.Key<Principal> upstreamKey = newKey("upstream", Principal.class);
        // Jersey filter looks up here. Intentional mismatch — the misconfiguration.
        final ContextMap.Key<Principal> jerseyKey = newKey("jersey", Principal.class);

        final CredentialsVerifier<Principal> verifier = new CredentialsVerifier<Principal>() {
            @Override
            public Single<Principal> apply(final String id, final String pwd) {
                return Single.succeeded(() -> id);
            }
            @Override
            public Completable closeAsync() {
                return Completable.completed();
            }
        };

        final StreamingHttpServiceFilterFactory upstream =
                new BasicAuthHttpServiceFilter.Builder<>(verifier, "test-realm")
                        .userInfoAsyncContextKey(upstreamKey)
                        .buildServer();

        final Application app = new Application() {
            @Override public Set<Class<?>> getClasses() {
                return new HashSet<>(asList(NameBindingResource.class));
            }
            @Override public Set<Object> getSingletons() {
                return singleton(BasicAuthSecurityContextFilters.forNameBinding(jerseyKey).build());
            }
        };

        serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(upstream)
                .listenStreamingAndAwait(new HttpJerseyRouterBuilder().buildStreaming(app));
        httpClient = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlocking();

        final String authHeader = "Basic " + getEncoder().encodeToString("any:any".getBytes(ISO_8859_1));
        final HttpResponse res = httpClient.request(httpClient.get(NameBindingResource.PATH)
                .appendPathSegments("security-context")
                .setHeader(AUTHORIZATION, authHeader));

        // Pre-fix: 200 (anonymous principal injected). Post-fix: 401.
        assertThat(res.status(), is(UNAUTHORIZED));
    }

    private void stubUriInfo() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://0.0.0.0"));
        when(requestCtx.getUriInfo()).thenReturn(uriInfo);
    }
}
