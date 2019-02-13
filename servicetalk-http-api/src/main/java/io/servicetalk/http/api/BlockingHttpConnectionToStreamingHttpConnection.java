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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

final class BlockingHttpConnectionToStreamingHttpConnection extends StreamingHttpConnection {
    private final BlockingHttpConnection connection;

    private BlockingHttpConnectionToStreamingHttpConnection(final BlockingHttpConnection connection,
                                                            final HttpExecutionStrategy strategy) {
        super(new HttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(connection.reqRespFactory), strategy);
        this.connection = requireNonNull(connection);
    }

    @Override
    public ConnectionContext connectionContext() {
        return connection.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return from(connection.settingIterable(settingKey));
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return BlockingUtils.request(connection, strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return connection.executionContext();
    }

    @Override
    public Completable onClose() {
        if (connection instanceof StreamingHttpConnectionToBlockingHttpConnection) {
            return ((StreamingHttpConnectionToBlockingHttpConnection) connection).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + connection.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(connection::close);
    }

    @Override
    BlockingHttpConnection asBlockingConnectionInternal() {
        return connection;
    }

    static StreamingHttpConnection transform(BlockingHttpConnection conn) {
        // Any connection created for alternate programming models always originates from the async streaming model
        // which contains the filters and hence the effective strategy while converting them to the different
        // programming models. So, in this case we simply take the executionStrategy() from the passed connection
        // instead of re-calculating the effective strategy.
        return new BlockingHttpConnectionToStreamingHttpConnection(conn,
                conn.executionStrategy().merge(OFFLOAD_ALL_STRATEGY));
    }
}
