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

import io.servicetalk.concurrent.api.AsyncContext;
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

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Arrays.asList;
import static java.util.Base64.getEncoder;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end tests for deployment misconfigurations of the fail-close pattern that relies on the
 * {@link BasicAuthHttpServiceFilter#AUTHENTICATED} marker:
 *
 * <ol>
 *   <li>Missing upstream {@code BasicAuthHttpServiceFilter} (marker absent): the filter aborts with {@code 401}
 *       instead of letting the request reach a {@code @BasicAuthenticated} resource.</li>
 *   <li>Key mismatch between the upstream filter and {@code BasicAuthSecurityContextFilters}: the request is
 *       authenticated but carries no user info, so the filter proceeds with a {@code null} user principal.</li>
 * </ol>
 */
class BasicAuthFilterMisconfigurationTest {

    private ServerContext serverContext;
    private BlockingHttpClient httpClient;

    @AfterEach
    void teardown() throws Exception {
        AsyncContext.clear();
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
     * A {@code @BasicAuthenticated} resource and the Jersey filter are registered, but the upstream
     * {@code BasicAuthHttpServiceFilter} is missing: the request must fail closed with {@code 401}.
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
                // Jersey filter registered, but the upstream BasicAuthHttpServiceFilter is NOT installed.
                singletons.add(BasicAuthSecurityContextFilters.forNameBinding(userInfoKey).build());
                return singletons;
            }
        };

        serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait(new HttpJerseyRouterBuilder().buildStreaming(app));
        httpClient = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlocking();

        // Valid header, but nothing validates it upstream, so AsyncContext stays empty.
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
    void jerseyProceedsWithNullPrincipalWhenUpstreamPublishesUnderDifferentKey() throws Exception {
        final ContextMap.Key<Principal> upstreamKey = newKey("upstream", Principal.class);
        // Intentional mismatch: the Jersey filter reads a different key than the upstream filter publishes.
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
            @Override
            public Set<Class<?>> getClasses() {
                return new HashSet<>(asList(NameBindingResource.class));
            }

            @Override
            public Set<Object> getSingletons() {
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

        // Authenticated (marker set), but no identity under the key the Jersey filter reads: proceed with a null
        // principal rather than reject.
        assertThat(res.status(), is(OK));
    }
}
