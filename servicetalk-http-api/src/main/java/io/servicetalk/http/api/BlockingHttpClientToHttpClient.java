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
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.HttpClientToBlockingHttpClient.ReservedHttpConnectionToReservedBlockingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientToHttpClient extends HttpClient {
    private final BlockingHttpClient client;

    private BlockingHttpClientToHttpClient(final BlockingHttpClient client, final HttpExecutionStrategy strategy) {
        super(client.reqRespFactory, strategy);
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                      final HttpRequestMetaData metaData) {
        return blockingToSingle(() -> new ReservedBlockingHttpConnectionToReservedHttpConnection(
                client.reserveConnection(strategy, metaData), executionStrategy()));
    }

    @Override
    public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
        return BlockingUtils.request(client, strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public Completable onClose() {
        if (client instanceof HttpClientToBlockingHttpClient) {
            return ((HttpClientToBlockingHttpClient) client).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + client.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(client::close);
    }

    @Override
    BlockingHttpClient asBlockingClientInternal() {
        return client;
    }

    static HttpClient transform(BlockingHttpClient client) {
        // Any client created for alternate programming models always originates from the async streaming model
        // which contains the filters and hence the effective strategy while converting them to the different
        // programming models. So, in this case we simply take the executionStrategy() from the passed client instead
        // of re-calculating the effective strategy.
        return new BlockingHttpClientToHttpClient(client, client.executionStrategy()
                .merge(OFFLOAD_RECEIVE_META_STRATEGY));
    }

    static final class ReservedBlockingHttpConnectionToReservedHttpConnection extends ReservedHttpConnection {
        private final ReservedBlockingHttpConnection connection;

        private ReservedBlockingHttpConnectionToReservedHttpConnection(final ReservedBlockingHttpConnection connection,
                                                                       final HttpExecutionStrategy strategy) {
            super(connection.reqRespFactory, strategy);
            this.connection = requireNonNull(connection);
        }

        @Override
        public Completable releaseAsync() {
            return blockingToCompletable(connection::release);
        }

        @Override
        public ConnectionContext connectionContext() {
            return connection.connectionContext();
        }

        @Override
        public <T> Publisher<T> settingStream(final StreamingHttpConnection.SettingKey<T> settingKey) {
            return from(connection.settingIterable(settingKey));
        }

        @Override
        public Single<HttpResponse> request(final HttpExecutionStrategy strategy, final HttpRequest request) {
            return blockingToSingle(() -> connection.request(strategy, request));
        }

        @Override
        public ExecutionContext executionContext() {
            return connection.executionContext();
        }

        @Override
        public Completable onClose() {
            if (connection instanceof ReservedHttpConnectionToReservedBlockingHttpConnection) {
                return ((ReservedHttpConnectionToReservedBlockingHttpConnection) connection).onClose();
            }

            return error(new UnsupportedOperationException("unsupported type: " + connection.getClass()));
        }

        @Override
        public Completable closeAsync() {
            return blockingToCompletable(connection::close);
        }

        @Override
        ReservedBlockingHttpConnection asBlockingConnectionInternal() {
            return connection;
        }

        static ReservedHttpConnection transform(ReservedBlockingHttpConnection connection) {
            // Any connection created for alternate programming models always originates from the async streaming model
            // which contains the filters and hence the effective strategy while converting them to the different
            // programming models. So, in this case we simply take the executionStrategy() from the passed connection
            // instead of re-calculating the effective strategy.
            return new ReservedBlockingHttpConnectionToReservedHttpConnection(connection,
                    connection.executionStrategy().merge(OFFLOAD_RECEIVE_META_STRATEGY));
        }
    }
}
