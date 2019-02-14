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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToHttpClient extends HttpClient {
    private final StreamingHttpClient client;

    private StreamingHttpClientToHttpClient(final StreamingHttpClient client, final HttpExecutionStrategy strategy) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(client.reqRespFactory), strategy);
        this.client = requireNonNull(client);
    }

    @Override
    public Single<ReservedHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                            final HttpRequestMetaData metaData) {
        return client.reserveConnection(strategy, metaData)
                .map(c -> new ReservedStreamingHttpConnectionToReservedHttpConnection(c, executionStrategy()));
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
    StreamingHttpClient asStreamingClientInternal() {
        return client;
    }

    static HttpClient transform(StreamingHttpClient client) {
        final HttpExecutionStrategy defaultStrategy = client instanceof StreamingHttpClientFilter ?
                ((StreamingHttpClientFilter) client).effectiveExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY) :
                OFFLOAD_RECEIVE_META_STRATEGY;
        return new StreamingHttpClientToHttpClient(client, defaultStrategy);
    }

    static final class ReservedStreamingHttpConnectionToReservedHttpConnection extends ReservedHttpConnection {
        private final ReservedStreamingHttpConnection reservedConnection;

        private ReservedStreamingHttpConnectionToReservedHttpConnection(
                ReservedStreamingHttpConnection reservedConnection, HttpExecutionStrategy strategy) {
            super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(
                    reservedConnection.reqRespFactory), strategy);
            this.reservedConnection = requireNonNull(reservedConnection);
        }

        @Override
        public Completable releaseAsync() {
            return reservedConnection.releaseAsync();
        }

        @Override
        public ConnectionContext connectionContext() {
            return reservedConnection.connectionContext();
        }

        @Override
        public <T> Publisher<T> settingStream(final StreamingHttpConnection.SettingKey<T> settingKey) {
            return reservedConnection.settingStream(settingKey);
        }

        @Override
        public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
            return reservedConnection.request(strategy, request.toStreamingRequest())
                    .flatMap(StreamingHttpResponse::toResponse);
        }

        @Override
        public ExecutionContext executionContext() {
            return reservedConnection.executionContext();
        }

        @Override
        public Completable onClose() {
            return reservedConnection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return reservedConnection.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return reservedConnection.closeAsyncGracefully();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return reservedConnection;
        }

        static ReservedHttpConnection transform(ReservedStreamingHttpConnection conn) {
            final HttpExecutionStrategy defaultStrategy = conn instanceof ReservedStreamingHttpConnectionFilter ?
                    ((ReservedStreamingHttpConnectionFilter) conn)
                            .effectiveExecutionStrategy(OFFLOAD_RECEIVE_META_STRATEGY) :
                    OFFLOAD_RECEIVE_META_STRATEGY;
            return new ReservedStreamingHttpConnectionToReservedHttpConnection(conn, defaultStrategy);
        }
    }
}
