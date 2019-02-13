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
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_NONE_STRATEGY;
import static java.util.Objects.requireNonNull;

final class HttpClientToBlockingHttpClient extends BlockingHttpClient {
    private final HttpClient client;

    private HttpClientToBlockingHttpClient(HttpClient client, HttpExecutionStrategy strategy) {
        super(client.reqRespFactory, strategy);
        this.client = requireNonNull(client);
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final HttpExecutionStrategy strategy,
                                                            final HttpRequestMetaData metaData) throws Exception {
        return new ReservedHttpConnectionToReservedBlockingHttpConnection(
                blockingInvocation(client.reserveConnection(strategy, metaData)), executionStrategy());
    }

    @Override
    public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
        return blockingInvocation(client.request(strategy, request));
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
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

    static BlockingHttpClient transform(HttpClient client) {
        // Any client created for alternate programming models always originates from the async streaming model
        // which contains the filters and hence the effective strategy while converting them to the different
        // programming models. So, in this case we simply take the executionStrategy() from the passed client instead
        // of re-calculating the effective strategy.
        return new HttpClientToBlockingHttpClient(client, client.executionStrategy().merge(OFFLOAD_NONE_STRATEGY));
    }

    static final class ReservedHttpConnectionToReservedBlockingHttpConnection extends ReservedBlockingHttpConnection {
        private final ReservedHttpConnection connection;

        private ReservedHttpConnectionToReservedBlockingHttpConnection(final ReservedHttpConnection connection,
                                                                       final HttpExecutionStrategy strategy) {
            super(connection.reqRespFactory, strategy);
            this.connection = requireNonNull(connection);
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ConnectionContext connectionContext() {
            return connection.connectionContext();
        }

        @Override
        public <T> BlockingIterable<T> settingIterable(final StreamingHttpConnection.SettingKey<T> settingKey) {
            return connection.settingStream(settingKey).toIterable();
        }

        @Override
        public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
            return blockingInvocation(connection.request(strategy, request));
        }

        @Override
        public ExecutionContext executionContext() {
            return connection.executionContext();
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

        static ReservedBlockingHttpConnection transform(ReservedHttpConnection connection) {
            // Any connection created for alternate programming models always originates from the async streaming model
            // which contains the filters and hence the effective strategy while converting them to the different
            // programming models. So, in this case we simply take the executionStrategy() from the passed connection
            // instead of re-calculating the effective strategy.
            return new ReservedHttpConnectionToReservedBlockingHttpConnection(connection,
                    connection.executionStrategy().merge(OFFLOAD_NONE_STRATEGY));
        }
    }
}
