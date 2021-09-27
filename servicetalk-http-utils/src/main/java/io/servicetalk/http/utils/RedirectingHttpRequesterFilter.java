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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatus.StatusClass;
import io.servicetalk.http.api.RedirectConfiguration;
import io.servicetalk.http.api.RedirectConfiguration.RedirectRequestTransformer;
import io.servicetalk.http.api.RedirectConfiguration.ShouldRedirectPredicate;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TrailersTransformer;
import io.servicetalk.http.utils.RedirectSingle.Config;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
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
    private static final ShouldRedirectPredicate DEFAULT_SHOULD_REDIRECT_PREDICATE =
            (relative, redirectCnt, previousRequest, redirectResponse) -> true;
    private static final CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = {};
    private static final RedirectRequestTransformer DEFAULT_REQUEST_TRANSFORMER =
            (relative, previousRequest, redirectResponse, redirectRequest) -> redirectRequest;

    private final boolean allowNonRelativeRedirects;
    private final Config config;

    /**
     * Create a new instance, only performing relative redirects.
     *
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter() {
        this(DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance, only performing relative redirects.
     *
     * @param maxRedirects The maximum number of follow up redirects.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final int maxRedirects) {
        this(true, maxRedirects);
    }

    /**
     * Create a new instance, performing relative redirects only for {@link HttpConnection}.
     *
     * @param onlyRelativeClient Limits the redirects to relative paths for {@link HttpClient} filters.
     * @deprecated Use {@link Builder}.
     */
    @Deprecated
    public RedirectingHttpRequesterFilter(final boolean onlyRelativeClient) {
        this(onlyRelativeClient, DEFAULT_MAX_REDIRECTS);
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
        this(!onlyRelativeClient, new Config(maxRedirects, toSortedNames(DEFAULT_ALLOWED_METHODS),
                DEFAULT_SHOULD_REDIRECT_PREDICATE,
                /* changePostToGet */ true,  // use "true" for backward compatibility
                DEFAULT_REQUEST_TRANSFORMER));
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
        this(onlyRelativeClient, DEFAULT_MAX_REDIRECTS);
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
        this(onlyRelativeClient, maxRedirects);
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
        private ShouldRedirectPredicate shouldRedirect = DEFAULT_SHOULD_REDIRECT_PREDICATE;
        private boolean changePostToGet;
        private CharSequence[] headersToRedirect = EMPTY_CHAR_SEQUENCE_ARRAY;
        private boolean redirectPayloadBody;
        private CharSequence[] trailersToRedirect = EMPTY_CHAR_SEQUENCE_ARRAY;
        private RedirectRequestTransformer requestTransformer = DEFAULT_REQUEST_TRANSFORMER;

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
        public Builder shouldRedirect(final ShouldRedirectPredicate shouldRedirect) {
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
        public Builder prepareRequest(final RedirectRequestTransformer requestTransformer) {
            this.requestTransformer = requireNonNull(requestTransformer);
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
                    new DefaultRedirectRequestTransformer(headersToRedirect, redirectPayloadBody, trailersToRedirect,
                            requestTransformer)));
        }
    }

    private static String[] toSortedNames(final HttpRequestMethod[] allowedMethods) {
        return Arrays.stream(allowedMethods).map(HttpRequestMethod::name)
                .sorted()   // Soring is required because RedirectSingle uses Arrays.binarySearch
                .toArray(String[]::new);
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
            this.headersToRedirect = headersToRedirect.clone();
            this.redirectPayloadBody = redirectPayloadBody;
            this.trailersToRedirect = trailersToRedirect.clone();
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
