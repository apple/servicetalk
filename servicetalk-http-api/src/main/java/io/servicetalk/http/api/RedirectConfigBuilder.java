/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.RedirectConfig.RedirectRequestTransformer;
import io.servicetalk.http.api.RedirectConfig.ShouldRedirectPredicate;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static java.util.Arrays.asList;
import static java.util.Arrays.sort;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link RedirectConfig}.
 */
public final class RedirectConfigBuilder {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirects usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final List<HttpRequestMethod> DEFAULT_ALLOWED_METHODS = toSortedList(GET, HEAD);
    private static final ShouldRedirectPredicate DEFAULT_SHOULD_REDIRECT_PREDICATE =
            (relative, redirectCnt, previousRequest, redirectResponse) -> true;
    private static final CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = {};
    private static final RedirectRequestTransformer DEFAULT_REDIRECT_REQUEST_TRANSFORMER =
            (relative, previousRequest, redirectResponse, redirectRequest) -> redirectRequest;

    private int maxRedirects = DEFAULT_MAX_REDIRECTS;
    private boolean allowNonRelativeRedirects;
    @Nullable
    private HttpRequestMethod[] allowedMethods;
    private ShouldRedirectPredicate shouldRedirectPredicate = DEFAULT_SHOULD_REDIRECT_PREDICATE;
    private boolean changePostToGet;
    private CharSequence[] headersToRedirect = EMPTY_CHAR_SEQUENCE_ARRAY;
    private boolean redirectPayloadBody;
    private CharSequence[] trailersToRedirect = EMPTY_CHAR_SEQUENCE_ARRAY;
    private RedirectRequestTransformer redirectRequestTransformer = DEFAULT_REDIRECT_REQUEST_TRANSFORMER;

    /**
     * Sets the maximum number of redirects to follow.
     *
     * @param maxRedirects The maximum number of redirects to follow
     * @return {@code this}
     */
    public RedirectConfigBuilder maxRedirects(final int maxRedirects) {
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("maxRedirects: " + maxRedirects + " (expected >= 0)");
        }
        this.maxRedirects = maxRedirects;
        return this;
    }

    /**
     * Sets {@link HttpRequestMethod}s that are allowed to follow redirects.
     *
     * @param methods {@link HttpRequestMethod}s that are allowed to follow redirects
     * @return {@code this}
     */
    public RedirectConfigBuilder allowedMethods(final HttpRequestMethod... methods) {
        this.allowedMethods = requireNonNull(methods);
        return this;
    }

    /**
     * Allows non-relative redirects (if supported by the underlying client implementation). Non-relative redirects are
     * redirects to either a different target host/port or a different scheme.
     * <p>
     * Notes:
     * <ol>
     *     <li>This option has effect only when redirections is performed by a client that is capable to communicate
     *     with multiple target hosts or schemes, like the one which is produced by
     *     {@link MultiAddressHttpClientBuilder}. If a client is limited to only one target host/port/scheme, it will
     *     follow only relative redirects.</li>
     *     <li>For security reasons, redirection should not automatically copy headers nor message body of the original
     *     request for non-relative locations. Use {@link #headersToRedirect(CharSequence...)},
     *     {@link #redirectPayloadBody(boolean)}, {@link #trailersToRedirect(CharSequence...)}, or
     *     {@link #redirectRequestTransformer(RedirectRequestTransformer)} if headers or message body should be
     *     preserved.</li>
     * </ol>
     *
     * @param allowNonRelativeRedirects If {@code true}, redirection will follow non-relative locations (if supported by
     * the underlying client implementation)
     * @return {@code this}
     *
     * @see MultiAddressHttpClientBuilder
     * @see #headersToRedirect(CharSequence...)
     * @see #redirectPayloadBody(boolean)
     * @see #trailersToRedirect(CharSequence...)
     * @see #redirectRequestTransformer(RedirectRequestTransformer)
     */
    public RedirectConfigBuilder allowNonRelativeRedirects(final boolean allowNonRelativeRedirects) {
        this.allowNonRelativeRedirects = allowNonRelativeRedirects;
        return this;
    }

    /**
     * Sets a predicate for an additional check to decide if the redirect should be performed or not based on the given
     * context.
     *
     * @param predicate {@link ShouldRedirectPredicate} for an additional check to decide if the redirect should be
     * performed or not based on the given context
     * @return {@code this}
     * @see #maxRedirects(int)
     * @see #allowedMethods(HttpRequestMethod...)
     * @see #allowNonRelativeRedirects(boolean)
     */
    public RedirectConfigBuilder shouldRedirectPredicate(final ShouldRedirectPredicate predicate) {
        this.shouldRedirectPredicate = requireNonNull(predicate);
        return this;
    }

    /**
     * Enforces change of the request method from {@link HttpRequestMethod#POST POST} to
     * {@link HttpRequestMethod#GET GET} for subsequent requests for
     * {@link HttpResponseStatus#MOVED_PERMANENTLY 301 (Moved Permanently)} and
     * {@link HttpResponseStatus#FOUND 302 (Found)} status codes.
     * <p>
     * For historical reasons, RFC7231 sections
     * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4.2">6.4.2</a> and
     * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4.3">6.4.3</a> allow user agents to change the
     * request method from {@link HttpRequestMethod#POST POST} to {@link HttpRequestMethod#GET GET} for the subsequent
     * request. If this behavior is undesired, this option can be turned off or
     * {@link HttpResponseStatus#TEMPORARY_REDIRECT 307 (Temporary Redirect)} and
     * {@link HttpResponseStatus#PERMANENT_REDIRECT 308 (Permanent Redirect)} status codes can be used instead.
     *
     * @param changePostToGet if {@code true}, request method will change from {@link HttpRequestMethod#POST POST}
     * to {@link HttpRequestMethod#GET GET} while following
     * {@link HttpResponseStatus#MOVED_PERMANENTLY 301 (Moved Permanently)} or
     * {@link HttpResponseStatus#FOUND 302 (Found)} redirect status codes
     * @return {@code this}
     */
    public RedirectConfigBuilder changePostToGet(final boolean changePostToGet) {
        this.changePostToGet = changePostToGet;
        return this;
    }

    /**
     * Configures headers that have to be copied from the original request on each non-relative redirect.
     * <p>
     * Note: for security reasons, redirection should not automatically copy any headers from the original request when
     * it performs a non-relative redirect. For relative redirects, everything is copied by default. Use
     * {@link #redirectRequestTransformer(RedirectRequestTransformer)} if more customization required.
     *
     * @param headerNames Names of headers that have to be copied on each non-relative redirect
     * @return {@code this}
     * @see #allowNonRelativeRedirects(boolean)
     * @see #redirectPayloadBody(boolean)
     * @see #trailersToRedirect(CharSequence...)
     * @see #redirectRequestTransformer(RedirectRequestTransformer)
     */
    public RedirectConfigBuilder headersToRedirect(final CharSequence... headerNames) {
        this.headersToRedirect = requireNonNull(headerNames);
        return this;
    }

    /**
     * Allows redirecting payload body of the original request on each non-relative redirect.
     * <p>
     * Note: for security reasons, redirection should not automatically copy payload body of the original request when
     * it performs a non-relative redirect. For relative redirects, everything is copied by default. Use
     * {@link #redirectRequestTransformer(RedirectRequestTransformer)} if more customization required.
     * <p>
     * <b>Note:</b> This option expects that the redirected {@link StreamingHttpRequest requests} have a
     * {@link StreamingHttpRequest#payloadBody() payload body} that is
     * <a href="http://reactivex.io/documentation/operators/replay.html">replayable</a>, i.e. multiple subscribes to the
     * payload {@link Publisher} observe the same data. {@link Publisher}s that do not emit any data or which are
     * created from in-memory data are typically replayable.
     *
     * @param redirectPayloadBody If {@code true}, payload body of the original request will be repeated for each
     * non-relative redirect
     * @return {@code this}
     * @see #allowNonRelativeRedirects(boolean)
     * @see #headersToRedirect(CharSequence...)
     * @see #trailersToRedirect(CharSequence...)
     * @see #redirectRequestTransformer(RedirectRequestTransformer)
     */
    public RedirectConfigBuilder redirectPayloadBody(final boolean redirectPayloadBody) {
        this.redirectPayloadBody = redirectPayloadBody;
        return this;
    }

    /**
     * Configures trailers that have to be copied from the original request on each non-relative redirect.
     * <p>
     * Note: for security reasons, redirection should not automatically copy any trailers from the original request when
     * it performs a non-relative redirect. For relative redirects, everything is copied by default. Use
     * {@link #redirectRequestTransformer(RedirectRequestTransformer)} if more customization required.
     *
     * @param trailerNames Names of trailers that have to be copied on each non-relative redirect
     * @return {@code this}
     * @see #allowNonRelativeRedirects(boolean)
     * @see #headersToRedirect(CharSequence...)
     * @see #redirectPayloadBody(boolean)
     * @see #redirectRequestTransformer(RedirectRequestTransformer)
     */
    public RedirectConfigBuilder trailersToRedirect(final CharSequence... trailerNames) {
        this.trailersToRedirect = requireNonNull(trailerNames);
        return this;
    }

    /**
     * Sets a transformer to apply further modifications for the redirect request after it was initialized.
     * <p>
     * It can be used to add/remove headers, payload body, or trailers.
     *
     * @param transformer {@link RedirectRequestTransformer} that modifies a request for redirect
     * @return {@code this}
     * @see #headersToRedirect(CharSequence...)
     * @see #redirectPayloadBody(boolean)
     * @see #trailersToRedirect(CharSequence...)
     */
    public RedirectConfigBuilder redirectRequestTransformer(final RedirectRequestTransformer transformer) {
        this.redirectRequestTransformer = requireNonNull(transformer);
        return this;
    }

    public RedirectConfig build() {
        return new DefaultRedirectConfig(maxRedirects,
                allowedMethods == null ? DEFAULT_ALLOWED_METHODS : toSortedList(allowedMethods.clone()),
                allowNonRelativeRedirects, shouldRedirectPredicate, changePostToGet,
                new DefaultRedirectRequestTransformer(headersToRedirect.clone(), redirectPayloadBody,
                        trailersToRedirect.clone(), redirectRequestTransformer));
    }

    private static List<HttpRequestMethod> toSortedList(final HttpRequestMethod... allowedMethods) {
        sort(allowedMethods); // Soring is required because RedirectSingle uses binary search
        return unmodifiableList(asList(allowedMethods));
    }

    private static final class DefaultRedirectConfig implements RedirectConfig {

        private final int maxRedirects;
        private final List<HttpRequestMethod> allowedMethods;
        private final boolean allowNonRelativeRedirects;
        private final ShouldRedirectPredicate shouldRedirectPredicate;
        private final boolean changePostToGet;
        private final RedirectRequestTransformer redirectRequestTransformer;

        private DefaultRedirectConfig(final int maxRedirects,
                                      final List<HttpRequestMethod> allowedMethods,
                                      final boolean allowNonRelativeRedirects,
                                      final ShouldRedirectPredicate shouldRedirectPredicate,
                                      final boolean changePostToGet,
                                      final RedirectRequestTransformer redirectRequestTransformer) {
            this.maxRedirects = maxRedirects;
            this.allowedMethods = allowedMethods;
            this.allowNonRelativeRedirects = allowNonRelativeRedirects;
            this.shouldRedirectPredicate = shouldRedirectPredicate;
            this.changePostToGet = changePostToGet;
            this.redirectRequestTransformer = redirectRequestTransformer;
        }

        @Override
        public int maxRedirects() {
            return maxRedirects;
        }

        @Override
        public List<HttpRequestMethod> allowedMethods() {
            return allowedMethods;
        }

        @Override
        public boolean allowNonRelativeRedirects() {
            return allowNonRelativeRedirects;
        }

        @Override
        public ShouldRedirectPredicate shouldRedirectPredicate() {
            return shouldRedirectPredicate;
        }

        @Override
        public boolean changePostToGet() {
            return changePostToGet;
        }

        @Override
        public RedirectRequestTransformer redirectRequestTransformer() {
            return redirectRequestTransformer;
        }
    }

    private static final class DefaultRedirectRequestTransformer implements RedirectRequestTransformer {

        private static final TrailersTransformer<Object, Buffer> NOOP_TRAILERS_TRANSFORMER =
                new StatelessTrailersTransformer<>();

        private final CharSequence[] headersToRedirect;
        private final boolean redirectPayloadBody;
        private final CharSequence[] trailersToRedirect;
        private final RedirectRequestTransformer userDefinedTransformer;

        DefaultRedirectRequestTransformer(final CharSequence[] headersToRedirect,
                                          final boolean redirectPayloadBody,
                                          final CharSequence[] trailersToRedirect,
                                          final RedirectRequestTransformer userDefinedTransformer) {
            this.headersToRedirect = headersToRedirect;
            this.redirectPayloadBody = redirectPayloadBody;
            this.trailersToRedirect = trailersToRedirect;
            this.userDefinedTransformer = userDefinedTransformer;
        }

        @Override
        public StreamingHttpRequest apply(final boolean relative,
                                          final StreamingHttpRequest previousRequest,
                                          final StreamingHttpResponse redirectResponse,
                                          final StreamingHttpRequest redirectRequest) {
            if (relative) {
                fullCopy(previousRequest, redirectRequest);
            } else {
                safeCopy(previousRequest, redirectRequest);
            }
            return userDefinedTransformer.apply(relative, previousRequest, redirectResponse, redirectRequest);
        }

        private static void fullCopy(final StreamingHttpRequest originalRequest,
                                     final StreamingHttpRequest redirectRequest) {
            redirectRequest.setHeaders(originalRequest.headers());
            redirectRequest.transformMessageBody(p -> p.ignoreElements().concat(originalRequest.messageBody()));
            // Use `transform` to update PayloadInfo flags, assuming trailers may be included in the message body
            redirectRequest.transform(NOOP_TRAILERS_TRANSFORMER);
            // FIXME: instead of `transform`, preserve original PayloadInfo/FlushStrategy when it's API is available
        }

        private void safeCopy(final StreamingHttpRequest request, final StreamingHttpRequest redirectRequest) {
            // NOTE: for security reasons we do not copy any headers or payload body from the original request by
            // default for non-relative redirects.
            for (CharSequence headerName : headersToRedirect) {
                for (CharSequence headerValue : request.headers().values(headerName)) {
                    redirectRequest.addHeader(headerName, headerValue);
                }
            }

            if (redirectPayloadBody) {
                if (trailersToRedirect.length == 0) {
                    redirectRequest.payloadBody(request.payloadBody());
                } else {
                    redirectRequest.transformMessageBody(p -> p.ignoreElements().concat(request.messageBody()))
                            .transform(new StatelessTrailersTransformer<Buffer>() {
                                @Override
                                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                    return filterTrailers(trailers, trailersToRedirect);
                                }
                            });
                }
            } else if (trailersToRedirect.length != 0) {
                redirectRequest.transformMessageBody(p -> p.ignoreElements().concat(request.messageBody()
                                .filter(item -> item instanceof HttpHeaders)))
                        .transform(new StatelessTrailersTransformer<Buffer>() {
                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return filterTrailers(trailers, trailersToRedirect);
                            }
                        });
            }
        }

        private static HttpHeaders filterTrailers(final HttpHeaders trailers, final CharSequence[] keepOnly) {
            Iterator<Map.Entry<CharSequence, CharSequence>> it = trailers.iterator();
            while (it.hasNext()) {
                Map.Entry<CharSequence, CharSequence> entry = it.next();
                if (Arrays.stream(keepOnly).noneMatch(name -> contentEqualsIgnoreCase(entry.getKey(), name))) {
                    it.remove();
                }
            }
            return trailers;
        }
    }
}
