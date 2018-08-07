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
import io.servicetalk.http.api.BlockingAggregatedHttpClient.BlockingAggregatedReservedHttpConnection;
import io.servicetalk.http.api.HttpClientToBlockingAggregatedHttpClient.ReservedHttpConnectionToBlockingAggregated;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.AggregatedHttpClientToHttpClient.AggregatedToUpgradableHttpResponse.newUpgradeResponse;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.from;
import static java.util.Objects.requireNonNull;

final class BlockingAggregatedHttpClientToHttpClient extends HttpClient {
    private final BlockingAggregatedHttpClient client;

    BlockingAggregatedHttpClientToHttpClient(BlockingAggregatedHttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
        return from(request, client.getExecutionContext().getBufferAllocator()).flatMap(aggregatedRequest ->
                blockingToSingle(() -> new BlockingAggregatedReservedHttpConnectionToReserved(
                        client.reserveConnection(aggregatedRequest))));
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return from(request, client.getExecutionContext().getBufferAllocator()).flatMap(aggregatedRequest ->
                blockingToSingle(() -> newUpgradeResponse(client.upgradeConnection(aggregatedRequest))));
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        if (client instanceof HttpClientToBlockingAggregatedHttpClient) {
            return ((HttpClientToBlockingAggregatedHttpClient) client).onClose();
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

    static final class BlockingAggregatedReservedHttpConnectionToReserved extends ReservedHttpConnection {
        private final BlockingAggregatedReservedHttpConnection connection;

        BlockingAggregatedReservedHttpConnectionToReserved(BlockingAggregatedReservedHttpConnection connection) {
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
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return from(connection.getSettingIterable(settingKey));
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            if (connection instanceof ReservedHttpConnectionToBlockingAggregated) {
                return ((ReservedHttpConnectionToBlockingAggregated) connection).onClose();
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
