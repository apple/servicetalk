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

import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder;
import io.servicetalk.http.security.auth.basic.jersey.resources.GlobalBindingResource;
import io.servicetalk.http.security.auth.basic.jersey.resources.NameBindingResource;
import io.servicetalk.http.utils.auth.AuthenticationException;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.Builder;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.CredentialsVerifier;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Base64.getEncoder;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.SecurityContext.BASIC_AUTH;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractBasicAuthSecurityContextFilterTest {

    private static final String TEST_USERID = "test-id";
    private static final String TEST_PASSWORD = "test-pwd";
    private static final String TEST_AUTHORIZATION =
            "Basic " + getEncoder().encodeToString((TEST_USERID + ':' + TEST_PASSWORD).getBytes(ISO_8859_1));

    private static final CredentialsVerifier<BasicUserInfo> CREDENTIALS_VERIFIER =
            new CredentialsVerifier<BasicUserInfo>() {
                @Override
                public Single<BasicUserInfo> apply(final String userId, final String password) {
                    if (TEST_USERID.equals(userId) && TEST_PASSWORD.equals(password)) {
                        return succeeded(new BasicUserInfo(userId));
                    }
                    return failed(new AuthenticationException("Invalid credentials"));
                }

                @Override
                public Completable closeAsync() {
                    return completed();
                }
            };

    static final class BasicUserInfo {
        private final String userId;

        BasicUserInfo(final String userId) {
            this.userId = requireNonNull(userId);
        }

        public String userId() {
            return userId;
        }

        Set<String> roles() {
            return singleton("USER");
        }
    }

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    GlobalBindingResource.class,
                    NameBindingResource.class
            ));
        }
    }

    private static final Key<BasicUserInfo> TEST_USER_INFO_KEY = newKey("basicUserInfo");

    private ServerContext serverContext;
    private BlockingHttpClient httpClient;

    void setUp(final boolean withUserInfo) throws Exception {
        final Builder<BasicUserInfo> builder = new Builder<>(CREDENTIALS_VERIFIER, "test-realm");
        if (withUserInfo) {
            builder.userInfoKey(TEST_USER_INFO_KEY);
        }
        serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(builder.buildServer())
                .listenStreamingAndAwait(new HttpJerseyRouterBuilder()
                        .buildStreaming(application(withUserInfo ? TEST_USER_INFO_KEY : null)));

        httpClient = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlocking();
    }

    protected abstract Application application(@Nullable Key<BasicUserInfo> userInfoKey);

    @AfterEach
    public void teardown() throws Exception {
        try {
            httpClient.close();
        } finally {
            serverContext.close();
        }
    }

    void assertBasicAuthSecurityContextPresent(final String path) throws Exception {
        final String json = getSecurityContextJson(path, true, OK);
        assertThatJson(json)
                .node("authenticationScheme").isStringEqualTo(BASIC_AUTH)
                .node("userPrincipal.name").isPresent()
                .node("secure").isEqualTo(false);
    }

    void assertBasicAuthSecurityContextAbsent(final String path,
                                              final boolean authenticated) throws Exception {
        assertBasicAuthSecurityContextAbsent(path, authenticated, authenticated ? OK : UNAUTHORIZED);
    }

    void assertBasicAuthSecurityContextAbsent(final String path,
                                              final boolean authenticated,
                                              final HttpResponseStatus expectedStatus) throws Exception {

        final String json = getSecurityContextJson(path, authenticated, expectedStatus);

        if (json != null) {
            assertThatJson(json)
                    .node("authenticationScheme").isEqualTo(null)
                    .node("userPrincipal").isEqualTo(null)
                    .node("secure").isEqualTo(false);
        }
    }

    @Nullable
    private String getSecurityContextJson(final String path,
                                          final boolean authenticated,
                                          final HttpResponseStatus expectedStatus) throws Exception {

        final HttpRequest req = httpClient.get(path).appendPathSegments("security-context");
        if (authenticated) {
            req.setHeader(AUTHORIZATION, TEST_AUTHORIZATION);
        }

        final HttpResponse res = httpClient.request(req);
        assertThat(res.status(), is(expectedStatus));

        return OK.equals(res.status()) ? res.payloadBody().toString(UTF_8) : null;
    }
}
