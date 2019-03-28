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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

public final class TestStreamingHttpClient {

    private TestStreamingHttpClient() {
        // no instances
    }

    public static StreamingHttpClient from(
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final ExecutionContext executionContext,
            final HttpClientFilterFactory factory) {
        final StreamingHttpClientFilter filterChain = factory
                .create(new FilterableStreamingHttpClient() {
                    private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

                    @Override
                    public Single<ReservedStreamingHttpConnection> reserveConnection(
                            final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                        return failed(new UnsupportedOperationException());
                    }

                    @Override
                    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                 final StreamingHttpRequest request) {
                        return failed(new UnsupportedOperationException());
                    }

                    @Override
                    public ExecutionContext executionContext() {
                        return executionContext;
                    }

                    @Override
                    public Completable closeAsync() {
                        return closeable.closeAsync();
                    }

                    @Override
                    public Completable closeAsyncGracefully() {
                        return closeable.closeAsyncGracefully();
                    }

                    @Override
                    public Completable onClose() {
                        return closeable.onClose();
                    }

                    @Override
                    public StreamingHttpResponse newResponse(final HttpResponseStatus status) {
                        return reqRespFactory.newResponse(status);
                    }

                    @Override
                    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                        return reqRespFactory.newRequest(method, requestTarget);
                    }
                }, Publisher.empty());
        return from(filterChain);
    }

    public static StreamingHttpClient from(FilterableStreamingHttpClient filterChain) {
        return new StreamingHttpClient() {
            private final HttpExecutionStrategy strategy = filterChain.computeExecutionStrategy(defaultStrategy());

            @Override
            public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                             final HttpRequestMetaData metaData) {
                return filterChain.reserveConnection(strategy, metaData);
            }

            @Override
            public StreamingHttpResponse newResponse(final HttpResponseStatus status) {
                return filterChain.newResponse(status);
            }

            @Override
            public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                return filterChain.newRequest(method, requestTarget);
            }

            @Override
            public Completable closeAsync() {
                return filterChain.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return filterChain.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return filterChain.onClose();
            }

            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return filterChain.request(strategy, request);
            }

            @Override
            public ExecutionContext executionContext() {
                return filterChain.executionContext();
            }

            @Override
            public void close() throws Exception {
                filterChain.close();
            }

            @Override
            public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
                return filterChain.computeExecutionStrategy(other);
            }

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return filterChain.request(strategy, request);
            }

            @Override
            public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
                return filterChain.reserveConnection(strategy, metaData);
            }
        };
    }
}
