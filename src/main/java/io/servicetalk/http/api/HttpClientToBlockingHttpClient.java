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
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpRequests.fromBlockingRequest;
import static java.util.Objects.requireNonNull;

final class HttpClientToBlockingHttpClient extends BlockingHttpClient {
    private final HttpClient client;

    HttpClientToBlockingHttpClient(HttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingHttpResponse<HttpPayloadChunk> request(final BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public BlockingReservedHttpConnection reserveConnection(final BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return new ReservedHttpConnectionToBlocking(awaitIndefinitelyNonNull(client.reserveConnection(
                fromBlockingRequest(request))));
    }

    @Override
    public BlockingUpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(
            final BlockingHttpRequest<HttpPayloadChunk> request) throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        UpgradableHttpResponse<HttpPayloadChunk> upgradeResponse = awaitIndefinitelyNonNull(
                client.upgradeConnection(fromBlockingRequest(request)));
        return new UpgradableHttpResponseToBlocking<>(upgradeResponse, upgradeResponse.getPayloadBody().toIterable());
    }

    @Override
    public void close() throws Exception {
        BlockingUtils.close(client);
    }

    Completable onClose() {
        return client.onClose();
    }

    @Override
    HttpClient asClientInternal() {
        return client;
    }

    static final class ReservedHttpConnectionToBlocking extends BlockingReservedHttpConnection {
        private final ReservedHttpConnection reservedConnection;

        ReservedHttpConnectionToBlocking(ReservedHttpConnection reservedConnection) {
            this.reservedConnection = requireNonNull(reservedConnection);
        }

        @Override
        public void release() throws Exception {
            // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter).
            // So we don't apply any explicit timeout here and just wait forever.
            awaitIndefinitely(reservedConnection.releaseAsync());
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return reservedConnection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final HttpConnection.SettingKey<T> settingKey) {
            return reservedConnection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public BlockingHttpResponse<HttpPayloadChunk> request(final BlockingHttpRequest<HttpPayloadChunk> request)
                throws Exception {
            return BlockingUtils.request(reservedConnection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return reservedConnection.getExecutionContext();
        }

        @Override
        public void close() throws Exception {
            BlockingUtils.close(reservedConnection);
        }

        Completable onClose() {
            return reservedConnection.onClose();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return reservedConnection;
        }
    }

    private static final class UpgradableHttpResponseToBlocking<T> implements BlockingUpgradableHttpResponse<T> {
        private final UpgradableHttpResponse<?> upgradeResponse;
        private final BlockingIterable<T> payloadBody;

        UpgradableHttpResponseToBlocking(UpgradableHttpResponse<?> upgradeResponse,
                                         BlockingIterable<T> payloadBody) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public BlockingReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionToBlocking(upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public UpgradableHttpResponseToBlocking<T> setVersion(final HttpProtocolVersion version) {
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
        public UpgradableHttpResponseToBlocking<T> setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }

        @Override
        public BlockingIterable<T> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> BlockingUpgradableHttpResponse<R> transformPayloadBody(
                final Function<BlockingIterable<T>, BlockingIterable<R>> transformer) {
            return new UpgradableHttpResponseToBlocking<>(upgradeResponse, transformer.apply(payloadBody));
        }
    }
}
