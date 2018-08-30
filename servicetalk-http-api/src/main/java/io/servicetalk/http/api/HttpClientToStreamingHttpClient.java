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
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.DefaultHttpRequest.from;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static java.util.Objects.requireNonNull;

final class HttpClientToStreamingHttpClient extends StreamingHttpClient {
    private final HttpClient client;

    HttpClientToStreamingHttpClient(HttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(
            final StreamingHttpRequest<HttpPayloadChunk> request) {
        return from(request, client.getExecutionContext().getBufferAllocator())
                .flatMap(client::reserveConnection)
                .map(ReservedHttpConnectionToReservedStreamingHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final StreamingHttpRequest<HttpPayloadChunk> request) {
        return from(request, client.getExecutionContext().getBufferAllocator())
                .flatMap(client::upgradeConnection).map(
                        UpgradableHttpResponseToUpgradableStreamingHttpResponse::newUpgradeResponse);
    }

    @Override
    public Single<StreamingHttpResponse<HttpPayloadChunk>> request(
            final StreamingHttpRequest<HttpPayloadChunk> request) {
        return from(request, client.getExecutionContext().getBufferAllocator())
                .flatMap(client::request).map(DefaultHttpResponse::toHttpResponse);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return client.onClose();
    }

    @Override
    public Completable closeAsync() {
        return client.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return client.closeAsyncGracefully();
    }

    @Override
    HttpClient asClientInternal() {
        return client;
    }

    static final class ReservedHttpConnectionToReservedStreamingHttpConnection extends ReservedStreamingHttpConnection {
        private final ReservedHttpConnection reservedConnection;

        ReservedHttpConnectionToReservedStreamingHttpConnection(ReservedHttpConnection reservedConnection) {
            this.reservedConnection = requireNonNull(reservedConnection);
        }

        @Override
        public Completable releaseAsync() {
            return reservedConnection.releaseAsync();
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return reservedConnection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return reservedConnection.getSettingStream(settingKey);
        }

        @Override
        public Single<StreamingHttpResponse<HttpPayloadChunk>> request(
                final StreamingHttpRequest<HttpPayloadChunk> request) {
            return from(request, reservedConnection.getExecutionContext().getBufferAllocator())
                    .flatMap(reservedConnection::request).map(DefaultHttpResponse::toHttpResponse);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return reservedConnection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return reservedConnection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return reservedConnection.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return reservedConnection.closeAsyncGracefully();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return reservedConnection;
        }
    }

    static final class UpgradableHttpResponseToUpgradableStreamingHttpResponse<T> implements
                                                                                  UpgradableStreamingHttpResponse<T> {
        private final UpgradableHttpResponse<?> upgradableResponse;
        private final Publisher<T> payloadBody;

        UpgradableHttpResponseToUpgradableStreamingHttpResponse(UpgradableHttpResponse<?> upgradableResponse,
                                                                Publisher<T> payloadBody) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        static UpgradableHttpResponseToUpgradableStreamingHttpResponse<HttpPayloadChunk> newUpgradeResponse(
                UpgradableHttpResponse<HttpPayloadChunk> upgradableResponse) {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse<>(upgradableResponse,
                    just(newLastPayloadChunk(upgradableResponse.getPayloadBody().getContent(),
                            upgradableResponse.getTrailers())));
        }

        @Override
        public ReservedStreamingHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionToReservedStreamingHttpConnection(
                    upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public Publisher<T> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public UpgradableStreamingHttpResponse<T> setVersion(final HttpProtocolVersion version) {
            upgradableResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradableResponse.getHeaders();
        }

        @Override
        public String toString(
                final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
            return upgradableResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus getStatus() {
            return upgradableResponse.getStatus();
        }

        @Override
        public UpgradableStreamingHttpResponse<T> setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }

        @Override
        public <R> UpgradableStreamingHttpResponse<R> transformPayloadBody(
                final Function<Publisher<T>, Publisher<R>> transformer) {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse<>(
                    upgradableResponse, transformer.apply(payloadBody));
        }
    }
}
