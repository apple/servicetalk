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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_SEND_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingStreamingHttpClient extends BlockingStreamingHttpClient {
    private final StreamingHttpClient client;

    private StreamingHttpClientToBlockingStreamingHttpClient(StreamingHttpClient client,
                                                             HttpExecutionStrategy strategy) {
        super(new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(
                client.reqRespFactory), strategy);
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                 final BlockingStreamingHttpRequest request) throws Exception {
        return blockingInvocation(client.request(strategy, request.toStreamingRequest())).toBlockingStreamingResponse();
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final HttpExecutionStrategy strategy,
                                                                     final HttpRequestMetaData metaData)
            throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return new ReservedStreamingHttpConnectionToBlockingStreaming(
                blockingInvocation(client.reserveConnection(strategy, metaData)), executionStrategy());
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    Completable onClose() {
        return client.onClose();
    }

    @Override
    StreamingHttpClient asStreamingClientInternal() {
        return client;
    }

    static BlockingStreamingHttpClient transform(StreamingHttpClient client) {
        final HttpExecutionStrategy defaultStrategy = client instanceof StreamingHttpClientFilter ?
                ((StreamingHttpClientFilter) client).effectiveExecutionStrategy(OFFLOAD_SEND_STRATEGY) :
                OFFLOAD_SEND_STRATEGY;
        return new StreamingHttpClientToBlockingStreamingHttpClient(client, defaultStrategy);
    }

    static final class ReservedStreamingHttpConnectionToBlockingStreaming extends
                                                                          ReservedBlockingStreamingHttpConnection {
        private final ReservedStreamingHttpConnection connection;

        private ReservedStreamingHttpConnectionToBlockingStreaming(final ReservedStreamingHttpConnection connection,
                                                                   final HttpExecutionStrategy strategy) {
            super(new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(
                    connection.reqRespFactory), strategy);
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
        public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                     final BlockingStreamingHttpRequest request) throws Exception {
            return blockingInvocation(connection.request(strategy, request.toStreamingRequest()))
                    .toBlockingStreamingResponse();
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
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return connection;
        }

        static ReservedBlockingStreamingHttpConnection transform(ReservedStreamingHttpConnection conn) {
            final HttpExecutionStrategy defaultStrategy = conn instanceof ReservedStreamingHttpConnectionFilter ?
                    ((ReservedStreamingHttpConnectionFilter) conn).effectiveExecutionStrategy(OFFLOAD_SEND_STRATEGY) :
                    OFFLOAD_SEND_STRATEGY;
            return new ReservedStreamingHttpConnectionToBlockingStreaming(conn, defaultStrategy);
        }
    }
}
