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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.concurrent.api.Publisher.empty;

/**
 * A HTTP request filter that performs automatic redirects if {@link
 * StreamingHttpRequester#request(StreamingHttpRequest)} method receives 3XX status code in the {@link
 * StreamingHttpResponse response}.
 * <p>
 * <b>Notes</b>:
 * <ul>
 *     <li>This implementation will not parse a payload for 300 (Multiple Choices) response code, if a preferred
 *     choice's URI reference is not returned in the {@link HttpHeaderNames#LOCATION Location} header.</li>
 *     <li>This implementation creates a redirect request internally with a request target in the
 *     <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> and assumes that
 *     {@link StreamingHttpRequest#requestTarget() request target} and {@link HttpHeaderNames#HOST host header} are
 *     sufficient for a provided request-to-group-key function.</li>
 *     <li>Depending on its parameters and context: {@link HttpClient} or {@link HttpConnection}, this filter may be
 *     limited to automatically following relative redirects only.</li>
 * </ul>
 */
public final class RedirectingHttpRequesterFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirects usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final int maxRedirects;
    private final boolean onlyRelativeClient;
    private final boolean onlyRelativeConnection;

    /**
     * Create a new instance, only performing relative redirects.
     */
    public RedirectingHttpRequesterFilter() {
        this(true, true);
    }

    /**
     * Create a new instance, only performing relative redirects.
     *
     * @param maxRedirects The maximum number of follow up redirects.
     */
    public RedirectingHttpRequesterFilter(final int maxRedirects) {
        this(true, true, maxRedirects);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     */
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient) {
        this(onlyRelativeClient, true, DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param maxRedirects The maximum number of follow up redirects.
     */
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final int maxRedirects) {
        this(onlyRelativeClient, true, maxRedirects);
    }

    /**
     * Create a new instance.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param onlyRelativeConnection Limits the redirects to relative paths for {@link HttpConnection} filters.
     */
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient, final boolean onlyRelativeConnection) {
        this(onlyRelativeClient, onlyRelativeConnection, DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param onlyRelativeConnection Limits the redirects to relative paths for {@link HttpConnection} filters.
     * @param maxRedirects The maximum number of follow up redirects.
     */
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final boolean onlyRelativeConnection,
                                          final int maxRedirects) {
        this.onlyRelativeClient = onlyRelativeClient;
        this.onlyRelativeConnection = onlyRelativeConnection;
        this.maxRedirects = maxRedirects;
    }

    @Override
    public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate, strategy, request, onlyRelativeClient);
            }

            @Override
            protected Single<ReservedStreamingHttpConnection> reserveConnection(
                    final StreamingHttpClient delegate,
                    final HttpExecutionStrategy strategy,
                    final HttpRequestMetaData metaData) {
                return delegate.reserveConnection(strategy, metaData)
                        .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                            @Override
                            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                         final StreamingHttpRequest request) {
                                return RedirectingHttpRequesterFilter.this.request(delegate, strategy, request,
                                        onlyRelativeConnection);
                            }
                        });
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    /**
     * Create a {@link StreamingHttpClientFilter} using the provided {@link StreamingHttpClient}.
     *
     * @param client {@link StreamingHttpClient} to filter
     * @return {@link StreamingHttpClientFilter} using the provided {@link StreamingHttpClient}.
     */
    public StreamingHttpClientFilter create(final StreamingHttpClient client) {
        return create(client, empty());
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate(), strategy, request, onlyRelativeConnection);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request,
                                                  final boolean onlyRelative) {
        final Single<StreamingHttpResponse> response = delegate.request(strategy, request);
        if (maxRedirects <= 0) {
            return response;
        }
        return new RedirectSingle(strategy, response, request, maxRedirects, delegate, onlyRelative);
    }
}
