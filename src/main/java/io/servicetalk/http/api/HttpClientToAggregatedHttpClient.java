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

import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultAggregatedHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class HttpClientToAggregatedHttpClient extends AggregatedHttpClient {
    private final HttpClient client;

    HttpClientToAggregatedHttpClient(final HttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends AggregatedReservedHttpConnection> reserveConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return client.reserveConnection(toHttpRequest(request)).map(ReservedHttpConnectionToAggregated::new);
    }

    @Override
    public Single<? extends AggregatedUpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return client.upgradeConnection(toHttpRequest(request))
                .flatMap(response -> from(response, client.getExecutionContext().getBufferAllocator())
                        .map(fullResponse -> new UpgradableHttpResponseToAggregated<>(response,
                                fullResponse.getPayloadBody(), fullResponse.getTrailers())));
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return client.request(toHttpRequest(request)).flatMap(response ->
                from(response, client.getExecutionContext().getBufferAllocator()));
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
    HttpClient asClientInternal() {
        return client;
    }

    static final class ReservedHttpConnectionToAggregated extends AggregatedReservedHttpConnection {
        private final ReservedHttpConnection reservedConnection;

        ReservedHttpConnectionToAggregated(ReservedHttpConnection reservedConnection) {
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
        public <T> Publisher<T> getSettingStream(final HttpConnection.SettingKey<T> settingKey) {
            return reservedConnection.getSettingStream(settingKey);
        }

        @Override
        public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(final AggregatedHttpRequest<HttpPayloadChunk> request) {
            return reservedConnection.request(toHttpRequest(request)).flatMap(response ->
                    from(response, reservedConnection.getExecutionContext().getBufferAllocator()));
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
        ReservedHttpConnection asConnectionInternal() {
            return reservedConnection;
        }
    }

    private static final class UpgradableHttpResponseToAggregated<T> implements AggregatedUpgradableHttpResponse<T> {
        private final UpgradableHttpResponse<?> upgradableResponse;
        private final T payloadBody;
        private final HttpHeaders trailers;

        UpgradableHttpResponseToAggregated(UpgradableHttpResponse<?> upgradableResponse,
                                           T payloadBody,
                                           HttpHeaders trailers) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.trailers = requireNonNull(trailers);
        }

        @Override
        public AggregatedReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionToAggregated(upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public <R> AggregatedUpgradableHttpResponse<R> transformPayloadBody(final Function<T, R> transformer) {
            return new UpgradableHttpResponseToAggregated<>(
                    upgradableResponse, transformer.apply(payloadBody), trailers);
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public AggregatedUpgradableHttpResponse<T> setVersion(final HttpProtocolVersion version) {
            upgradableResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradableResponse.getHeaders();
        }

        @Override
        public String toString() {
            return upgradableResponse.toString();
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
        public AggregatedUpgradableHttpResponse<T> setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }

        @Override
        public T getPayloadBody() {
            return payloadBody;
        }

        @Override
        public HttpHeaders getTrailers() {
            return trailers;
        }
    }
}
