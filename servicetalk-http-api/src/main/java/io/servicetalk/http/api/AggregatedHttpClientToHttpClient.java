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
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.from;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static java.util.Objects.requireNonNull;

final class AggregatedHttpClientToHttpClient extends HttpClient {
    private final AggregatedHttpClient aggregatedClient;

    AggregatedHttpClientToHttpClient(AggregatedHttpClient aggregatedClient) {
        this.aggregatedClient = requireNonNull(aggregatedClient);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedClient.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedClient::reserveConnection).map(AggregatedToReservedHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedClient.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedClient::upgradeConnection).map(
                        AggregatedToUpgradableHttpResponse::newUpgradeResponse);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedClient.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedClient::request).map(DefaultAggregatedHttpResponse::toHttpResponse);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return aggregatedClient.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return aggregatedClient.onClose();
    }

    @Override
    public Completable closeAsync() {
        return aggregatedClient.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return aggregatedClient.closeAsyncGracefully();
    }

    @Override
    AggregatedHttpClient asAggregatedClientInternal() {
        return aggregatedClient;
    }

    static final class AggregatedToReservedHttpConnection extends ReservedHttpConnection {
        private final AggregatedReservedHttpConnection aggregatedReservedConnection;

        AggregatedToReservedHttpConnection(AggregatedReservedHttpConnection aggregatedReservedConnection) {
            this.aggregatedReservedConnection = requireNonNull(aggregatedReservedConnection);
        }

        @Override
        public Completable releaseAsync() {
            return aggregatedReservedConnection.releaseAsync();
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return aggregatedReservedConnection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return aggregatedReservedConnection.getSettingStream(settingKey);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return from(request, aggregatedReservedConnection.getExecutionContext().getBufferAllocator())
                    .flatMap(aggregatedReservedConnection::request).map(DefaultAggregatedHttpResponse::toHttpResponse);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return aggregatedReservedConnection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return aggregatedReservedConnection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return aggregatedReservedConnection.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return aggregatedReservedConnection.closeAsyncGracefully();
        }

        @Override
        AggregatedReservedHttpConnection asAggregatedConnectionInternal() {
            return aggregatedReservedConnection;
        }
    }

    static final class AggregatedToUpgradableHttpResponse<T> implements HttpClient.UpgradableHttpResponse<T> {
        private final AggregatedUpgradableHttpResponse<?> upgradableResponse;
        private final Publisher<T> payloadBody;

        AggregatedToUpgradableHttpResponse(AggregatedUpgradableHttpResponse<?> upgradableResponse,
                                           Publisher<T> payloadBody) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        static AggregatedToUpgradableHttpResponse<HttpPayloadChunk> newUpgradeResponse(
                AggregatedUpgradableHttpResponse<HttpPayloadChunk> upgradableResponse) {
            return new AggregatedToUpgradableHttpResponse<>(upgradableResponse,
                    just(newLastPayloadChunk(upgradableResponse.getPayloadBody().getContent(),
                            upgradableResponse.getTrailers())));
        }

        @Override
        public ReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new AggregatedToReservedHttpConnection(upgradableResponse.getHttpConnection(releaseReturnsToClient));
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
        public UpgradableHttpResponse<T> setVersion(final HttpProtocolVersion version) {
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
        public UpgradableHttpResponse<T> setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }

        @Override
        public <R> UpgradableHttpResponse<R> transformPayloadBody(
                final Function<Publisher<T>, Publisher<R>> transformer) {
            return new AggregatedToUpgradableHttpResponse<>(upgradableResponse, transformer.apply(payloadBody));
        }
    }
}
