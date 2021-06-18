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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderNames.WWW_AUTHENTICATE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpServiceFilter}, which filters HTTP requests using
 * <a href="https://tools.ietf.org/html/rfc7617">RFC7617</a>: The 'Basic' HTTP Authentication Scheme.
 *
 * @param <UserInfo> a type for authenticated user info object
 * @see Builder
 */
public final class BasicAuthHttpServiceFilter<UserInfo>
        implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

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

    /**
     * A builder for an {@link StreamingHttpServiceFilter}, which filters HTTP requests using <a
     * href="https://tools.ietf.org/html/rfc7617">RFC7617</a>: The 'Basic' HTTP Authentication Scheme.
     *
     * @param <UserInfo> a type for authenticated user info object
     */
    public static final class Builder<UserInfo> {

        private final CredentialsVerifier<UserInfo> credentialsVerifier;
        private final String realm;
        @Nullable
        private Key<UserInfo> userInfoKey;
        private boolean utf8;

        /**
         * Creates a new builder for an {@link StreamingHttpServiceFilter}, which filters HTTP requests using <a
         * href="https://tools.ietf.org/html/rfc7617">RFC7617</a>: The 'Basic' HTTP Authentication Scheme.
         * <p>
         * It accepts credentials as {@code user-id:password} pairs, encoded using {@link Base64} for {@link
         * HttpHeaderNames#AUTHORIZATION Authorization} or {@link HttpHeaderNames#PROXY_AUTHORIZATION
         * Proxy-Authorization} header values. Use of the format {@code user:password} in the {@link
         * HttpRequestMetaData#userInfo() userinfo} field is deprecated by <a
         * href="https://tools.ietf.org/html/rfc3986#section-3.2.1">RFC3986</a>.
         * <p>
         * User info object of authenticated user could be stored in {@link AsyncContextMap}, if {@link Key} was
         * configured via {@link Builder#userInfoKey(AsyncContextMap.Key)}.
         * <p>
         * <b>Note:</b> This scheme is not considered to be a secure method of user authentication unless used in
         * conjunction with some external secure system such as TLS (Transport Layer Security, [<a
         * href="https://tools.ietf.org/html/rfc5246">RFC5246</a>]), as the {@code user-id} and {@code password} are
         * passed over the network as cleartext.
         *
         * @param credentialsVerifier a {@link CredentialsVerifier} for {@code user-id} and {@code passwords} pair
         * @param realm a <a href="https://tools.ietf.org/html/rfc7235#section-2.2">protection space (realm)</a>
         */
        public Builder(final CredentialsVerifier<UserInfo> credentialsVerifier,
                       final String realm) {
            this.credentialsVerifier = requireNonNull(credentialsVerifier);
            this.realm = requireNonNull(realm);
        }

        /**
         * Sets a {@link Key key} to store a user info object of authenticated user in {@link AsyncContextMap}.
         *
         * @param userInfoKey a key to store a user info object in {@link AsyncContextMap}
         * @return {@code this}
         */
        public Builder<UserInfo> userInfoKey(final Key<UserInfo> userInfoKey) {
            this.userInfoKey = requireNonNull(userInfoKey);
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
        public Builder<UserInfo> setCharsetUtf8(final boolean utf8) {
            this.utf8 = utf8;
            return this;
        }

        /**
         * Creates a new instance for non-proxy service.
         * <p>
         * It will use the following constants to handle authentication:
         *
         * <blockquote><table>
         * <caption>Response status code, authenticate and authorization headers for non-proxy Basic auth</caption>
         * <tr>
         * <td>Response status code</td>
         * <td><a href="https://tools.ietf.org/html/rfc7235#section-3.1">401 (Unauthorized)</a></td>
         * </tr>
         * <tr>
         * <td>Authenticate header</td>
         * <td><a href="https://tools.ietf.org/html/rfc7235#section-4.1">WWW-Authenticate</a></td>
         * </tr>
         * <tr>
         * <td>Authorization header</td>
         * <td><a href="https://tools.ietf.org/html/rfc7235#section-4.2">Authorization</a></td>
         * </tr>
         * </table></blockquote>
         *
         * @return a new {@link Builder}
         */
        public StreamingHttpServiceFilterFactory buildServer() {
            return new BasicAuthHttpServiceFilter<>(credentialsVerifier, realm, false, userInfoKey, utf8);
        }

        /**
         * Creates a new instance for proxy service.
         * <p>
         * It will use the following constants to handle authentication:
         *
         * <blockquote><table>
         * <caption>Response status code, authenticate and authorization headers for proxy Basic auth</caption>
         * <tr>
         * <td>Response status code</td>
         * <td><a href="https://tools.ietf.org/html/rfc7235#section-3.2">407 (Proxy Authentication Required)</a></td>
         * </tr>
         * <tr>
         * <td>Authenticate header</td>
         * <td><a href="https://tools.ietf.org/html/rfc7235#section-4.3">Proxy-Authenticate</a></td>
         * </tr>
         * <tr>
         * <td>Authorization header</td>
         * <td><a href="https://tools.ietf.org/html/rfc7235#section-4.5">Proxy-Authorization</a></td>
         * </tr>
         * </table></blockquote>
         *
         * @return a new {@link StreamingHttpServiceFilterFactory}
         */
        public StreamingHttpServiceFilterFactory buildProxy() {
            return new BasicAuthHttpServiceFilter<>(credentialsVerifier, realm, true, userInfoKey, utf8);
        }
    }

    private final CredentialsVerifier<UserInfo> credentialsVerifier;
    private final HttpExecutionStrategyInfluencer influencer;
    private final String realm;
    private final boolean proxy;
    @Nullable
    private final Key<UserInfo> userInfoKey;
    private final boolean utf8;

    private BasicAuthHttpServiceFilter(final CredentialsVerifier<UserInfo> credentialsVerifier,
                                       final String realm,
                                       final boolean proxy,
                                       @Nullable final Key<UserInfo> userInfoKey,
                                       final boolean utf8) {
        this.credentialsVerifier = credentialsVerifier;
        this.realm = realm;
        this.proxy = proxy;
        this.userInfoKey = userInfoKey;
        this.utf8 = utf8;
        influencer = credentialsVerifier instanceof HttpExecutionStrategyInfluencer ?
                (HttpExecutionStrategyInfluencer) credentialsVerifier :
                defaultStreamingInfluencer();
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new BasicAuthStreamingHttpService<>(this, service);
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return influencer.influenceStrategy(strategy);
    }

    private static final class BasicAuthStreamingHttpService<UserInfo> extends StreamingHttpServiceFilter {

        private static final Logger LOGGER = LoggerFactory.getLogger(BasicAuthStreamingHttpService.class);

        private static final String AUTH_SCHEME = "basic ";
        private static final int AUTH_SCHEME_LENGTH = AUTH_SCHEME.length();

        private final BasicAuthHttpServiceFilter<UserInfo> config;

        private final String authenticateHeader;
        private final AsyncCloseable closeable;

        BasicAuthStreamingHttpService(final BasicAuthHttpServiceFilter<UserInfo> config,
                                      final StreamingHttpService next) {
            super(next);
            this.config = config;
            this.authenticateHeader = "Basic realm=\"" + config.realm + (config.utf8 ? "\", charset=\"UTF-8\"" : '"');
            closeable = newCompositeCloseable().appendAll(next, config.credentialsVerifier);
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory factory) {
            // Use of the format "user:password" in the userinfo field is deprecated:
            //  - https://tools.ietf.org/html/rfc3986#section-3.2.1
            //  - https://tools.ietf.org/html/rfc3986#section-7.5
            // Check only "Authorization/Proxy-Authorization" headers, according to the format described in:
            //  - https://tools.ietf.org/html/rfc7617#section-2
            //  - https://tools.ietf.org/html/rfc2617#section-2
            final Iterator<? extends CharSequence> authorizations = request.headers()
                    .valuesIterator(config.proxy ? PROXY_AUTHORIZATION : AUTHORIZATION);
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
                return onAccessDenied(request, factory);
            }

            // Because most browsers use UTF-8 encoding for usernames and passwords (see:
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#Character_encoding_of_HTTP_authentication)
            // and RFC7617 says: the default encoding is undefined (most implementations chose UTF-8), as long as it is
            // compatible with US-ASCII, we can use UTF-8 to decode a "user-id:password" pair here:
            final String userIdAndPassword = new String(Base64.getDecoder().decode(token), UTF_8);
            final int colonIdx = userIdAndPassword.indexOf(':');
            if (colonIdx < 1) {
                return onAccessDenied(request, factory);
            }

            final String userId = userIdAndPassword.substring(0, colonIdx);
            final String password = userIdAndPassword.length() - 1 == colonIdx ? "" :
                    userIdAndPassword.substring(colonIdx + 1);

            return config.credentialsVerifier.apply(userId, password)
                    .flatMap(userInfo -> onAuthenticated(ctx, request, factory, userInfo).subscribeShareContext())
                    .onErrorResume(t -> {
                        if (t instanceof AuthenticationException) {
                            return onAccessDenied(request, factory);
                        }

                        LOGGER.debug("Unexpected exception during authentication", t);
                        return failed(t);
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

        private Single<StreamingHttpResponse> onAccessDenied(final HttpMetaData requestMetaData,
                                                             final StreamingHttpResponseFactory factory) {
            final StreamingHttpResponse response = factory.newResponse(
                    config.proxy ? PROXY_AUTHENTICATION_REQUIRED : UNAUTHORIZED).version(requestMetaData.version());
            HttpHeaders headers = response.headers();
            headers.set(config.proxy ? PROXY_AUTHENTICATE : WWW_AUTHENTICATE, authenticateHeader);
            headers.set(CONTENT_LENGTH, ZERO);
            return succeeded(response);
        }

        private Single<StreamingHttpResponse> onAuthenticated(final HttpServiceContext ctx,
                                                              final StreamingHttpRequest request,
                                                              final StreamingHttpResponseFactory factory,
                                                              final UserInfo userInfo) {
            if (config.userInfoKey != null) {
                AsyncContext.put(config.userInfoKey, userInfo);
            }
            return delegate().handle(ctx, request, factory);
        }
    }
}
