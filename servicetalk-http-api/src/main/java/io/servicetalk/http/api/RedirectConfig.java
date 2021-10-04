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

import java.util.Set;

/**
 * Configuration options for <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4">redirection</a>.
 */
public interface RedirectConfig {

    /**
     * Predicate to make the final decision if redirect should be performed or not based on the given context.
     * <p>
     * Implementations should prefer running this predicate as the last check, after validation of the
     * {@link HttpResponseStatus}, {@link RedirectConfig#maxRedirects()}, {@link RedirectConfig#allowedMethods()},
     * presence of the {@link HttpHeaderNames#LOCATION Location} header, and
     * {@link RedirectConfig#allowNonRelativeRedirects()}.
     */
    @FunctionalInterface
    interface RedirectPredicate {

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
     * request.
     * <p>
     * It can be used to add/remove headers, payload body, or trailers.
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
         * @return the final {@link StreamingHttpRequest} that will be sent to follow the redirect after all
         * transformations applied
         */
        StreamingHttpRequest apply(boolean relative, StreamingHttpRequest previousRequest,
                                   StreamingHttpResponse redirectResponse, StreamingHttpRequest redirectRequest);
    }

    /**
     * Maximum number of redirects to follow.
     *
     * @return maximum number of redirects to follow.
     */
    int maxRedirects();

    /**
     * {@link HttpRequestMethod}s that are allowed to follow redirects.
     *
     * @return {@link HttpRequestMethod}s that are allowed to follow redirects.
     */
    Set<HttpRequestMethod> allowedMethods();

    /**
     * Tells if redirection should follow non-relative redirects (if supported by the underlying client implementation).
     * Non-relative redirects are redirects to either a different target host/port or a different scheme.
     * <p>
     * Notes:
     * <ol>
     *     <li>This option has effect only when redirections is performed by a client that is capable to communicate
     *     with multiple target hosts or schemes, like the one which is produced by
     *     {@link MultiAddressHttpClientBuilder}. If a client is limited to only one target host/port/scheme, it will
     *     follow only relative redirects.</li>
     *     <li>For security reasons, redirection should not automatically copy headers nor message body of the original
     *     request for non-relative locations. Use {@link RedirectRequestTransformer} to preserve headers or message
     *     body.</li>
     * </ol>
     *
     * @return {@code true} if redirection should follow non-relative redirects (if supported by the underlying client
     * implementation).
     * @see MultiAddressHttpClientBuilder#followRedirects(RedirectConfig)
     */
    boolean allowNonRelativeRedirects();

    /**
     * {@link RedirectPredicate} to make the final decision if redirect should be performed or not based on the
     * given context.
     *
     * @return {@link RedirectPredicate} to make the final decision if redirect should be performed or not based
     * on the given context.
     */
    RedirectPredicate redirectPredicate();

    /**
     * {@link RedirectRequestTransformer} to apply further modifications for the redirect request after it was
     * initialized.
     *
     * @return {@link RedirectRequestTransformer} to apply further modifications for the redirect request after it was
     * initialized.
     */
    RedirectRequestTransformer redirectRequestTransformer();
}
