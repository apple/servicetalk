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
package io.servicetalk.apple.http.basic.auth.jersey;

import io.servicetalk.apple.http.basic.auth.jersey.resources.AnnotatedResource;
import io.servicetalk.apple.http.basic.auth.jersey.resources.NotAnnotatedResource;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.jersey.HttpJerseyRouterBuilder;
import io.servicetalk.http.utils.auth.AuthenticationException;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.Builder;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.CredentialsVerifier;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Base64.getEncoder;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.SecurityContext.BASIC_AUTH;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public abstract class AbstractBasicAuthSecurityContextFilterTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

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

    public static final class BasicUserInfo {
        private final String userId;

        BasicUserInfo(final String userId) {
            this.userId = requireNonNull(userId);
        }

        public String userId() {
            return userId;
        }
    }

    protected static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    AnnotatedResource.class,
                    NotAnnotatedResource.class
            ));
        }
    }

    private static final Key<BasicUserInfo> TEST_USER_INFO_KEY = newKey("basicUserInfo");

    private final boolean withUserInfo;

    private ServerContext serverContext;
    private BlockingHttpClient httpClient;

    protected AbstractBasicAuthSecurityContextFilterTest(final boolean withUserInfo) {
        this.withUserInfo = withUserInfo;
    }

    @Parameters(name = " {index} with user info? {0}")
    public static Object[] params() {
        return new Object[]{true, false};
    }

    @Before
    public void startServer() throws Exception {
        final Builder<BasicUserInfo> builder = new Builder<>(CREDENTIALS_VERIFIER, "test-realm");
        if (withUserInfo) {
            builder.userInfoKey(TEST_USER_INFO_KEY);
        }
        serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(builder.buildServer())
                .listenStreamingAndAwait(new HttpJerseyRouterBuilder()
                        .build(application(withUserInfo ? TEST_USER_INFO_KEY : null)));

        httpClient = HttpClients.forSingleAddress(serverHostAndPort(serverContext)).buildBlocking();
    }

    protected abstract Application application(@Nullable Key<BasicUserInfo> userInfoKey);

    @After
    public void stopClient() throws Exception {
        httpClient.close();
    }

    @After
    public void stopServer() {
        serverContext.close();
    }

    protected void assertBasicAuthSecurityContextPresent(final String path) throws Exception {
        final String json = getSecurityContextJson(path, true);
        System.err.println(">>>> " + json);
        assertThatJson(json)
                .node("authenticationScheme").isStringEqualTo(BASIC_AUTH)
                .node("userPrincipal.name").isPresent()
                .node("secure").isEqualTo(false);
    }

    protected void assertBasicAuthSecurityContextAbsent(final String path,
                                                        final boolean authenticated) throws Exception {
        final String json = getSecurityContextJson(path, authenticated);
        if (!authenticated) {
            assertThat(json, is(nullValue()));
            return;
        }
        assertThatJson(json)
                .node("authenticationScheme").isEqualTo(null)
                .node("userPrincipal").isEqualTo(null)
                .node("secure").isEqualTo(false);
    }

    @Nullable
    private String getSecurityContextJson(final String path, final boolean authenticated) throws Exception {
        final HttpRequest req = httpClient.get(path).appendPathSegments("security-context");
        if (authenticated) {
            req.setHeader(AUTHORIZATION, TEST_AUTHORIZATION);
        }

        final HttpResponse res = httpClient.request(req);
        return res.status() == OK ? res.payloadBody().toString(UTF_8) : null;
    }
}
