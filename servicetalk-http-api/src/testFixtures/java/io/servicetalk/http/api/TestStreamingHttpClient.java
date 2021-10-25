/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingClient;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingClient;
import static io.servicetalk.http.api.HttpApiConversions.toClient;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.anyStrategy;

public final class TestStreamingHttpClient {

    private TestStreamingHttpClient() {
        // no instances
    }

    public static StreamingHttpClient from(
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final HttpExecutionContext executionContext,
            final StreamingHttpClientFilterFactory factory) {
        final StreamingHttpClientFilter filterChain = factory
                .create(new FilterableStreamingHttpClient() {
                    private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

                    @Override
                    public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                            final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                        return failed(new UnsupportedOperationException());
                    }

                    @Override
                    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                 final StreamingHttpRequest request) {
                        return failed(new UnsupportedOperationException());
                    }

                    @Override
                    public HttpExecutionContext executionContext() {
                        return executionContext;
                    }

                    @Override
                    public StreamingHttpResponseFactory httpResponseFactory() {
                        return reqRespFactory;
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
                    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                        return reqRespFactory.newRequest(method, requestTarget);
                    }
                });
        return from(filterChain);
    }

    public static StreamingHttpClient from(FilterableStreamingHttpClient filterChain) {
        return new StreamingHttpClient() {
            @Override
            public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                             final HttpRequestMetaData metaData) {
                return filterChain.reserveConnection(strategy, metaData)
                        .map(rc -> new ReservedStreamingHttpConnection() {
                            @Override
                            public ReservedHttpConnection asConnection() {
                                return toReservedConnection(this, anyStrategy());
                            }

                            @Override
                            public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
                                return toReservedBlockingStreamingConnection(this, anyStrategy());
                            }

                            @Override
                            public ReservedBlockingHttpConnection asBlockingConnection() {
                                return toReservedBlockingConnection(this, anyStrategy());
                            }

                            @Override
                            public Completable releaseAsync() {
                                return rc.releaseAsync();
                            }

                            @Override
                            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                return rc.request(strategy, request);
                            }

                            @Override
                            public HttpConnectionContext connectionContext() {
                                return rc.connectionContext();
                            }

                            @Override
                            public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> settingKey) {
                                return rc.transportEventStream(settingKey);
                            }

                            @Override
                            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                                         final StreamingHttpRequest request) {
                                return rc.request(strategy, request);
                            }

                            @Override
                            public HttpExecutionContext executionContext() {
                                return rc.executionContext();
                            }

                            @Override
                            public StreamingHttpResponseFactory httpResponseFactory() {
                                return rc.httpResponseFactory();
                            }

                            @Override
                            public Completable onClose() {
                                return rc.onClose();
                            }

                            @Override
                            public Completable closeAsync() {
                                return rc.closeAsync();
                            }

                            @Override
                            public Completable closeAsyncGracefully() {
                                return rc.closeAsyncGracefully();
                            }

                            @Override
                            public StreamingHttpRequest newRequest(final HttpRequestMethod method,
                                                                   final String requestTarget) {
                                return rc.newRequest(method, requestTarget);
                            }
                        });
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
            public HttpExecutionContext executionContext() {
                return filterChain.executionContext();
            }

            @Override
            public StreamingHttpResponseFactory httpResponseFactory() {
                return filterChain.httpResponseFactory();
            }

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return filterChain.request(defaultStrategy(), request);
            }

            @Override
            public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
                return reserveConnection(defaultStrategy(), metaData);
            }

            @Override
            public HttpClient asClient() {
                return toClient(this, anyStrategy());
            }

            @Override
            public BlockingStreamingHttpClient asBlockingStreamingClient() {
                return toBlockingStreamingClient(this, anyStrategy());
            }

            @Override
            public BlockingHttpClient asBlockingClient() {
                return toBlockingClient(this, anyStrategy());
            }
        };
    }
}
