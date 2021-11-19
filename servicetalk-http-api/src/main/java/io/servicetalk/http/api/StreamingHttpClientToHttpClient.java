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

import static io.servicetalk.http.api.HttpApiConversions.requestStrategy;
import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;
import static io.servicetalk.http.api.StreamingHttpConnectionToHttpConnection.DEFAULT_CONNECTION_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToHttpClient implements HttpClient {
    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final HttpExecutionContext context;
    private final HttpRequestResponseFactory reqRespFactory;

    StreamingHttpClientToHttpClient(final StreamingHttpClient client, final HttpExecutionStrategy strategy) {
        this.strategy = DEFAULT_CONNECTION_STRATEGY.merge(strategy);
        this.client = client;
        context = new DelegatingHttpExecutionContext(client.executionContext()) {
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return StreamingHttpClientToHttpClient.this.strategy;
            }
        };
        reqRespFactory = toAggregated(client);
    }

    @Override
    public Single<HttpResponse> request(final HttpRequest request) {
        return Single.defer(() -> request(requestStrategy(request, strategy), request).subscribeShareContext());
    }

    @Override
    public Single<ReservedHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
        return Single.defer(() -> reserveConnection(requestStrategy(metaData, strategy), metaData)
                .subscribeShareContext());
    }

    @Override
    public Single<ReservedHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                            final HttpRequestMetaData metaData) {
        return client.reserveConnection(strategy, metaData)
                .map(c -> new ReservedStreamingHttpConnectionToReservedHttpConnection(c, this.strategy,
                        reqRespFactory));
    }

    @Override
    public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
        return client.request(strategy, request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
    }

    @Override
    public HttpExecutionContext executionContext() {
        return context;
    }

    @Override
    public HttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public void closeGracefully() throws Exception {
        client.closeGracefully();
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
    public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    static final class ReservedStreamingHttpConnectionToReservedHttpConnection implements ReservedHttpConnection {
        private final ReservedStreamingHttpConnection connection;
        private final HttpExecutionStrategy strategy;
        private final HttpConnectionContext context;
        private final HttpExecutionContext executionContext;
        private final HttpRequestResponseFactory reqRespFactory;

        ReservedStreamingHttpConnectionToReservedHttpConnection(final ReservedStreamingHttpConnection connection,
                                                                final HttpExecutionStrategy strategy) {
            this(connection, DEFAULT_CONNECTION_STRATEGY.merge(strategy), toAggregated(connection));
        }

        ReservedStreamingHttpConnectionToReservedHttpConnection(final ReservedStreamingHttpConnection connection,
                                                                final HttpExecutionStrategy strategy,
                                                                final HttpRequestResponseFactory reqRespFactory) {
            this.strategy = strategy;
            this.connection = requireNonNull(connection);
            executionContext = new DelegatingHttpExecutionContext(connection.executionContext()) {
                @Override
                public HttpExecutionStrategy executionStrategy() {
                    return ReservedStreamingHttpConnectionToReservedHttpConnection.this.strategy;
                }
            };
            final HttpConnectionContext originalCtx = connection.connectionContext();
            context = new DelegatingHttpConnectionContext(originalCtx) {
                @Override
                public HttpExecutionContext executionContext() {
                    return executionContext;
                }
            };
            this.reqRespFactory = requireNonNull(reqRespFactory);
        }

        @Override
        public Completable releaseAsync() {
            return connection.releaseAsync();
        }

        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
        }

        @Override
        public Single<HttpResponse> request(final HttpRequest request) {
            return Single.defer(() -> request(requestStrategy(request, strategy), request).subscribeShareContext());
        }

        @Override
        public HttpConnectionContext connectionContext() {
            return context;
        }

        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return connection.transportEventStream(eventKey);
        }

        @Override
        public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
            return connection.request(strategy, request.toStreamingRequest())
                    .flatMap(StreamingHttpResponse::toResponse);
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public HttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public Completable onClose() {
            return connection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return connection.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return connection.closeAsyncGracefully();
        }

        @Override
        public void close() throws Exception {
            connection.close();
        }

        @Override
        public void closeGracefully() throws Exception {
            connection.closeGracefully();
        }

        @Override
        public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }
    }
}
