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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import static java.util.Objects.requireNonNull;

/**
 * An {@link StreamingHttpClient} wrapper that performs automatic redirects if
 * {@link StreamingHttpClient#request(StreamingHttpRequest)} method receives 3XX status code in the
 * {@link StreamingHttpResponse response}.
 * <p>
 * <b>Notes</b>:
 * <ul>
 *     <li>This implementation will not parse a payload for 300 (Multiple Choices) response code, if a preferred
 *     choice's URI reference is not returned in the {@link HttpHeaderNames#LOCATION Location} header.</li>
 *     <li>This implementation creates a redirect request internally with a request target in the
 *     <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> and assumes that
 *     {@link StreamingHttpRequest#requestTarget() request target} and {@link HttpHeaderNames#HOST host header} are
 *     sufficient for a provided request-to-group-key function.</li>
 * </ul>
 *
 */
public final class RedirectingHttpClientFilter extends StreamingHttpClientAdapter {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirections usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final int maxRedirects;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpClient} to delegate to.
     */
    public RedirectingHttpClientFilter(final StreamingHttpClient delegate) {
        this(delegate, DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpClient} to delegate to.
     * @param maxRedirects The maximum number of follow up redirects.
     */
    public RedirectingHttpClientFilter(final StreamingHttpClient delegate,
                                       final int maxRedirects) {
        super(requireNonNull(delegate));
        this.maxRedirects = maxRedirects;
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        final Single<StreamingHttpResponse> response = super.request(strategy, request);
        if (maxRedirects <= 0) {
            return response;
        }
        return new RedirectSingle(strategy, response, request, maxRedirects, super.delegate());
    }
}
