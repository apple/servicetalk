/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.FilterableStreamingHttpConnection.SettingKey;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_SEND_STRATEGY;
import static io.servicetalk.http.api.RequestResponseFactories.toBlockingStreaming;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingStreamingHttpClient implements BlockingStreamingHttpClient {
    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

    StreamingHttpClientToBlockingStreamingHttpClient(final StreamingHttpClient client) {
        strategy = client.computeExecutionStrategy(OFFLOAD_SEND_STRATEGY);
        this.client = client;
        reqRespFactory = toBlockingStreaming(client);
    }

    @Override
    public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
        return request(strategy, request);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final HttpRequestMetaData metaData)
            throws Exception {
        return reserveConnection(strategy, metaData);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final HttpExecutionStrategy strategy,
                                                                     final HttpRequestMetaData metaData)
            throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return new ReservedStreamingHttpConnectionToBlockingStreaming(
                blockingInvocation(client.reserveConnection(strategy, metaData)), this.strategy, reqRespFactory);
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
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
    public void close() throws Exception {
        client.close();
    }

    @Override
    public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
        // TODO(scott): should we include the API strategy?
        return client.computeExecutionStrategy(other);
    }

    @Override
    public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    @Override
    public BlockingStreamingHttpResponse newResponse(final HttpResponseStatus status) {
        return reqRespFactory.newResponse(status);
    }

    static final class ReservedStreamingHttpConnectionToBlockingStreaming implements
                                                                              ReservedBlockingStreamingHttpConnection {
        private final ReservedStreamingHttpConnection connection;
        private final HttpExecutionStrategy strategy;
        private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

        ReservedStreamingHttpConnectionToBlockingStreaming(ReservedStreamingHttpConnection connection) {
            this(connection, connection.computeExecutionStrategy(OFFLOAD_SEND_STRATEGY),
                    toBlockingStreaming(connection));
        }

        ReservedStreamingHttpConnectionToBlockingStreaming(ReservedStreamingHttpConnection connection,
                                                           HttpExecutionStrategy strategy,
                                                           BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
            this.connection = requireNonNull(connection);
            this.strategy = strategy;
            this.reqRespFactory = reqRespFactory;
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
        }

        @Override
        public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
            return request(strategy, request);
        }

        @Override
        public ConnectionContext connectionContext() {
            return connection.connectionContext();
        }

        @Override
        public <T> BlockingIterable<T> settingIterable(final SettingKey<T> settingKey) {
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
            connection.close();
        }

        @Override
        public HttpExecutionStrategy computeExecutionStrategy(final HttpExecutionStrategy other) {
            // TODO(scott): should we include the API strategy?
            return connection.computeExecutionStrategy(other);
        }

        @Override
        public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }

        @Override
        public BlockingStreamingHttpResponse newResponse(final HttpResponseStatus status) {
            return reqRespFactory.newResponse(status);
        }
    }
}
