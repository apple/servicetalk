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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.http.api.HttpApiConversions.toBlockingClient;
import static io.servicetalk.http.api.HttpApiConversions.toBlockingStreamingClient;
import static io.servicetalk.http.api.HttpApiConversions.toClient;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_EXECUTION_STRATEGY_KEY;

final class FilterableClientToClient implements StreamingHttpClient {
    private final FilterableStreamingHttpClient client;
    private final DelegatingHttpExecutionContext executionContext;

    FilterableClientToClient(FilterableStreamingHttpClient filteredClient, HttpExecutionStrategy strategy) {
        client = filteredClient;
        this.executionContext = new DelegatingHttpExecutionContext(client.executionContext()) {
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return strategy;
            }
        };
    }

    @Override
    public HttpClient asClient() {
        return toClient(this, executionContext.executionStrategy());
    }

    @Override
    public BlockingStreamingHttpClient asBlockingStreamingClient() {
        return toBlockingStreamingClient(this, executionContext.executionStrategy());
    }

    @Override
    public BlockingHttpClient asBlockingClient() {
        return toBlockingClient(this, executionContext.executionStrategy());
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return Single.defer(() -> {
            request.context().putIfAbsent(HTTP_EXECUTION_STRATEGY_KEY, executionContext.executionStrategy());
            // HttpExecutionStrategy currentStrategy = request.context().getOrDefault(
            //         HTTP_EXECUTION_STRATEGY_KEY, defaultStrategy());
            // if (defaultStrategy() == currentStrategy) {
            //     HttpApiConversions.ClientAPI clientApi = request.context().getOrDefault(
            //             HTTP_CLIENT_API_KEY, HttpApiConversions.ClientAPI.ASYNC_STREAMING);
            //     request.context().put(HTTP_EXECUTION_STRATEGY_KEY, clientApi.defaultStrategy().merge(strategy));
            // } else {
            //     request.context().put(HTTP_EXECUTION_STRATEGY_KEY, strategy);
            // }
            return client.request(request).shareContextOnSubscribe();
        });
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
        return Single.defer(() -> {
            metaData.context().putIfAbsent(HTTP_EXECUTION_STRATEGY_KEY, executionContext.executionStrategy());
            return client.reserveConnection(metaData).map(rc -> new ReservedStreamingHttpConnection() {
                @Override
                public ReservedHttpConnection asConnection() {
                    return toReservedConnection(this, executionContext.executionStrategy());
                }

                @Override
                public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
                    return toReservedBlockingStreamingConnection(this, executionContext.executionStrategy());
                }

                @Override
                public ReservedBlockingHttpConnection asBlockingConnection() {
                    return toReservedBlockingConnection(this, executionContext.executionStrategy());
                }

                @Override
                public Completable releaseAsync() {
                    return rc.releaseAsync();
                }

                @Override
                public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                    // Use the strategy from the client as the underlying ReservedStreamingHttpConnection may be user
                    // created and hence could have an incorrect default strategy. Doing this makes sure we never call
                    // the method without strategy just as we do for the regular connection.
                    return Single.defer(() -> {
                        request.context().putIfAbsent(HTTP_EXECUTION_STRATEGY_KEY,
                                FilterableClientToClient.this.executionContext.executionStrategy());
                        return rc.request(request).shareContextOnSubscribe();
                    });
                }

                @Override
                public HttpConnectionContext connectionContext() {
                    return rc.connectionContext();
                }

                @Override
                public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
                    return rc.transportEventStream(eventKey);
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
                public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                    return rc.newRequest(method, requestTarget);
                }
            }).shareContextOnSubscribe();
        });
    }

    @Override
    public HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public StreamingHttpResponseFactory httpResponseFactory() {
        return client.httpResponseFactory();
    }

    @Override
    public Completable onClose() {
        return client.onClose();
    }

    @Override
    public Completable closeAsync() {
        return client.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return client.closeAsyncGracefully();
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return client.newRequest(method, requestTarget);
    }
}
