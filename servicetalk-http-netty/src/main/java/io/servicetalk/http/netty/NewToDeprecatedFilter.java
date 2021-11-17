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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;

/**
 * Because users can have a mixed set of filters, we should always delegate from the new {@code request} method to the
 * deprecated one to make sure filters that were not migrated still work.
 *
 * @deprecated Temporarily class, should be removed after deprecated
 * {@link StreamingHttpRequester#request(HttpExecutionStrategy, StreamingHttpRequest)} is removed.
 */
@Deprecated
final class NewToDeprecatedFilter implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    static final NewToDeprecatedFilter NEW_TO_DEPRECATED_FILTER = new NewToDeprecatedFilter();

    private NewToDeprecatedFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return Single.defer(() -> delegate.request(
                        requestStrategy(request, delegate.executionContext().executionStrategy()), request)
                        .subscribeShareContext());
            }

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return delegate.request(strategy, request);
            }

            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpRequestMetaData metaData) {
                return Single.defer(() -> delegate().reserveConnection(
                        requestStrategy(metaData, delegate().executionContext().executionStrategy()), metaData)
                        .map(conn -> new ReservedStreamingHttpConnectionFilter(conn) {
                            @Override
                            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                return Single.defer(() -> delegate().request(
                                        requestStrategy(request, delegate().executionContext().executionStrategy()),
                                                request)
                                        .subscribeShareContext());
                            }
                        })
                        .subscribeShareContext());
            }

            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                return delegate().reserveConnection(strategy, metaData);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return Single.defer(() -> delegate().request(
                        requestStrategy(request, delegate().executionContext().executionStrategy()), request)
                        .subscribeShareContext());
            }

            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return delegate().request(strategy, request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.anyStrategy();
    }

    static HttpExecutionStrategy requestStrategy(HttpRequestMetaData metaData, HttpExecutionStrategy fallback) {
        final HttpExecutionStrategy strategy = metaData.context().get(HTTP_EXECUTION_STRATEGY_KEY);
        return strategy != null ? strategy : fallback;
    }
}
