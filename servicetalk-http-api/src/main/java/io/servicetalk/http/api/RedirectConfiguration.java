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

import io.servicetalk.concurrent.api.Publisher;

/**
 * Configuration options for <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4">redirection</a>.
 */
public interface RedirectConfiguration {
    /**
     * Sets the maximum number of redirects to follow.
     *
     * @param maxRedirects The maximum number of redirects to follow
     * @return {@code this}
     */
    RedirectConfiguration maxRedirects(int maxRedirects);

    /**
     * Sets {@link HttpRequestMethod}s that are allowed to follow redirects.
     *
     * @param methods {@link HttpRequestMethod}s that are allowed to follow redirects
     * @return {@code this}
     */
    RedirectConfiguration allowedMethods(HttpRequestMethod... methods);

    /**
     * Allows non-relative redirects. Non-relative redirects are redirects to either a different target host/port or a
     * different scheme.
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
     *     {@link #prepareRequest(RedirectRequestTransformer)} if headers or message body should be preserved.</li>
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
     * @see #prepareRequest(RedirectRequestTransformer)
     */
    RedirectConfiguration allowNonRelativeRedirects(boolean allowNonRelativeRedirects);

    /**
     * Defines an additional check to decide if the redirect should be performed or not based on the given context.
     *
     * @param shouldRedirect {@link ShouldRedirectPredicate} to decide if the request should follow redirect or not
     * based on the given context
     * @return {@code this}
     * @see #maxRedirects(int)
     * @see #allowedMethods(HttpRequestMethod...)
     * @see #allowNonRelativeRedirects(boolean)
     */
    RedirectConfiguration shouldRedirect(ShouldRedirectPredicate shouldRedirect);

    /**
     * Enforces change from {@link HttpRequestMethod#POST POST} to {@link HttpRequestMethod#GET GET} for subsequent
     * requests for {@link HttpResponseStatus#MOVED_PERMANENTLY 301 (Moved Permanently)} and
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
    RedirectConfiguration changePostToGet(boolean changePostToGet);

    /**
     * Headers that have to be copied from the original request on each non-relative redirect.
     * <p>
     * Note: for security reasons, redirection should not automatically copy any headers from the original request when
     * it performs a non-relative redirect.
     *
     * @param headerNames Names of headers that have to be copied on each non-relative redirect
     * @return {@code this}
     * @see #allowNonRelativeRedirects(boolean)
     * @see #redirectPayloadBody(boolean)
     * @see #trailersToRedirect(CharSequence...)
     * @see #prepareRequest(RedirectRequestTransformer)
     */
    RedirectConfiguration headersToRedirect(CharSequence... headerNames);

    /**
     * Allows redirecting payload body of the original request on each non-relative redirect.
     * <p>
     * Note: for security reasons, redirection should not automatically copy payload body of the original request when
     * it performs a non-relative redirect.
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
     * @see #prepareRequest(RedirectRequestTransformer)
     */
    RedirectConfiguration redirectPayloadBody(boolean redirectPayloadBody);

    /**
     * Trailers that have to be copied from the original request on each non-relative redirect.
     * <p>
     * Note: for security reasons, redirection should not automatically copy any trailers from the original request when
     * it performs a non-relative redirect.
     *
     * @param trailerNames Names of trailers that have to be copied on each non-relative redirect
     * @return {@code this}
     * @see #allowNonRelativeRedirects(boolean)
     * @see #headersToRedirect(CharSequence...)
     * @see #redirectPayloadBody(boolean)
     * @see #prepareRequest(RedirectRequestTransformer)
     */
    RedirectConfiguration trailersToRedirect(CharSequence... trailerNames);

    /**
     * Applies further modifications for the redirect request after it was initialized.
     *
     * @param requestTransformer {@link RedirectRequestTransformer} that modifies a request for redirect
     * @return {@code this}
     * @see #allowNonRelativeRedirects(boolean)
     * @see #headersToRedirect(CharSequence...)
     * @see #redirectPayloadBody(boolean)
     * @see #trailersToRedirect(CharSequence...)
     */
    RedirectConfiguration prepareRequest(RedirectRequestTransformer requestTransformer);

    /**
     * Predicate to make the final decision if redirect should be performed or not based on the given context.
     * <p>
     * Implementations should prefer running this predicate as the last check, after validation of the
     * {@link HttpResponseStatus}, {@link #maxRedirects(int)}, {@link #allowedMethods(HttpRequestMethod...)}, presence
     * of the {@link HttpHeaderNames#LOCATION Location} header, and {@link #allowNonRelativeRedirects(boolean)}.
     */
    @FunctionalInterface
    interface ShouldRedirectPredicate {

        /**
         * Decides if a redirect should be performed or not based on the given context.
         *
         * @param relative if {@code true}, the redirect location was identified as relative to the previous request
         * @param redirectCount sequential counter of already processed redirects (starts from {@code 0})
         * @param previousRequest previous request that was redirected
         * @param redirectResponse response to redirect
         * @return {@code true} if the redirect should be processed based on the given context
         */
        boolean test(boolean relative, int redirectCount,
                     HttpRequestMetaData previousRequest, HttpResponseMetaData redirectResponse);
    }

    /**
     * Provides access to the full context of the redirect to apply transformations for the pre-initialized redirect
     * request. It can be used to add/remove headers, payload body, or trailers.
     */
    @FunctionalInterface
    interface RedirectRequestTransformer {

        /**
         * Applies transformations for the pre-initialized redirect request.
         *
         * @param relative if {@code true}, the redirect location was identified as relative to the previous request
         * @param previousRequest previous request that was redirected
         * @param redirectResponse response to redirect
         * @param redirectRequest pre-initialized request to follow the redirect
         * @return the final {@link StreamingHttpRequest} that will be send to follow the redirect after all
         * transformations applied
         */
        StreamingHttpRequest apply(boolean relative, StreamingHttpRequest previousRequest,
                                   StreamingHttpResponse redirectResponse, StreamingHttpRequest redirectRequest);
    }
}
