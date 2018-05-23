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

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

final class HttpConnectionToBlockingAggregatedHttpConnection extends BlockingAggregatedHttpConnection {
    private final HttpConnection connection;

    HttpConnectionToBlockingAggregatedHttpConnection(HttpConnection connection) {
        this.connection = requireNonNull(connection);
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
        BlockingUtils.close(connection);
    }

    @Override
    HttpConnection asConnectionInternal() {
        return connection;
    }

    Completable onClose() {
        return connection.onClose();
    }
}
