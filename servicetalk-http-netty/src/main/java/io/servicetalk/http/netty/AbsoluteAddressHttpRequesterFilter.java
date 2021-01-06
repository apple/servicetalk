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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.http.utils.HttpRequestUriUtils.getEffectiveRequestUri;
import static java.util.Objects.requireNonNull;

/**
 * A filter that rewrites non-absolute request targets to include the host and port the {@link HttpClient} was built
 * for.
 */
final class AbsoluteAddressHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                          StreamingHttpConnectionFilterFactory,
                                                          HttpExecutionStrategyInfluencer {
    private final String scheme;
    private final String authority;

    /**
     * Create a new instance.
     *
     * @param scheme The scheme of the client.
     * @param authority The authority (host:port) of the client.
     */
    AbsoluteAddressHttpRequesterFilter(String scheme, CharSequence authority) {
        this.scheme = requireNonNull(scheme);
        this.authority = authority.toString();
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return AbsoluteAddressHttpRequesterFilter.this.request(delegate, strategy, request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return AbsoluteAddressHttpRequesterFilter.this.request(delegate(), strategy, request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request) {
        return defer(() -> {
            final String effectiveRequestUri = getEffectiveRequestUri(request, scheme, authority, false);
            request.requestTarget(effectiveRequestUri);
            return delegate.request(strategy, request).subscribeShareContext();
        });
    }
}
