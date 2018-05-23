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
import io.servicetalk.http.api.AggregatedHttpClientToBlockingAggregatedHttpClient.AggregatedReservedHttpConnectionToBlockingAggregated;
import io.servicetalk.http.api.BlockingAggregatedHttpClient.BlockingAggregatedReservedHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingAggregatedHttpClientToAggregatedHttpClient extends AggregatedHttpClient {
    private final BlockingAggregatedHttpClient client;

    BlockingAggregatedHttpClientToAggregatedHttpClient(BlockingAggregatedHttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends AggregatedReservedHttpConnection> reserveConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> new BlockingAggregatedReservedToAggregatedHttpConnection(
                client.reserveConnection(request)));
    }

    @Override
    public Single<? extends AggregatedUpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> client.upgradeConnection(request));
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(
            final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        if (client instanceof AggregatedHttpClientToBlockingAggregatedHttpClient) {
            return ((AggregatedHttpClientToBlockingAggregatedHttpClient) client).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + client.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(client::close);
    }

    @Override
    BlockingAggregatedHttpClient asBlockingAggregatedClientInternal() {
        return client;
    }

    static final class BlockingAggregatedReservedToAggregatedHttpConnection extends AggregatedReservedHttpConnection {
        private final BlockingAggregatedReservedHttpConnection connection;

        BlockingAggregatedReservedToAggregatedHttpConnection(BlockingAggregatedReservedHttpConnection connection) {
            this.connection = requireNonNull(connection);
        }

        @Override
        public Completable releaseAsync() {
            return blockingToCompletable(connection::release);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final HttpConnection.SettingKey<T> settingKey) {
            return from(connection.getSettingIterable(settingKey));
        }

        @Override
        public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(
                final AggregatedHttpRequest<HttpPayloadChunk> request) {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            if (connection instanceof AggregatedReservedHttpConnectionToBlockingAggregated) {
                return ((AggregatedReservedHttpConnectionToBlockingAggregated) connection).onClose();
            }

            return error(new UnsupportedOperationException("unsupported type: " + connection.getClass()));
        }

        @Override
        public Completable closeAsync() {
            return blockingToCompletable(connection::close);
        }

        @Override
        BlockingAggregatedReservedHttpConnection asBlockingAggregatedConnectionInternal() {
            return connection;
        }
    }
}
