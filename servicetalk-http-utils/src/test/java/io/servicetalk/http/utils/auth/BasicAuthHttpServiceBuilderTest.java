/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils.auth;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.context.AsyncContext;
import io.servicetalk.concurrent.context.AsyncContextMap.Key;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceBuilder.CredentialsVerifier;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.context.AsyncContextMap.Key.newKeyWithDebugToString;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.WWW_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.UNAUTHORIZED;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.utils.auth.BasicAuthHttpServiceBuilder.newBasicAuthBuilder;
import static io.servicetalk.http.utils.auth.BasicAuthHttpServiceBuilder.newBasicAuthBuilderForProxy;
import static java.util.Base64.getEncoder;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BasicAuthHttpServiceBuilderTest {

    private static final class BasicUserInfo {

        private final String userId;

        BasicUserInfo(final String userId) {
            this.userId = requireNonNull(userId);
        }

        public String getUserId() {
            return userId;
        }
    }

    private static final HttpService HELLO_WORLD_SERVICE = HttpService.fromAsync((ctx, request) ->
            success(newResponse(OK, ctx.getExecutionContext().getBufferAllocator().fromAscii("Hello World!"))));
    private static final CredentialsVerifier<BasicUserInfo> CREDENTIALS_VERIFIER = new CredentialsVerifier<BasicUserInfo>() {
        @Override
        public Single<BasicUserInfo> apply(final String userId, final String password) {
            if ("password".equals(password)) {
                return success(new BasicUserInfo(userId));
            }
            return error(new AuthenticationException("Wrong password"));
        }

        @Override
        public Completable closeAsync() {
            return completed();
        }
    };

    private static final Key<BasicUserInfo> USER_INFO_KEY = newKeyWithDebugToString("basicUserInfo");
    private static final String REALM_VALUE = "hw_realm";
    private static final ConnectionContext CONN_CTX = mock(ConnectionContext.class);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @BeforeClass
    public static void beforeClass() {
        ExecutionContext ec = mock(ExecutionContext.class);
        when(ec.getBufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(CONN_CTX.getExecutionContext()).thenReturn(ec);
    }

    @After
    public void cleanUp() {
        AsyncContext.clear();
    }

    @Test
    public void noAuthorizationHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        testUnauthorized(request);
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void tooShortAuthorizationHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "short");
        testUnauthorized(request);
    }

    @Test
    public void tooShortAuthorizationHeaderForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "short");
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void noBasicSchemeInAuthorizationHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "long-enough-but-no-scheme");
        testUnauthorized(request);
    }

    @Test
    public void noBasicSchemeInAuthorizationHeaderForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "long-enough-but-no-scheme");
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void emptyBasicTokenInAuthorizationHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "OtherScheme qwe, Basic ");
        testUnauthorized(request);
    }

    @Test
    public void emptyBasicTokenInAuthorizationHeaderForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "OtherScheme qwe, Basic ");
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void noUserIdInToken() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Basic " + base64("no-colon"));
        testUnauthorized(request);
    }

    @Test
    public void noUserIdInTokenForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "Basic " + base64("no-colon"));
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void wrongPassword() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Basic " + base64("userId:wrong-password"));
        testUnauthorized(request);
    }

    @Test
    public void wrongPasswordForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "Basic " + base64("userId:wrong-password"));
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void authenticatedWithoutUserInfo() throws Exception {
        HttpService service = newBasicAuthBuilder(CREDENTIALS_VERIFIER, REALM_VALUE)
                .build(HELLO_WORLD_SERVICE);

        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Basic " + base64("userId:password"));

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request));
        assertEquals(OK, response.getStatus());

        BasicUserInfo userInfo = AsyncContext.get(USER_INFO_KEY);
        assertNull(userInfo);
    }

    @Test
    public void authenticated() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Basic " + base64("userId:password"));
        testAuthenticated(request);
    }

    @Test
    public void authenticatedForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "Basic " + base64("userId:password"));
        testAuthenticatedForProxy(request);
    }

    @Test
    public void authenticatedAndHasOtherScheme() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Other token, Basic " + base64("userId:password"));
        testAuthenticated(request);
    }

    @Test
    public void authenticatedAndHasOtherSchemeForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION, "Other token, Basic " + base64("userId:password"));
        testAuthenticatedForProxy(request);
    }

    @Test
    public void authenticatedBasicTokenInBetween() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Other token1, Basic " + base64("userId:password") + ", Some token2");
        testAuthenticated(request);
    }

    @Test
    public void authenticatedBasicTokenInBetweenForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(PROXY_AUTHORIZATION,
                "Other token1, Basic " + base64("userId:password") + ", Some token2");
        testAuthenticatedForProxy(request);
    }

    @Test
    public void authenticatedWithSecondHeader() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().add(AUTHORIZATION, "Other token1");
        request.getHeaders().add(AUTHORIZATION, "Basic " + base64("userId:password"));
        request.getHeaders().add(AUTHORIZATION, "Some token2");
        testAuthenticated(request);
    }

    @Test
    public void authenticatedWithSecondHeaderForProxy() throws Exception {
        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().add(PROXY_AUTHORIZATION, "Other token1");
        request.getHeaders().add(PROXY_AUTHORIZATION, "Basic " + base64("userId:password"));
        request.getHeaders().add(PROXY_AUTHORIZATION, "Some token2");
        testAuthenticatedForProxy(request);
    }

    @Test
    public void utf8() throws Exception {
        final CredentialsVerifier<BasicUserInfo> utf8CredentialsVerifier = new CredentialsVerifier<BasicUserInfo>() {
            @Override
            public Single<BasicUserInfo> apply(final String userId, final String password) {
                if ("пароль".equals(password)) {
                    return success(new BasicUserInfo(userId));
                }
                return error(new AuthenticationException("Wrong password"));
            }

            @Override
            public Completable closeAsync() {
                return completed();
            }
        };
        HttpService service = newBasicAuthBuilder(utf8CredentialsVerifier, REALM_VALUE)
                .setUserInfoKey(USER_INFO_KEY)
                .setCharsetUtf8(true)
                .build(HELLO_WORLD_SERVICE);

        HttpResponse<HttpPayloadChunk> response =
                awaitIndefinitelyNonNull(service.handle(CONN_CTX, newRequest(GET, "/path")));
        assertEquals(UNAUTHORIZED, response.getStatus());
        assertEquals("Basic realm=\"" + REALM_VALUE + "\", charset=\"UTF-8\"",
                response.getHeaders().get(WWW_AUTHENTICATE));
        assertNull(AsyncContext.get(USER_INFO_KEY));

        HttpRequest<HttpPayloadChunk> request = newRequest(GET, "/path");
        request.getHeaders().set(AUTHORIZATION, "Basic " + base64("userId:пароль"));
        testAuthenticated(request, service);
    }

    @Test
    public void closeAsync() throws Exception {
        AtomicBoolean credentialsVerifierClosed = new AtomicBoolean();
        AtomicBoolean nextServiceClosed = new AtomicBoolean();

        HttpService service = newBasicAuthBuilder(new CredentialsVerifier<BasicUserInfo>() {
            @Override
            public Single<BasicUserInfo> apply(final String userId, final String password) {
                return never();
            }

            @Override
            public Completable closeAsync() {
                return completed().doBeforeComplete(() -> credentialsVerifierClosed.set(true));
            }
        }, REALM_VALUE).build(new HttpService() {
            @Override
            public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                 final HttpRequest<HttpPayloadChunk> request) {
                return never();
            }

            @Override
            public Completable closeAsync() {
                return completed().doBeforeComplete(() -> nextServiceClosed.set(true));
            }
        });

        assertFalse(credentialsVerifierClosed.get());
        assertFalse(nextServiceClosed.get());
        awaitIndefinitely(service.closeAsync());
        assertTrue(credentialsVerifierClosed.get());
        assertTrue(nextServiceClosed.get());
    }

    private static void testUnauthorized(HttpRequest<HttpPayloadChunk> request) throws Exception {
        HttpService service = newBasicAuthBuilder(CREDENTIALS_VERIFIER, REALM_VALUE)
                .build(HELLO_WORLD_SERVICE);

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request));
        assertEquals(UNAUTHORIZED, response.getStatus());
        assertEquals("Basic realm=\"" + REALM_VALUE + '"', response.getHeaders().get(WWW_AUTHENTICATE));
        assertEquals(ZERO, response.getHeaders().get(CONTENT_LENGTH));
        assertNull(AsyncContext.get(USER_INFO_KEY));
    }

    private static void testProxyAuthenticationRequired(HttpRequest<HttpPayloadChunk> request) throws Exception {
        HttpService service = newBasicAuthBuilderForProxy(CREDENTIALS_VERIFIER, REALM_VALUE)
                .build(HELLO_WORLD_SERVICE);

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request));
        assertEquals(PROXY_AUTHENTICATION_REQUIRED, response.getStatus());
        assertEquals("Basic realm=\"" + REALM_VALUE + '"', response.getHeaders().get(PROXY_AUTHENTICATE));
        assertEquals(ZERO, response.getHeaders().get(CONTENT_LENGTH));
        assertNull(AsyncContext.get(USER_INFO_KEY));
    }

    private static void testAuthenticated(HttpRequest<HttpPayloadChunk> request) throws Exception {
        HttpService service = newBasicAuthBuilder(CREDENTIALS_VERIFIER, REALM_VALUE)
                .setUserInfoKey(USER_INFO_KEY)
                .build(HELLO_WORLD_SERVICE);
        testAuthenticated(request, service);
    }

    private static void testAuthenticated(HttpRequest<HttpPayloadChunk> request, HttpService service) throws Exception {
        HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request));
        assertEquals(OK, response.getStatus());

        BasicUserInfo userInfo = AsyncContext.get(USER_INFO_KEY);
        assertNotNull(userInfo);
        assertEquals("userId", userInfo.getUserId());
    }

    private static void testAuthenticatedForProxy(HttpRequest<HttpPayloadChunk> request) throws Exception {
        HttpService service = newBasicAuthBuilderForProxy(CREDENTIALS_VERIFIER, REALM_VALUE)
                .setUserInfoKey(USER_INFO_KEY)
                .build(HELLO_WORLD_SERVICE);

        HttpResponse<HttpPayloadChunk> response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request));
        assertEquals(OK, response.getStatus());

        BasicUserInfo userInfo = AsyncContext.get(USER_INFO_KEY);
        assertNotNull(userInfo);
        assertEquals("userId", userInfo.getUserId());
    }

    private static String base64(String str) {
        return getEncoder().encodeToString(str.getBytes());
    }
}
