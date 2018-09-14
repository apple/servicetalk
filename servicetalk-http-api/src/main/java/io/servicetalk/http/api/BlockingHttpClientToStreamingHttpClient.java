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
import io.servicetalk.http.api.StreamingHttpClientToBlockingHttpClient.ReservedStreamingHttpConnectionToBlocking;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientToStreamingHttpClient extends StreamingHttpClient {
    private final BlockingHttpClient client;

    BlockingHttpClientToStreamingHttpClient(BlockingHttpClient client) {
        super(new HttpRequestFactoryToStreamingHttpRequestFactory(client));
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest request) {
        return request.toRequest().flatMap(req -> blockingToSingle(() ->
                new BlockingReservedStreamingHttpConnectionToReserved(client.reserveConnection(req))));
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(final StreamingHttpRequest request) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> client.upgradeConnection(req))
                .map(HttpClient.UpgradableHttpResponse::toStreamingResponse));
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        if (client instanceof StreamingHttpClientToBlockingHttpClient) {
            return ((StreamingHttpClientToBlockingHttpClient) client).onClose();
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

    static final class BlockingReservedStreamingHttpConnectionToReserved extends ReservedStreamingHttpConnection {
        private final ReservedBlockingHttpConnection connection;

        BlockingReservedStreamingHttpConnectionToReserved(ReservedBlockingHttpConnection connection) {
            super(new HttpRequestFactoryToStreamingHttpRequestFactory(connection));
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
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            if (connection instanceof ReservedStreamingHttpConnectionToBlocking) {
                return ((ReservedStreamingHttpConnectionToBlocking) connection).onClose();
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
    }
}
