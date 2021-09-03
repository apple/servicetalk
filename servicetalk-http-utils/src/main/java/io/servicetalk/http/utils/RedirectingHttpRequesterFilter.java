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
import io.servicetalk.concurrent.api.TriConsumer;
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
import io.servicetalk.http.api.RedirectConfiguration;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RedirectSingle.Config;

import java.util.Arrays;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
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
 *     non-relative locations. Use {@link RedirectConfiguration} to opt-in for redirect of requires request components.
 *     </li>
 * </ul>
 */
public final class RedirectingHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                             StreamingHttpConnectionFilterFactory,
                                                             HttpExecutionStrategyInfluencer {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirects usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final HttpRequestMethod[] DEFAULT_ALLOWED_METHODS = {GET, HEAD};
    private static final BiPredicate<HttpRequestMetaData, HttpResponseMetaData> DEFAULT_SHOULD_REDIRECT =
            (req, resp) -> true;
    private static final CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = {};
    private static final TriConsumer<StreamingHttpRequest, StreamingHttpResponse, StreamingHttpRequest>
            DEFAULT_PREPARE_REQUEST = (original, response, redirect) -> { };

    private final boolean allowNonRelativeRedirects;
    private final Config config;

    /**
     * Create a new instance, only performing relative redirects.
     *
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter() {
        this(DEFAULT_MAX_REDIRECTS, true);
    }

    /**
     * Create a new instance, only performing relative redirects.
     *
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final int maxRedirects) {
        this(maxRedirects, true);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient) {
        this(DEFAULT_MAX_REDIRECTS, onlyRelativeClient);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final int maxRedirects) {
        this(maxRedirects, onlyRelativeClient);
    }

    /**
     * Create a new instance.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param onlyRelativeConnection Limits the redirects to relative paths for {@link HttpConnection} filters.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final boolean onlyRelativeConnection) {
        this(DEFAULT_MAX_REDIRECTS, onlyRelativeClient);
    }

    /**
     * Create a new instance.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @param onlyRelativeConnection Limits the redirects to relative paths for {@link HttpConnection} filters.
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient,
                                          final boolean onlyRelativeConnection,
                                          final int maxRedirects) {
        this(maxRedirects, onlyRelativeClient);
    }

    private RedirectingHttpRequesterFilter(final int maxRedirects,
                                           final boolean onlyRelativeClient) {
        this(!onlyRelativeClient, new Config(maxRedirects, toSortedNames(DEFAULT_ALLOWED_METHODS),
                DEFAULT_SHOULD_REDIRECT,
                /* changePostToGet */ true,  // use "true" for backward compatibility
                /* headersToRedirect */ EMPTY_CHAR_SEQUENCE_ARRAY,
                /* redirectPayloadBody */ false,
                /* trailersToRedirect */ EMPTY_CHAR_SEQUENCE_ARRAY,
                DEFAULT_PREPARE_REQUEST));
    }

    private RedirectingHttpRequesterFilter(final boolean allowNonRelativeRedirects,
                                           final Config config) {
        this.allowNonRelativeRedirects = allowNonRelativeRedirects;
        this.config = config;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate, strategy, request,
                        allowNonRelativeRedirects);
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
        if (config.maxRedirects <= 0) {
            return response;
        }
        return new RedirectSingle(delegate, strategy, request, response, allowNonRelativeRedirects, config);
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    /**
     * Builder for {@link RedirectingHttpRequesterFilter}.
     */
    public static final class Builder implements RedirectConfiguration {

        private int maxRedirects = DEFAULT_MAX_REDIRECTS;
        private boolean allowNonRelativeRedirects;
        private HttpRequestMethod[] allowedMethods = DEFAULT_ALLOWED_METHODS;
        private BiPredicate<HttpRequestMetaData, HttpResponseMetaData> shouldRedirect = DEFAULT_SHOULD_REDIRECT;
        private boolean changePostToGet;
        @Nullable
        private CharSequence[] headersToRedirect;
        private boolean redirectPayloadBody;
        @Nullable
        private CharSequence[] trailersToRedirect;
        private TriConsumer<StreamingHttpRequest, StreamingHttpResponse, StreamingHttpRequest> prepareRequest =
                DEFAULT_PREPARE_REQUEST;

        @Override
        public Builder maxRedirects(final int maxRedirects) {
            if (maxRedirects < 0) {
                throw new IllegalArgumentException("maxRedirects: " + maxRedirects + " (expected >= 0)");
            }
            this.maxRedirects = maxRedirects;
            return this;
        }

        @Override
        public Builder allowNonRelativeRedirects(final boolean allowNonRelativeRedirects) {
            this.allowNonRelativeRedirects = allowNonRelativeRedirects;
            return this;
        }

        @Override
        public Builder allowedMethods(HttpRequestMethod... methods) {
            this.allowedMethods = requireNonNull(methods);
            return this;
        }

        @Override
        public Builder shouldRedirect(final BiPredicate<HttpRequestMetaData, HttpResponseMetaData> shouldRedirect) {
            this.shouldRedirect = requireNonNull(shouldRedirect);
            return this;
        }

        @Override
        public Builder changePostToGet(final boolean changePostToGet) {
            this.changePostToGet = changePostToGet;
            return this;
        }

        @Override
        public Builder headersToRedirect(final CharSequence... headerNames) {
            this.headersToRedirect = requireNonNull(headerNames);
            return this;
        }

        @Override
        public Builder redirectPayloadBody(final boolean redirectPayloadBody) {
            this.redirectPayloadBody = redirectPayloadBody;
            return this;
        }

        @Override
        public Builder trailersToRedirect(final CharSequence... trailerNames) {
            this.trailersToRedirect = requireNonNull(trailerNames);
            return this;
        }

        @Override
        public Builder prepareRequest(
                final TriConsumer<StreamingHttpRequest, StreamingHttpResponse, StreamingHttpRequest> prepareRequest) {
            this.prepareRequest = requireNonNull(prepareRequest);
            return this;
        }

        /**
         * Builds a new instance of {@link RedirectingHttpRequesterFilter}.
         *
         * @return a new instance of {@link RedirectingHttpRequesterFilter}
         */
        public RedirectingHttpRequesterFilter build() {
            return new RedirectingHttpRequesterFilter(allowNonRelativeRedirects, new Config(maxRedirects,
                    toSortedNames(allowedMethods), shouldRedirect, changePostToGet,
                    headersToRedirect == null ? EMPTY_CHAR_SEQUENCE_ARRAY : headersToRedirect.clone(),
                    redirectPayloadBody,
                    trailersToRedirect == null ? EMPTY_CHAR_SEQUENCE_ARRAY : trailersToRedirect.clone(),
                    prepareRequest));
        }
    }

    private static String[] toSortedNames(final HttpRequestMethod[] allowedMethods) {
        return Arrays.stream(allowedMethods).map(HttpRequestMethod::name)
                .sorted()   // Soring is required because RedirectSingle uses Arrays.binarySearch
                .toArray(String[]::new);
    }
}
