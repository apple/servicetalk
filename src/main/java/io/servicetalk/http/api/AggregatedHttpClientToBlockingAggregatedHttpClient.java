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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static java.util.Objects.requireNonNull;

final class AggregatedHttpClientToBlockingAggregatedHttpClient extends BlockingAggregatedHttpClient {
    private final AggregatedHttpClient client;

    AggregatedHttpClientToBlockingAggregatedHttpClient(AggregatedHttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingAggregatedReservedHttpConnection reserveConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception {
        return new AggregatedReservedHttpConnectionToBlockingAggregated(
                awaitIndefinitelyNonNull(client.reserveConnection(request)));
    }

    @Override
    public AggregatedUpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return awaitIndefinitelyNonNull(client.upgradeConnection(request));
    }

    @Override
    public AggregatedHttpResponse<HttpPayloadChunk> request(
            final AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public void close() throws Exception {
        BlockingUtils.close(client);
    }

    @Override
    AggregatedHttpClient asAggregatedClientInternal() {
        return client;
    }

    Completable onClose() {
        return client.onClose();
    }

    static final class AggregatedReservedHttpConnectionToBlockingAggregated
            extends BlockingAggregatedReservedHttpConnection {
        private final AggregatedReservedHttpConnection connection;

        AggregatedReservedHttpConnectionToBlockingAggregated(AggregatedReservedHttpConnection connection) {
            this.connection = requireNonNull(connection);
        }

        @Override
        public void release() throws Exception {
            // It is assumed that users will always apply timeouts at the HttpConnection layer (e.g. via filter).
            // So we don't apply any explicit timeout here and just wait forever.
            awaitIndefinitely(connection.releaseAsync());
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final HttpConnection.SettingKey<T> settingKey) {
            return connection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public AggregatedHttpResponse<HttpPayloadChunk> request(
                final AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public void close() throws Exception {
            BlockingUtils.close(connection);
        }

        Completable onClose() {
            return connection.onClose();
        }

        @Override
        AggregatedReservedHttpConnection asAggregatedConnectionInternal() {
            return connection;
        }
    }
}
