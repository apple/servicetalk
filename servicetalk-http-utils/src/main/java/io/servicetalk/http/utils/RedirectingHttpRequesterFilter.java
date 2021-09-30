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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatus.StatusClass;
import io.servicetalk.http.api.RedirectConfig;
import io.servicetalk.http.api.RedirectConfigBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.List;

import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpResponseStatus.FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.MOVED_PERMANENTLY;
import static java.util.Arrays.asList;
import static java.util.Arrays.sort;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * An HTTP request filter that performs automatic
 * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4">redirection</a> if a client receives
 * {@link StatusClass#REDIRECTION_3XX 3XX status code} in the {@link HttpResponseMetaData response}.
 * <p>
 * <b>Notes</b>:
 * <ul>
 *     <li>This implementation will not parse a payload for
 *     {@link HttpResponseStatus#MULTIPLE_CHOICES 300 (Multiple Choices)} response code, if a preferred choice's URI
 *     reference is not returned in the {@link HttpHeaderNames#LOCATION Location} header.</li>
 *     <li>Depending on its parameters and context: {@link HttpClient} or {@link HttpConnection}, this filter may be
 *     limited to automatically following relative redirects only.</li>
 *     <li>This implementation will automatically redirect headers and message body for relative locations.</li>
 *     <li>For security reasons, this implementation will NOT automatically redirect headers and message body for
 *     non-relative locations. Use {@link RedirectConfig} to opt-in for redirect of requires request components.
 *     </li>
 * </ul>
 */
public final class RedirectingHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                             StreamingHttpConnectionFilterFactory,
                                                             HttpExecutionStrategyInfluencer {

    private static final RedirectConfig DEFAULT_CONFIG = new RedirectConfigBuilder().build();

    private final RedirectConfig config;

    /**
     * Create a new instance, only performing relative redirects.
     */
    public RedirectingHttpRequesterFilter() {
        this(DEFAULT_CONFIG);
    }

    /**
     * Create a new instance, only performing relative redirects.
     *
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link #RedirectingHttpRequesterFilter(RedirectConfig)}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final int maxRedirects) {
        this(true, maxRedirects);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @deprecated Use {@link #RedirectingHttpRequesterFilter(RedirectConfig)}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient) {
        this(onlyRelativeClient, BackwardCompatibleRedirectConfig.DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link #RedirectingHttpRequesterFilter(RedirectConfig)}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final int maxRedirects) {
        this(new BackwardCompatibleRedirectConfig(maxRedirects, !onlyRelativeClient));
    }

    /**
     * Create a new instance.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param onlyRelativeConnection Limits the redirects to relative paths for {@link HttpConnection} filters.
     * @deprecated Use {@link #RedirectingHttpRequesterFilter(RedirectConfig)}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final boolean onlyRelativeConnection) {
        this(onlyRelativeClient, BackwardCompatibleRedirectConfig.DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param onlyRelativeConnection Limits the redirects to relative paths for {@link HttpConnection} filters.
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link #RedirectingHttpRequesterFilter(RedirectConfig)}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final boolean onlyRelativeConnection,
                                          final int maxRedirects) {
        this(onlyRelativeClient, maxRedirects);
    }

    /**
     * Create a new instance.
     *
     * @param config {@link RedirectConfig} to customize the behavior.
     */
    public RedirectingHttpRequesterFilter(final RedirectConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate, strategy, request,
                        config.allowNonRelativeRedirects());
            }

            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpExecutionStrategy strategy,
                    final HttpRequestMetaData metaData) {
                return delegate().reserveConnection(strategy, metaData)
                        .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                            @Override
                            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                            final HttpExecutionStrategy strategy,
                                                                            final StreamingHttpRequest request) {
                                return RedirectingHttpRequesterFilter.this.request(delegate, strategy, request, false);
                            }
                        });
            }
       };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate(), strategy, request, false);
            }
        };
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request,
                                                  final boolean allowNonRelativeRedirects) {
        final Single<StreamingHttpResponse> response = delegate.request(strategy,
                // Duplicate each payload buffer chunk to allow safely replaying it without worry that indexes can move
                request.transformMessageBody(p -> p.map(item -> {
                    if (item instanceof Buffer) {
                        return ((Buffer) item).duplicate();
                    }
                    return item;
                })));
        if (config.maxRedirects() <= 0) {
            return response;
        }
        return new RedirectSingle(delegate, strategy, request, response, allowNonRelativeRedirects, config);
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    @Deprecated
    private static final class BackwardCompatibleRedirectConfig implements RedirectConfig {

        // https://tools.ietf.org/html/rfc2068#section-10.3 says:
        // A user agent SHOULD NOT automatically redirect a request more than 5 times,
        // since such redirects usually indicate an infinite loop.
        static final int DEFAULT_MAX_REDIRECTS = 5;
        private static final List<HttpRequestMethod> DEFAULT_ALLOWED_METHODS =
                toSortedList(GET, HEAD, POST, PUT, DELETE, PATCH);
        private static final ShouldRedirectPredicate DEFAULT_SHOULD_REDIRECT_PREDICATE =
                (relative, redirectCnt, previousRequest, redirectResponse) -> true;
        private static final RedirectRequestTransformer DEFAULT_REDIRECT_REQUEST_TRANSFORMER =
                (relative, previousRequest, redirectResponse, redirectRequest) -> {
                    // https://tools.ietf.org/html/rfc7231#section-6.4.2
                    // https://tools.ietf.org/html/rfc7231#section-6.4.3
                    // Note for 301 (Moved Permanently) and 302 (Found):
                    //     For historical reasons, a user agent MAY change the request method from POST to GET for the
                    //     subsequent request.  If this behavior is undesired, the 307 (Temporary Redirect) or
                    //     308 (Permanent Redirect) status codes can be used instead.
                    final int statusCode = redirectResponse.status().code();
                    if ((statusCode == MOVED_PERMANENTLY.code() || statusCode == FOUND.code()) &&
                            POST.name().equals(previousRequest.method().name())) {
                        redirectRequest.method(GET);
                    }
                    return redirectRequest;
                };

        private final int maxRedirects;
        private final boolean allowNonRelativeRedirects;

        BackwardCompatibleRedirectConfig(final int maxRedirects, final boolean allowNonRelativeRedirects) {
            this.maxRedirects = maxRedirects;
            this.allowNonRelativeRedirects = allowNonRelativeRedirects;
        }

        @Override
        public int maxRedirects() {
            return maxRedirects;
        }

        @Override
        public List<HttpRequestMethod> allowedMethods() {
            return DEFAULT_ALLOWED_METHODS;
        }

        @Override
        public boolean allowNonRelativeRedirects() {
            return allowNonRelativeRedirects;
        }

        @Override
        public ShouldRedirectPredicate shouldRedirectPredicate() {
            return DEFAULT_SHOULD_REDIRECT_PREDICATE;
        }

        @Override
        public RedirectRequestTransformer redirectRequestTransformer() {
            return DEFAULT_REDIRECT_REQUEST_TRANSFORMER;
        }

        private static List<HttpRequestMethod> toSortedList(final HttpRequestMethod... allowedMethods) {
            sort(allowedMethods); // Soring is required because RedirectSingle uses binary search
            return unmodifiableList(asList(allowedMethods));
        }
    }
}
