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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.context.AsyncContext;
import io.servicetalk.concurrent.context.AsyncContextMap;
import io.servicetalk.concurrent.context.AsyncContextMap.Key;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.WWW_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatuses.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.UNAUTHORIZED;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * A builder for an {@link HttpService}, which filters HTTP requests using
 * <a href="https://tools.ietf.org/html/rfc7617">RFC7617</a>: The 'Basic' HTTP Authentication Scheme.
 * <p>
 * It accepts credentials as {@code user-id:password} pairs, encoded using {@link Base64} for
 * {@link HttpHeaderNames#AUTHORIZATION Authorization} or
 * {@link HttpHeaderNames#PROXY_AUTHORIZATION Proxy-Authorization} header values. Use of the format
 * {@code user:password} in the {@link HttpRequestMetaData#getUserInfo() userinfo} field is deprecated by
 * <a href="https://tools.ietf.org/html/rfc3986#section-3.2.1">RFC3986</a>.
 * <p>
 * User info object of authenticated user could be stored in {@link AsyncContextMap}, if {@link Key} was configured via
 * {@link #setUserInfoKey(AsyncContextMap.Key)}.
 * <p>
 * <b>Note:</b> This scheme is not considered to be a secure method of user authentication unless used in conjunction
 * with some external secure system such as TLS (Transport Layer Security,
 * [<a href="https://tools.ietf.org/html/rfc5246">RFC5246</a>]), as the {@code user-id} and {@code password} are passed
 * over the network as cleartext.
 *
 * @param <UserInfo> a type for authenticated user info object
 */
public final class BasicAuthHttpServiceBuilder<UserInfo> {

    /**
     * Verifies {@code user-id} and {@code password}, parsed from the 'Basic' HTTP Authentication Scheme credentials.
     * <p>
     * This is an {@link AutoCloseable} {@link BiFunction}, which accepts {@code user-id} and {@code password} pair and
     * returns {@link Single}&lt;{@link UserInfo}&gt; with a user info object of authenticated user. In case of denied
     * access {@link Single} must fail with {@link AuthenticationException}.
     *
     * @param <UserInfo> a type for authenticated user info object
     */
    public interface CredentialsVerifier<UserInfo> extends BiFunction<String, String, Single<UserInfo>>,
                                                           AsyncCloseable {
        /**
         * Verifies {@code user-id} and {@code password}, parsed from the 'Basic' HTTP Authentication Scheme
         * credentials.
         *
         * @param userId a {@code user-id} parsed from the authentication token
         * @param password a {@code password} parsed from the authentication token
         * @return {@link Single}&lt;{@link UserInfo}&gt; with a user info object of authenticated user or
         * {@link Single} failed with an {@link AuthenticationException} if access was denied
         */
        @Override
        Single<UserInfo> apply(String userId, String password);
    }

    private final CredentialsVerifier<UserInfo> credentialsVerifier;
    private final String realm;
    private final boolean proxy;
    @Nullable
    private Key<UserInfo> userInfoKey;
    private boolean utf8;

    private BasicAuthHttpServiceBuilder(final CredentialsVerifier<UserInfo> credentialsVerifier,
                                        final String realm, final boolean proxy) {
        this.credentialsVerifier = requireNonNull(credentialsVerifier);
        this.realm = requireNonNull(realm);
        this.proxy = proxy;
    }

    /**
     * Creates a new instance for non-proxy service.
     * <p>
     * It will use the next constants to handle authentication:
     *
     * <blockquote><table cellpadding=1 cellspacing=0
     * summary="Response status code, authenticate and authorization headers for non-proxy Basic auth">
     * <tr>
     *     <td>Response status code</td>
     *     <td><a href="https://tools.ietf.org/html/rfc7235#section-3.1">401 (Unauthorized)</a></td>
     * </tr>
     * <tr>
     *     <td>Authenticate header</td>
     *     <td><a href="https://tools.ietf.org/html/rfc7235#section-4.1">WWW-Authenticate</a></td>
     * </tr>
     * <tr>
     *     <td>Authorization header</td>
     *     <td><a href="https://tools.ietf.org/html/rfc7235#section-4.2">Authorization</a></td>
     * </tr>
     * </table></blockquote>
     *
     * @param credentialsVerifier a {@link CredentialsVerifier} for {@code user-id} and {@code passwords} pair. The
     * future built {@link HttpService} will manage the lifecycle of the passed one, ensuring it is closed when the
     * {@link HttpService} is closed
     * @param realm a <a href="https://tools.ietf.org/html/rfc7235#section-2.2">protection space (realm)</a>
     * @param <UserInfo> a type for authenticated user info object
     * @return a new {@link BasicAuthHttpServiceBuilder}
     */
    public static <UserInfo> BasicAuthHttpServiceBuilder<UserInfo> newBasicAuthBuilder(
            final CredentialsVerifier<UserInfo> credentialsVerifier, final String realm) {
        return new BasicAuthHttpServiceBuilder<>(credentialsVerifier, realm, false);
    }

    /**
     * Creates a new instance for proxy service.
     * <p>
     * It will use the next constants to handle authentication:
     *
     * <blockquote><table cellpadding=1 cellspacing=0
     * summary="Response status code, authenticate and authorization headers for proxy Basic auth">
     * <tr>
     *     <td>Response status code</td>
     *     <td><a href="https://tools.ietf.org/html/rfc7235#section-3.2">407 (Proxy Authentication Required)</a></td>
     * </tr>
     * <tr>
     *     <td>Authenticate header</td>
     *     <td><a href="https://tools.ietf.org/html/rfc7235#section-4.3">Proxy-Authenticate</a></td>
     * </tr>
     * <tr>
     *     <td>Authorization header</td>
     *     <td><a href="https://tools.ietf.org/html/rfc7235#section-4.5">Proxy-Authorization</a></td>
     * </tr>
     * </table></blockquote>
     *
     * @param credentialsVerifier a {@link CredentialsVerifier} for {@code user-id} and {@code passwords} pair. The
     * future built {@link HttpService} will manage the lifecycle of the passed one, ensuring it is closed when the
     * {@link HttpService} is closed
     * @param realm a <a href="https://tools.ietf.org/html/rfc7235#section-2.2">protection space (realm)</a>
     * @param <UserInfo> a type for authenticated user info object
     * @return a new {@link BasicAuthHttpServiceBuilder}
     */
    public static <UserInfo> BasicAuthHttpServiceBuilder<UserInfo> newBasicAuthBuilderForProxy(
            final CredentialsVerifier<UserInfo> credentialsVerifier, final String realm) {
        return new BasicAuthHttpServiceBuilder<>(credentialsVerifier, realm, true);
    }

    /**
     * Sets a {@link Key key} to store a user info object of authenticated user in {@link AsyncContextMap}.
     *
     * @param userInfoKey a key to store a user info object in {@link AsyncContextMap}
     * @return {@code this}
     */
    public BasicAuthHttpServiceBuilder<UserInfo> setUserInfoKey(final Key<UserInfo> userInfoKey) {
        this.userInfoKey = userInfoKey;
        return this;
    }

    /**
     * Sets an advice for a user agent to use {@link StandardCharsets#UTF_8 UTF-8} charset when it generates
     * {@code user-id:password} pair.
     * <p>
     * It will result in adding an optional <a href="https://tools.ietf.org/html/rfc7617#section-2.1">
     * charset="UTF-8"</a> parameter for an authenticate header.
     *
     * @param utf8 if {@code true}, an optional {@code charset="UTF-8"} parameter will be added for an authenticate
     * header
     * @return {@code this}
     */
    public BasicAuthHttpServiceBuilder<UserInfo> setCharsetUtf8(final boolean utf8) {
        this.utf8 = utf8;
        return this;
    }

    /**
     * Builds a new Basic HTTP Auth filtering {@link HttpService}.
     *
     * @param next an {@link HttpService} to protect against unauthorized access. The returned {@link HttpService}
     * manages the lifecycle of the passed one, ensuring it is closed when the {@link HttpService} is closed
     * @return a new {@link HttpService}
     */
    public HttpService build(final HttpService next) {
        return new BasicAuthHttpService<>(credentialsVerifier, realm, proxy, userInfoKey, utf8, requireNonNull(next));
    }

    private static final class BasicAuthHttpService<UserInfo> extends HttpService {

        private static final Logger LOGGER = LoggerFactory.getLogger(BasicAuthHttpService.class);

        private static final String AUTH_SCHEME = "basic ";
        private static final int AUTH_SCHEME_LENGTH = AUTH_SCHEME.length();

        private final CredentialsVerifier<UserInfo> credentialsVerifier;
        private final String authenticateHeader;
        private final boolean proxy;
        @Nullable
        private final Key<UserInfo> userInfoKey;
        private final HttpService next;
        private final AsyncCloseable closeable;

        BasicAuthHttpService(final CredentialsVerifier<UserInfo> credentialsVerifier,
                             final String realm,
                             final boolean proxy,
                             @Nullable final Key<UserInfo> userInfoKey,
                             final boolean utf8,
                             final HttpService next) {
            this.credentialsVerifier = credentialsVerifier;
            this.authenticateHeader = "Basic realm=\"" + realm + (utf8 ? "\", charset=\"UTF-8\"" : '"');
            this.proxy = proxy;
            this.userInfoKey = userInfoKey;
            this.next = next;
            closeable = newCompositeCloseable().appendAll(next, credentialsVerifier);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                             final HttpRequest<HttpPayloadChunk> request) {
            // Use of the format "user:password" in the userinfo field is deprecated:
            //  - https://tools.ietf.org/html/rfc3986#section-3.2.1
            //  - https://tools.ietf.org/html/rfc3986#section-7.5
            // Check only "Authorization/Proxy-Authorization" headers, according to the format described in:
            //  - https://tools.ietf.org/html/rfc7617#section-2
            //  - https://tools.ietf.org/html/rfc2617#section-2
            final Iterator<? extends CharSequence> authorizations = request.getHeaders()
                    .getAll(proxy ? PROXY_AUTHORIZATION : AUTHORIZATION);
            String token = "";
            while (authorizations.hasNext()) {
                final CharSequence authorization = authorizations.next();
                if (authorization.length() <= AUTH_SCHEME_LENGTH) {
                    continue;
                }

                final String strAuth = authorization.toString();
                final int schemeIdx = strAuth.toLowerCase().indexOf(AUTH_SCHEME);
                if (schemeIdx < 0) {
                    continue;
                }

                final int beginIdx = schemeIdx + AUTH_SCHEME_LENGTH;
                final int commaIdx = strAuth.indexOf(',', beginIdx);
                final int endIdx = commaIdx < 0 ? strAuth.length() : commaIdx;
                if (endIdx > beginIdx) {
                    token = strAuth.substring(beginIdx, endIdx);
                    break;
                }
            }
            if (token.isEmpty()) {
                return onAccessDenied(request);
            }

            // Because most browsers use UTF-8 encoding for usernames and passwords (see:
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#Character_encoding_of_HTTP_authentication)
            // and RFC7617 says: the default encoding is undefined (most implementations chose UTF-8), as long as it is
            // compatible with US-ASCII, we can use UTF-8 to decode a "user-id:password" pair here:
            final String userIdAndPassword = new String(Base64.getDecoder().decode(token), UTF_8);
            final int colonIdx = userIdAndPassword.indexOf(':');
            if (colonIdx < 1) {
                return onAccessDenied(request);
            }

            final String userId = userIdAndPassword.substring(0, colonIdx);
            final String password = userIdAndPassword.length() - 1 == colonIdx ? "" :
                    userIdAndPassword.substring(colonIdx + 1);

            return credentialsVerifier.apply(userId, password)
                    .flatMap(userInfo -> onAuthenticated(ctx, request, userInfo))
                    .onErrorResume(t -> {
                        if (t instanceof AuthenticationException) {
                            return onAccessDenied(request);
                        }

                        LOGGER.debug("Unexpected exception during authentication", t);
                        return error(t);
                    });
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }

        private Single<HttpResponse<HttpPayloadChunk>> onAccessDenied(final HttpMetaData requestMetaData) {
            final HttpResponse<HttpPayloadChunk> response = newResponse(requestMetaData.getVersion(),
                    proxy ? PROXY_AUTHENTICATION_REQUIRED : UNAUTHORIZED);
            HttpHeaders headers = response.getHeaders();
            headers.set(proxy ? PROXY_AUTHENTICATE : WWW_AUTHENTICATE, authenticateHeader);
            headers.set(CONTENT_LENGTH, ZERO);
            return success(response);
        }

        private Single<HttpResponse<HttpPayloadChunk>> onAuthenticated(final ConnectionContext ctx,
                                                                       final HttpRequest<HttpPayloadChunk> request,
                                                                       final UserInfo userInfo) {
            if (userInfoKey != null) {
                AsyncContext.put(userInfoKey, userInfo);
            }
            return next.handle(ctx, request);
        }
    }
}
