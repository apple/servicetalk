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
import io.servicetalk.http.api.BlockingHttpClient.BlockingReservedHttpConnection;
import io.servicetalk.http.api.BlockingHttpClient.BlockingUpgradableHttpResponse;
import io.servicetalk.http.api.HttpClientToBlockingHttpClient.ReservedHttpConnectionToBlocking;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientToHttpClient extends HttpClient {
    private final BlockingHttpClient blockingClient;

    BlockingHttpClientToHttpClient(BlockingHttpClient blockingClient) {
        this.blockingClient = requireNonNull(blockingClient);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return BlockingUtils.request(blockingClient, request);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> new BlockingToReservedHttpConnection(
                    blockingClient.reserveConnection(new DefaultBlockingHttpRequest<>(request))));
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> {
            BlockingUpgradableHttpResponse<HttpPayloadChunk> upgradeResponse =
                    blockingClient.upgradeConnection(new DefaultBlockingHttpRequest<>(request));
            return new BlockingToUpgradableHttpResponse<>(upgradeResponse,
                    from(upgradeResponse.getPayloadBody()));
        });
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return blockingClient.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        if (blockingClient instanceof HttpClientToBlockingHttpClient) {
            return ((HttpClientToBlockingHttpClient) blockingClient).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingClient.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingClient::close);
    }

    @Override
    BlockingHttpClient asBlockingClientInternal() {
        return blockingClient;
    }

    static final class BlockingToReservedHttpConnection extends ReservedHttpConnection {
        private final BlockingReservedHttpConnection blockingReservedConnection;

        BlockingToReservedHttpConnection(BlockingReservedHttpConnection blockingReservedConnection) {
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
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return BlockingUtils.request(blockingReservedConnection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return blockingReservedConnection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            if (blockingReservedConnection instanceof ReservedHttpConnectionToBlocking) {
                return ((ReservedHttpConnectionToBlocking) blockingReservedConnection).onClose();
            }

            return error(new UnsupportedOperationException("unsupported type: " +
                    blockingReservedConnection.getClass()));
        }

        @Override
        public Completable closeAsync() {
            return blockingToCompletable(blockingReservedConnection::close);
        }

        @Override
        BlockingReservedHttpConnection asBlockingConnectionInternal() {
            return blockingReservedConnection;
        }
    }

    static final class BlockingToUpgradableHttpResponse<T> implements UpgradableHttpResponse<T> {
        private final BlockingUpgradableHttpResponse<?> upgradeResponse;
        private final Publisher<T> payloadBody;

        BlockingToUpgradableHttpResponse(BlockingUpgradableHttpResponse<?> upgradeResponse,
                                         Publisher<T> payloadBody) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new BlockingToReservedHttpConnection(
                    upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public BlockingToUpgradableHttpResponse<T> setVersion(final HttpProtocolVersion version) {
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
        public BlockingToUpgradableHttpResponse<T> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public Publisher<T> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> BlockingToUpgradableHttpResponse<R> transformPayloadBody(
                final Function<Publisher<T>, Publisher<R>> transformer) {
            return new BlockingToUpgradableHttpResponse<>(upgradeResponse, transformer.apply(payloadBody));
        }
    }
}
