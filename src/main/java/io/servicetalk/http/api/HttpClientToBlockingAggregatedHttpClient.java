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
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.HttpClientToAggregatedHttpClient.doUpgradeConnection;
import static java.util.Objects.requireNonNull;

final class HttpClientToBlockingAggregatedHttpClient extends BlockingAggregatedHttpClient {
    private final HttpClient client;

    HttpClientToBlockingAggregatedHttpClient(HttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingAggregatedReservedHttpConnection reserveConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception {
        return blockingInvocation(client.reserveConnection(toHttpRequest(request))
                .map(ReservedHttpConnectionToBlockingAggregated::new));
    }

    @Override
    public AggregatedUpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception {
        return blockingInvocation(doUpgradeConnection(client, request));
    }

    @Override
    public AggregatedHttpResponse<HttpPayloadChunk> request(final AggregatedHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    @Override
    HttpClient asClientInternal() {
        return client;
    }

    Completable onClose() {
        return client.onClose();
    }

    static final class ReservedHttpConnectionToBlockingAggregated extends BlockingAggregatedReservedHttpConnection {
        private final ReservedHttpConnection connection;

        ReservedHttpConnectionToBlockingAggregated(ReservedHttpConnection connection) {
            this.connection = requireNonNull(connection);
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final SettingKey<T> settingKey) {
            return connection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public AggregatedHttpResponse<HttpPayloadChunk> request(final AggregatedHttpRequest<HttpPayloadChunk> request)
                throws Exception {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public void close() throws Exception {
            blockingInvocation(connection.closeAsync());
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return connection;
        }

        Completable onClose() {
            return connection.onClose();
        }
    }
}
