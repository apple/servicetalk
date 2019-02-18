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
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_ALL_STRATEGY;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingHttpClientToStreamingHttpClient extends StreamingHttpClient {
    private final BlockingStreamingHttpClient blockingClient;

    private BlockingStreamingHttpClientToStreamingHttpClient(final BlockingStreamingHttpClient blockingClient,
                                                             final HttpExecutionStrategy strategy) {
        super(new BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(
                blockingClient.reqRespFactory), strategy);
        this.blockingClient = requireNonNull(blockingClient);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return BlockingUtils.request(blockingClient, strategy, request);
    }

    @Override
    public Single<ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                     final HttpRequestMetaData metaData) {
        return blockingToSingle(() -> new BlockingToReservedStreamingHttpConnection(
                blockingClient.reserveConnection(strategy, metaData), executionStrategy()));
    }

    @Override
    public ExecutionContext executionContext() {
        return blockingClient.executionContext();
    }

    @Override
    public Completable onClose() {
        if (blockingClient instanceof StreamingHttpClientToBlockingStreamingHttpClient) {
            return ((StreamingHttpClientToBlockingStreamingHttpClient) blockingClient).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingClient.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingClient::close);
    }

    @Override
    BlockingStreamingHttpClient asBlockingStreamingClientInternal() {
        return blockingClient;
    }

    static StreamingHttpClient transform(BlockingStreamingHttpClient client) {
        // Any client created for alternate programming models always originates from the async streaming model
        // which contains the filters and hence the effective strategy while converting them to the different
        // programming models. So, in this case we simply take the executionStrategy() from the passed client instead
        // of re-calculating the effective strategy.
        return new BlockingStreamingHttpClientToStreamingHttpClient(client,
                client.executionStrategy().merge(OFFLOAD_ALL_STRATEGY));
    }

    static final class BlockingToReservedStreamingHttpConnection extends ReservedStreamingHttpConnection {
        private final ReservedBlockingStreamingHttpConnection blockingReservedConnection;

        private BlockingToReservedStreamingHttpConnection(final ReservedBlockingStreamingHttpConnection connection,
                                                          final HttpExecutionStrategy strategy) {
            super(new BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(
                    connection.reqRespFactory), strategy);
            this.blockingReservedConnection = requireNonNull(connection);
        }

        @Override
        public Completable releaseAsync() {
            return blockingToCompletable(blockingReservedConnection::release);
        }

        @Override
        public ConnectionContext connectionContext() {
            return blockingReservedConnection.connectionContext();
        }

        @Override
        public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
            return from(blockingReservedConnection.settingIterable(settingKey));
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return blockingToSingle(() ->
                    blockingReservedConnection.request(strategy, request.toBlockingStreamingRequest())
                            .toStreamingResponse());
        }

        @Override
        public ExecutionContext executionContext() {
            return blockingReservedConnection.executionContext();
        }

        @Override
        public Completable onClose() {
            if (blockingReservedConnection instanceof ReservedStreamingHttpConnectionToBlockingStreaming) {
                return ((ReservedStreamingHttpConnectionToBlockingStreaming) blockingReservedConnection).onClose();
            }

            return error(new UnsupportedOperationException("unsupported type: " +
                    blockingReservedConnection.getClass()));
        }

        @Override
        public Completable closeAsync() {
            return blockingToCompletable(blockingReservedConnection::close);
        }

        @Override
        ReservedBlockingStreamingHttpConnection asBlockingStreamingConnectionInternal() {
            return blockingReservedConnection;
        }

        static ReservedStreamingHttpConnection transform(ReservedBlockingStreamingHttpConnection connection) {
            // Any connection created for alternate programming models always originates from the async streaming model
            // which contains the filters and hence the effective strategy while converting them to the different
            // programming models. So, in this case we simply take the executionStrategy() from the passed connection
            // instead of re-calculating the effective strategy.
            return new BlockingToReservedStreamingHttpConnection(connection,
                    connection.executionStrategy().merge(OFFLOAD_ALL_STRATEGY));
        }
    }
}
