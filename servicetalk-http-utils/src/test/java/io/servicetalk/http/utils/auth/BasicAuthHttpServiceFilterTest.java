/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.CredentialsVerifier;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.WWW_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getEncoder;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BasicAuthHttpServiceFilterTest {

    private static final CharSequence USER_ID_HEADER_NAME = newAsciiString("test-userid");
    private static final Key<BasicUserInfo> USER_INFO_KEY = newKey("basicUserInfo");

    private static final class BasicUserInfo {

        private final String userId;

        BasicUserInfo(final String userId) {
            this.userId = requireNonNull(userId);
        }

        public String userId() {
            return userId;
        }
    }

    private static final StreamingHttpService HELLO_WORLD_SERVICE = new StreamingHttpService() {
        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {
            StreamingHttpResponse response = factory.ok().payloadBody(
                    from(ctx.executionContext().bufferAllocator().fromAscii("Hello World!")));
            BasicUserInfo userInfo = AsyncContext.get(USER_INFO_KEY);
            if (userInfo != null) {
                response.headers().set(USER_ID_HEADER_NAME, userInfo.userId());
            }
            return succeeded(response);
        }
    };

    private static final CredentialsVerifier<BasicUserInfo> CREDENTIALS_VERIFIER =
            new CredentialsVerifier<BasicUserInfo>() {
                @Override
                public Single<BasicUserInfo> apply(final String userId, final String password) {
                    if ("password".equals(password)) {
                        return succeeded(new BasicUserInfo(userId));
                    }
                    return failed(new AuthenticationException("Wrong password"));
                }

                @Override
                public Completable closeAsync() {
                    return completed();
                }
            };

    private static final String REALM_VALUE = "hw_realm";
    private static final HttpServiceContext CONN_CTX = mock(HttpServiceContext.class);
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @BeforeClass
    public static void beforeClass() {
        HttpExecutionContext ec = mock(HttpExecutionContext.class);
        when(ec.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(CONN_CTX.executionContext()).thenReturn(ec);
    }

    @After
    public void cleanUp() {
        AsyncContext.clear();
    }

    @Test
    public void noAuthorizationHeader() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        testUnauthorized(request);
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void tooShortAuthorizationHeader() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "short");
        testUnauthorized(request);
    }

    @Test
    public void tooShortAuthorizationHeaderForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "short");
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void noBasicSchemeInAuthorizationHeader() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "long-enough-but-no-scheme");
        testUnauthorized(request);
    }

    @Test
    public void noBasicSchemeInAuthorizationHeaderForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "long-enough-but-no-scheme");
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void emptyBasicTokenInAuthorizationHeader() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "OtherScheme qwe, Basic ");
        testUnauthorized(request);
    }

    @Test
    public void emptyBasicTokenInAuthorizationHeaderForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "OtherScheme qwe, Basic ");
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void noUserIdInToken() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Basic " + base64("no-colon"));
        testUnauthorized(request);
    }

    @Test
    public void noUserIdInTokenForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "Basic " + base64("no-colon"));
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void wrongPassword() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Basic " + base64("userId:wrong-password"));
        testUnauthorized(request);
    }

    @Test
    public void wrongPasswordForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "Basic " + base64("userId:wrong-password"));
        testProxyAuthenticationRequired(request);
    }

    @Test
    public void authenticatedWithoutUserInfo() throws Exception {
        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(CREDENTIALS_VERIFIER, REALM_VALUE)
                .buildServer()
                .create(HELLO_WORLD_SERVICE);

        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Basic " + base64("userId:password"));

        StreamingHttpResponse response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request, reqRespFactory));
        assertEquals(OK, response.status());

        assertFalse(response.headers().contains(USER_ID_HEADER_NAME));
    }

    @Test
    public void authenticated() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Basic " + base64("userId:password"));
        testAuthenticated(request);
    }

    @Test
    public void authenticatedForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "Basic " + base64("userId:password"));
        testAuthenticatedForProxy(request);
    }

    @Test
    public void authenticatedAndHasOtherScheme() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Other token, Basic " + base64("userId:password"));
        testAuthenticated(request);
    }

    @Test
    public void authenticatedAndHasOtherSchemeForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION, "Other token, Basic " + base64("userId:password"));
        testAuthenticatedForProxy(request);
    }

    @Test
    public void authenticatedBasicTokenInBetween() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Other token1, Basic " + base64("userId:password") + ", Some token2");
        testAuthenticated(request);
    }

    @Test
    public void authenticatedBasicTokenInBetweenForProxy() throws Exception {
        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(PROXY_AUTHORIZATION,
                "Other token1, Basic " + base64("userId:password") + ", Some token2");
        testAuthenticatedForProxy(request);
    }

    @Test
    public void authenticatedWithSecondHeader() throws Exception {
        testAuthenticated(reqRespFactory.get("/path")
                .addHeader(AUTHORIZATION, "Other token1")
                .addHeader(AUTHORIZATION, "Basic " + base64("userId:password"))
                .addHeader(AUTHORIZATION, "Some token2"));
    }

    @Test
    public void authenticatedWithSecondHeaderForProxy() throws Exception {
        testAuthenticatedForProxy(reqRespFactory.get("/path")
                .addHeader(PROXY_AUTHORIZATION, "Other token1")
                .addHeader(PROXY_AUTHORIZATION, "Basic " + base64("userId:password"))
                .addHeader(PROXY_AUTHORIZATION, "Some token2"));
    }

    @Test
    public void utf8() throws Exception {
        final CredentialsVerifier<BasicUserInfo> utf8CredentialsVerifier = new CredentialsVerifier<BasicUserInfo>() {
            @Override
            public Single<BasicUserInfo> apply(final String userId, final String password) {
                if ("пароль".equals(password)) {
                    return succeeded(new BasicUserInfo(userId));
                }
                return failed(new AuthenticationException("Wrong password"));
            }

            @Override
            public Completable closeAsync() {
                return completed();
            }
        };
        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(
                utf8CredentialsVerifier, REALM_VALUE)
                .userInfoKey(USER_INFO_KEY)
                .setCharsetUtf8(true)
                .buildServer()
                .create(HELLO_WORLD_SERVICE);

        StreamingHttpResponse response =
                awaitIndefinitelyNonNull(service.handle(CONN_CTX, reqRespFactory.get("/path"), reqRespFactory));
        assertEquals(UNAUTHORIZED, response.status());
        assertEquals("Basic realm=\"" + REALM_VALUE + "\", charset=\"UTF-8\"",
                response.headers().get(WWW_AUTHENTICATE));
        assertFalse(response.headers().contains(USER_ID_HEADER_NAME));

        StreamingHttpRequest request = reqRespFactory.get("/path");
        request.headers().set(AUTHORIZATION, "Basic " + base64("userId:пароль", UTF_8));
        testAuthenticated(request, service);
    }

    @Test
    public void closeAsync() throws Exception {
        AtomicBoolean credentialsVerifierClosed = new AtomicBoolean();
        AtomicBoolean nextServiceClosed = new AtomicBoolean();

        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(
                new CredentialsVerifier<BasicUserInfo>() {
                    @Override
                    public Single<BasicUserInfo> apply(final String userId, final String password) {
                        return never();
                    }

                    @Override
                    public Completable closeAsync() {
                        return completed().beforeOnComplete(() -> credentialsVerifierClosed.set(true));
                    }
                }, REALM_VALUE)
                .buildServer()
                .create(new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory factory) {
                return never();
            }

            @Override
            public Completable closeAsync() {
                return completed().beforeOnComplete(() -> nextServiceClosed.set(true));
            }
        });

        assertFalse(credentialsVerifierClosed.get());
        assertFalse(nextServiceClosed.get());
        service.closeAsync().toFuture().get();
        assertTrue(credentialsVerifierClosed.get());
        assertTrue(nextServiceClosed.get());
    }

    private static void testUnauthorized(StreamingHttpRequest request) throws Exception {
        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(CREDENTIALS_VERIFIER, REALM_VALUE)
                .buildServer()
                .create(HELLO_WORLD_SERVICE);

        StreamingHttpResponse response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request, reqRespFactory));
        assertEquals(UNAUTHORIZED, response.status());
        assertEquals("Basic realm=\"" + REALM_VALUE + '"', response.headers().get(WWW_AUTHENTICATE));
        assertEquals(ZERO, response.headers().get(CONTENT_LENGTH));
        assertFalse(response.headers().contains(USER_ID_HEADER_NAME));
    }

    private static void testProxyAuthenticationRequired(StreamingHttpRequest request) throws Exception {
        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(CREDENTIALS_VERIFIER, REALM_VALUE)
                .buildProxy()
                .create(HELLO_WORLD_SERVICE);

        StreamingHttpResponse response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request, reqRespFactory));
        assertEquals(PROXY_AUTHENTICATION_REQUIRED, response.status());
        assertEquals("Basic realm=\"" + REALM_VALUE + '"', response.headers().get(PROXY_AUTHENTICATE));
        assertEquals(ZERO, response.headers().get(CONTENT_LENGTH));
        assertFalse(response.headers().contains(USER_ID_HEADER_NAME));
    }

    private static void testAuthenticated(StreamingHttpRequest request) throws Exception {
        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(CREDENTIALS_VERIFIER, REALM_VALUE)
                .userInfoKey(USER_INFO_KEY)
                .buildServer()
                .create(HELLO_WORLD_SERVICE);
        testAuthenticated(request, service);
    }

    private static void testAuthenticated(StreamingHttpRequest request, StreamingHttpServiceFilter service)
            throws Exception {
        StreamingHttpResponse response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request, reqRespFactory));
        assertEquals(OK, response.status());

        assertTrue(response.headers().contains(USER_ID_HEADER_NAME, "userId"));
    }

    private static void testAuthenticatedForProxy(StreamingHttpRequest request) throws Exception {
        StreamingHttpServiceFilter service = new BasicAuthHttpServiceFilter.Builder<>(CREDENTIALS_VERIFIER, REALM_VALUE)
                .userInfoKey(USER_INFO_KEY)
                .buildProxy()
                .create(HELLO_WORLD_SERVICE);

        StreamingHttpResponse response = awaitIndefinitelyNonNull(service.handle(CONN_CTX, request, reqRespFactory));
        assertEquals(OK, response.status());

        assertTrue(response.headers().contains(USER_ID_HEADER_NAME, "userId"));
    }

    private static String base64(String str) {
        return base64(str, ISO_8859_1);
    }

    private static String base64(String str, Charset charset) {
        return getEncoder().encodeToString(str.getBytes(charset));
    }
}
