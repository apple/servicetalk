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
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientToHttpClient extends HttpClient {
    private final BlockingHttpClient client;

    BlockingHttpClientToHttpClient(BlockingHttpClient client) {
        super(client.reqRespFactory);
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                      final HttpRequest request) {
        return blockingToSingle(() -> new ReservedBlockingHttpConnectionToReservedHttpConnection(
                client.reserveConnection(strategy, request)));
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

    static final class ReservedBlockingHttpConnectionToReservedHttpConnection extends ReservedHttpConnection {
        private final ReservedBlockingHttpConnection connection;

        ReservedBlockingHttpConnectionToReservedHttpConnection(ReservedBlockingHttpConnection connection) {
            super(connection.reqRespFactory);
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
    }
}
