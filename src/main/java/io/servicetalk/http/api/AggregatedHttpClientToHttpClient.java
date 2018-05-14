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
import static io.servicetalk.http.api.DefaultFullHttpRequest.from;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static java.util.Objects.requireNonNull;

final class AggregatedHttpClientToHttpClient extends HttpClient<HttpPayloadChunk, HttpPayloadChunk> {
    private final AggregatedHttpClient aggregatedClient;

    AggregatedHttpClientToHttpClient(AggregatedHttpClient aggregatedClient) {
        this.aggregatedClient = requireNonNull(aggregatedClient);
    }

    @Override
    public Single<? extends ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk>> reserveConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedClient.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedClient::reserveConnection).map(AggregatedToReservedHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedClient.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedClient::upgradeConnection).map(AggregatedToUpgradableHttpResponse::new);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedClient.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedClient::request).map(DefaultFullHttpResponse::toHttpResponse);
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
    AggregatedHttpClient asAggregatedClientInternal(
                                Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
        return aggregatedClient;
    }

    static final class AggregatedToReservedHttpConnection extends
                                                          ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> {
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
                    .flatMap(aggregatedReservedConnection::request).map(DefaultFullHttpResponse::toHttpResponse);
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
        AggregatedReservedHttpConnection asAggregatedReservedConnectionInternal(
                                            Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                            Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
            return aggregatedReservedConnection;
        }
    }

    private static final class AggregatedToUpgradableHttpResponse implements
                                                          UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> {
        private final AggregatedUpgradableHttpResponse upgradableResponse;
        private final Publisher<HttpPayloadChunk> payloadBody;

        AggregatedToUpgradableHttpResponse(AggregatedUpgradableHttpResponse upgradableResponse) {
            this(upgradableResponse,
                    just(newLastPayloadChunk(upgradableResponse.getContent(), upgradableResponse.getTrailers())));
        }

        private AggregatedToUpgradableHttpResponse(AggregatedUpgradableHttpResponse upgradableResponse,
                                                   Publisher<HttpPayloadChunk> payloadBody) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> getHttpConnection(
                final boolean releaseReturnsToClient) {
            return new AggregatedToReservedHttpConnection(upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public Publisher<HttpPayloadChunk> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> setVersion(
                final HttpProtocolVersion version) {
            upgradableResponse.setVersion(version);
            return this;
        }

        @Override
        public HttpHeaders getHeaders() {
            return upgradableResponse.getHeaders();
        }

        @Override
        public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
            return upgradableResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus getStatus() {
            return upgradableResponse.getStatus();
        }

        @Override
        public UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }

        @Override
        public <R> UpgradableHttpResponse<HttpPayloadChunk, R> transformPayloadBody(
                final Function<Publisher<HttpPayloadChunk>, Publisher<R>> transformer) {
            return new AggregatedToUpgradableHttpResponseConverter<>(this, transformer, transformer.apply(payloadBody));
        }
    }

    static final class AggregatedToUpgradableHttpResponseConverter<O1, O2> implements
                                                              UpgradableHttpResponse<HttpPayloadChunk, O2> {
        private final UpgradableHttpResponse<HttpPayloadChunk, O1> upgradableResponse;
        private final Function<Publisher<O1>, Publisher<O2>> transformer;
        private final Publisher<O2> payloadBody;

        AggregatedToUpgradableHttpResponseConverter(UpgradableHttpResponse<HttpPayloadChunk, O1> upgradableResponse,
                                                    Function<Publisher<O1>, Publisher<O2>> transformer,
                                                    Publisher<O2> payloadBody) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.transformer = requireNonNull(transformer);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedHttpConnection<HttpPayloadChunk, O2> getHttpConnection(final boolean releaseReturnsToClient) {
            return new AggregatedReservedHttpConnectionConverter<>(
                    upgradableResponse.getHttpConnection(releaseReturnsToClient), transformer);
        }

        @Override
        public Publisher<O2> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> UpgradableHttpResponse<HttpPayloadChunk, R> transformPayloadBody(
                final Function<Publisher<O2>, Publisher<R>> transformer) {
            return new AggregatedToUpgradableHttpResponseConverter<>(this, transformer, transformer.apply(payloadBody));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public UpgradableHttpResponse<HttpPayloadChunk, O2> setVersion(final HttpProtocolVersion version) {
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
        public UpgradableHttpResponse<HttpPayloadChunk, O2> setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }
    }

    private static final class AggregatedReservedHttpConnectionConverter<O1, O2> extends
                                                                        ReservedHttpConnection<HttpPayloadChunk, O2> {
        private final ReservedHttpConnection<HttpPayloadChunk, O1> reservedConnection;
        private final Function<Publisher<O1>, Publisher<O2>> transformer;

        AggregatedReservedHttpConnectionConverter(
                                      ReservedHttpConnection<HttpPayloadChunk, O1> reservedConnection,
                                      Function<Publisher<O1>, Publisher<O2>> transformer) {
            this.reservedConnection = requireNonNull(reservedConnection);
            this.transformer = requireNonNull(transformer);
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
        public Single<HttpResponse<O2>> request(final HttpRequest<HttpPayloadChunk> request) {
            return reservedConnection.request(request).map(response -> response.transformPayloadBody(transformer));
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
    }
}
