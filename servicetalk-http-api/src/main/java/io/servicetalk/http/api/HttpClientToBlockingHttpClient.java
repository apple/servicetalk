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
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static java.util.Objects.requireNonNull;

final class HttpClientToBlockingHttpClient extends BlockingHttpClient {
    private final HttpClient client;

    HttpClientToBlockingHttpClient(HttpClient client) {
        super(client.reqRespFactory);
        this.client = requireNonNull(client);
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final HttpRequest request) throws Exception {
        return new ReservedHttpConnectionToReservedBlockingHttpConnection(
                blockingInvocation(client.reserveConnection(request)));
    }

    @Override
    public HttpClient.UpgradableHttpResponse upgradeConnection(final HttpRequest request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return blockingInvocation(client.upgradeConnection(request));
    }

    @Override
    public HttpResponse request(final HttpRequest request) throws Exception {
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

    static final class ReservedHttpConnectionToReservedBlockingHttpConnection extends ReservedBlockingHttpConnection {
        private final ReservedHttpConnection connection;

        ReservedHttpConnectionToReservedBlockingHttpConnection(ReservedHttpConnection connection) {
            super(connection.reqRespFactory);
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
        public <T> BlockingIterable<T> getSettingIterable(final StreamingHttpConnection.SettingKey<T> settingKey) {
            return connection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public HttpResponse request(final HttpRequest request) throws Exception {
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

        Completable onClose() {
            return connection.onClose();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return connection;
        }
    }
}
