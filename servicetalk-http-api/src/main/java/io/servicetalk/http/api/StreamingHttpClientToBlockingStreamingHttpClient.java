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
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.StreamingHttpRequests.fromBlockingRequest;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingStreamingHttpClient extends BlockingStreamingHttpClient {
    private final StreamingHttpClient client;

    StreamingHttpClientToBlockingStreamingHttpClient(StreamingHttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingStreamingHttpResponse<HttpPayloadChunk> request(
            final BlockingStreamingHttpRequest<HttpPayloadChunk> request) throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(
            final BlockingStreamingHttpRequest<HttpPayloadChunk> request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return new ReservedStreamingHttpConnectionToBlockingStreaming(
                blockingInvocation(client.reserveConnection(fromBlockingRequest(request))));
    }

    @Override
    public UpgradableBlockingStreamingHttpResponse<HttpPayloadChunk> upgradeConnection(
            final BlockingStreamingHttpRequest<HttpPayloadChunk> request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        StreamingHttpClient.UpgradableStreamingHttpResponse<HttpPayloadChunk> upgradeResponse = blockingInvocation(
                client.upgradeConnection(fromBlockingRequest(request)));
        return new UpgradableHttpResponseToBlockingStreaming<>(upgradeResponse,
                upgradeResponse.getPayloadBody().toIterable());
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

    static final class ReservedStreamingHttpConnectionToBlockingStreaming extends
                                                                          ReservedBlockingStreamingHttpConnection {
        private final ReservedStreamingHttpConnection connection;

        ReservedStreamingHttpConnectionToBlockingStreaming(ReservedStreamingHttpConnection connection) {
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
        public BlockingStreamingHttpResponse<HttpPayloadChunk> request(
                final BlockingStreamingHttpRequest<HttpPayloadChunk> request) throws Exception {
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
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return connection;
        }
    }

    static final class UpgradableHttpResponseToBlockingStreaming<T> implements
                                                                    UpgradableBlockingStreamingHttpResponse<T> {
        private final UpgradableStreamingHttpResponse<?> upgradeResponse;
        private final BlockingIterable<T> payloadBody;

        UpgradableHttpResponseToBlockingStreaming(UpgradableStreamingHttpResponse<?> upgradeResponse,
                                                  BlockingIterable<T> payloadBody) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedBlockingStreamingHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedStreamingHttpConnectionToBlockingStreaming(
                    upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public UpgradableHttpResponseToBlockingStreaming<T> setVersion(final HttpProtocolVersion version) {
            upgradeResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradeResponse.getHeaders();
        }

        @Override
        public String toString(
                final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
            return upgradeResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus getStatus() {
            return upgradeResponse.getStatus();
        }

        @Override
        public UpgradableHttpResponseToBlockingStreaming<T> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public BlockingIterable<T> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> UpgradableBlockingStreamingHttpResponse<R> transformPayloadBody(
                final Function<BlockingIterable<T>, BlockingIterable<R>> transformer) {
            return new UpgradableHttpResponseToBlockingStreaming<>(upgradeResponse, transformer.apply(payloadBody));
        }
    }
}
