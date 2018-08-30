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

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingHttpClientToStreamingHttpClient extends StreamingHttpClient {
    private final BlockingStreamingHttpClient blockingClient;

    BlockingStreamingHttpClientToStreamingHttpClient(BlockingStreamingHttpClient blockingClient) {
        this.blockingClient = requireNonNull(blockingClient);
    }

    @Override
    public Single<StreamingHttpResponse<HttpPayloadChunk>> request(final StreamingHttpRequest<HttpPayloadChunk> request) {
        return BlockingUtils.request(blockingClient, request);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> new BlockingToReservedStreamingHttpConnection(
                    blockingClient.reserveConnection(new DefaultBlockingStreamingHttpRequest<>(request))));
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final StreamingHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> {
            BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse<HttpPayloadChunk> upgradeResponse =
                    blockingClient.upgradeConnection(new DefaultBlockingStreamingHttpRequest<>(request));
            return new BlockingToUpgradableStreamingHttpResponse<>(upgradeResponse, from(upgradeResponse.getPayloadBody()));
        });
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return blockingClient.getExecutionContext();
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

    static final class BlockingToReservedStreamingHttpConnection extends ReservedStreamingHttpConnection {
        private final ReservedBlockingStreamingHttpConnection blockingReservedConnection;

        BlockingToReservedStreamingHttpConnection(ReservedBlockingStreamingHttpConnection blockingReservedConnection) {
            this.blockingReservedConnection = requireNonNull(blockingReservedConnection);
        }

        @Override
        public Completable releaseAsync() {
            return blockingToCompletable(blockingReservedConnection::release);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return blockingReservedConnection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return from(blockingReservedConnection.getSettingIterable(settingKey));
        }

        @Override
        public Single<StreamingHttpResponse<HttpPayloadChunk>> request(final StreamingHttpRequest<HttpPayloadChunk> request) {
            return BlockingUtils.request(blockingReservedConnection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return blockingReservedConnection.getExecutionContext();
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
    }

    static final class BlockingToUpgradableStreamingHttpResponse<T> implements UpgradableStreamingHttpResponse<T> {
        private final BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse<?> upgradeResponse;
        private final Publisher<T> payloadBody;

        BlockingToUpgradableStreamingHttpResponse(BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse<?> upgradeResponse,
                                                  Publisher<T> payloadBody) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedStreamingHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new BlockingToReservedStreamingHttpConnection(
                    upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public BlockingToUpgradableStreamingHttpResponse<T> setVersion(final HttpProtocolVersion version) {
            upgradeResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradeResponse.getHeaders();
        }

        @Override
        public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence>
                                               headerFilter) {
            return upgradeResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus getStatus() {
            return upgradeResponse.getStatus();
        }

        @Override
        public BlockingToUpgradableStreamingHttpResponse<T> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public Publisher<T> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> BlockingToUpgradableStreamingHttpResponse<R> transformPayloadBody(
                final Function<Publisher<T>, Publisher<R>> transformer) {
            return new BlockingToUpgradableStreamingHttpResponse<>(upgradeResponse, transformer.apply(payloadBody));
        }
    }
}
