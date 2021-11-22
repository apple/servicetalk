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
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
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
                                                             StreamingHttpConnectionFilterFactory {

    private static final RedirectConfig DEFAULT_CONFIG = new RedirectConfigBuilder().build();

    private final RedirectConfig config;

    /**
     * Create a new instance, only performing relative redirects.
     */
    public RedirectingHttpRequesterFilter() {
        this(DEFAULT_CONFIG);
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
                                                            final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate, request,
                        config.allowNonRelativeRedirects());
            }

            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpRequestMetaData metaData) {
                return delegate().reserveConnection(metaData)
                        .map(r -> new ReservedStreamingHttpConnectionFilter(r) {
                            @Override
                            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                return RedirectingHttpRequesterFilter.this.request(delegate(), request, false);
                            }
                        });
            }
       };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return RedirectingHttpRequesterFilter.this.request(delegate(), request, false);
            }
        };
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final StreamingHttpRequest request,
                                                  final boolean allowNonRelativeRedirects) {
        final Single<StreamingHttpResponse> response = delegate.request(
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
        return new RedirectSingle(delegate, request, response, allowNonRelativeRedirects, config);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.anyStrategy();
    }
}
