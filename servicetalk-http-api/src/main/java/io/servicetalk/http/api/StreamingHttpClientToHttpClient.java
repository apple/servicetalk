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
import io.servicetalk.http.api.FilterableStreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static io.servicetalk.http.api.RequestResponseFactories.toAggregated;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToHttpClient implements HttpClient {
    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final HttpRequestResponseFactory reqRespFactory;

    StreamingHttpClientToHttpClient(final StreamingHttpClient client) {
        strategy = client.computeExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY);
        this.client = client;
        reqRespFactory = toAggregated(client);
    }

    @Override
    public Single<HttpResponse> request(final HttpRequest request) {
        return request(strategy, request);
    }

    @Override
    public Single<ReservedHttpConnection> reserveConnection(final HttpRequestMetaData metaData) {
        return reserveConnection(strategy, metaData);
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
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
        // TODO(scott): should we include the API strategy?
        return client.computeExecutionStrategy(other);
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
    public HttpResponse newResponse(final HttpResponseStatus status) {
        return reqRespFactory.newResponse(status);
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    static final class ReservedStreamingHttpConnectionToReservedHttpConnection implements ReservedHttpConnection {
        private final ReservedStreamingHttpConnection connection;
        private final HttpExecutionStrategy strategy;
        private final HttpRequestResponseFactory reqRespFactory;

        ReservedStreamingHttpConnectionToReservedHttpConnection(ReservedStreamingHttpConnection connection) {
            this(connection, connection.computeExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY),
                    toAggregated(connection));
        }

        ReservedStreamingHttpConnectionToReservedHttpConnection(ReservedStreamingHttpConnection connection,
                                                                HttpExecutionStrategy strategy,
                                                                HttpRequestResponseFactory reqRespFactory) {
            this.strategy = strategy;
            this.connection = requireNonNull(connection);
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
            return request(strategy, request);
        }

        @Override
        public ConnectionContext connectionContext() {
            return connection.connectionContext();
        }

        @Override
        public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
            return connection.settingStream(settingKey);
        }

        @Override
        public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
            return connection.request(strategy, request.toStreamingRequest())
                    .flatMap(StreamingHttpResponse::toResponse);
        }

        @Override
        public ExecutionContext executionContext() {
            return connection.executionContext();
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
        public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
            // TODO(scott): should we include the API strategy?
            return connection.computeExecutionStrategy(other);
        }

        @Override
        public HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }

        @Override
        public HttpResponse newResponse(final HttpResponseStatus status) {
            return reqRespFactory.newResponse(status);
        }
    }
}
