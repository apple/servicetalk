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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.AggregatedHttpClientToHttpClient.AggregatedToUpgradableHttpResponseConverter;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultFullHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultFullHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class HttpClientToAggregatedHttpClient<I, O> extends AggregatedHttpClient {
    private final HttpPayloadChunkClient payloadClient;

    HttpClientToAggregatedHttpClient(final HttpClient<I, O> client,
                                     final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                     final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        payloadClient = new HttpPayloadChunkClient(client, requestPayloadTransformer, responsePayloadTransformer);
    }

    @Override
    public Single<? extends AggregatedReservedHttpConnection> reserveConnection(final FullHttpRequest request) {
        return payloadClient.reserveConnection(toHttpRequest(request)).map(ReservedHttpConnectionToAggregated::new);
    }

    @Override
    public Single<? extends AggregatedUpgradableHttpResponse> upgradeConnection(final FullHttpRequest request) {
        return payloadClient.upgradeConnection(toHttpRequest(request))
                .flatMap(response -> from(response, payloadClient.getExecutionContext().getBufferAllocator())
                        .map(fullResponse -> new UpgradableHttpResponseToAggregated(response, fullResponse)));
    }

    @Override
    public Single<FullHttpResponse> request(final FullHttpRequest request) {
        return payloadClient.request(toHttpRequest(request)).flatMap(response ->
                from(response, payloadClient.getExecutionContext().getBufferAllocator()));
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return payloadClient.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return payloadClient.onClose();
    }

    @Override
    public Completable closeAsync() {
        return payloadClient.closeAsync();
    }

    @Override
    HttpClient<HttpPayloadChunk, HttpPayloadChunk> asClientInternal() {
        return payloadClient;
    }

    private final class HttpPayloadChunkClient extends HttpClient<HttpPayloadChunk, HttpPayloadChunk> {
        private final HttpClient<I, O> client;
        private final Function<HttpPayloadChunk, I> requestPayloadTransformer;
        private final Function<O, HttpPayloadChunk> responsePayloadTransformer;

        HttpPayloadChunkClient(final HttpClient<I, O> client,
                               final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                               final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            this.client = requireNonNull(client);
            this.requestPayloadTransformer = requireNonNull(requestPayloadTransformer);
            this.responsePayloadTransformer = requireNonNull(responsePayloadTransformer);
        }

        @Override
        public Single<? extends ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk>> reserveConnection(
                final HttpRequest<HttpPayloadChunk> request) {
            return client.reserveConnection(request.transformPayloadBody(
                    requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(reserved -> new ReservedPayloadChunkConnection<>(null,
                            reserved, requestPayloadTransformer, responsePayloadTransformer));
        }

        @Override
        public Single<? extends UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk>> upgradeConnection(
                final HttpRequest<HttpPayloadChunk> request) {
            return client.upgradeConnection(request.transformPayloadBody(
                    requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(upgradable -> new UpgradablePayloadResponse<>(
                            upgradable, requestPayloadTransformer, responsePayloadTransformer));
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return client.request(request.transformPayloadBody(
                        requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(response -> response.transformPayloadBody(
                            responsePayload -> responsePayload.map(responsePayloadTransformer)));
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
        AggregatedHttpClient asAggregatedClientInternal(
                                Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
            return HttpClientToAggregatedHttpClient.this;
        }
    }

    private static final class ReservedHttpConnectionToAggregated extends AggregatedReservedHttpConnection {
        private final ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> reservedConnection;

        ReservedHttpConnectionToAggregated(
                ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> reservedConnection) {
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
        public Single<FullHttpResponse> request(final FullHttpRequest request) {
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
        ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> asReservedConnectionInternal() {
            return reservedConnection;
        }
    }

    private static final class UpgradableHttpResponseToAggregated implements AggregatedUpgradableHttpResponse {
        private final UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> upgradableResponse;
        private final FullHttpResponse response;

        UpgradableHttpResponseToAggregated(
                UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> upgradableResponse,
                FullHttpResponse response) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.response = requireNonNull(response);
        }

        @Override
        public AggregatedReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionToAggregated(upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public AggregatedUpgradableHttpResponse setVersion(final HttpProtocolVersion version) {
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
        public AggregatedUpgradableHttpResponse setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }

        @Override
        public Buffer getPayloadBody() {
            return response.getPayloadBody();
        }

        @Override
        public HttpHeaders getTrailers() {
            return response.getTrailers();
        }

        @Override
        public AggregatedUpgradableHttpResponse duplicate() {
            response.duplicate();
            return this;
        }

        @Override
        public AggregatedUpgradableHttpResponse replace(final Buffer content) {
            return new UpgradableHttpResponseToAggregated(upgradableResponse, response.replace(content));
        }
    }

    static final class GenericReservedHttpConnectionToAggregated<I, O> extends AggregatedReservedHttpConnection {
        private final ReservedPayloadChunkConnection<I, O> payloadReserved;

        GenericReservedHttpConnectionToAggregated(final ReservedHttpConnection<I, O> reservedConnection,
                                                  final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                                  final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            payloadReserved = new ReservedPayloadChunkConnection<>(this,
                    reservedConnection, requestPayloadTransformer, responsePayloadTransformer);
        }

        @Override
        public Completable releaseAsync() {
            return payloadReserved.releaseAsync();
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return payloadReserved.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final HttpConnection.SettingKey<T> settingKey) {
            return payloadReserved.getSettingStream(settingKey);
        }

        @Override
        public Single<FullHttpResponse> request(final FullHttpRequest request) {
            return payloadReserved.request(toHttpRequest(request)).flatMap(response ->
                    from(response, payloadReserved.getExecutionContext().getBufferAllocator()));
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return payloadReserved.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return payloadReserved.onClose();
        }

        @Override
        public Completable closeAsync() {
            return payloadReserved.closeAsync();
        }

        @Override
        ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> asReservedConnectionInternal() {
            return payloadReserved;
        }
    }

    private static final class UpgradablePayloadResponse<I, O> implements
                                                           UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> {
        private final UpgradableHttpResponse<I, O> upgradableResponse;
        private final Function<HttpPayloadChunk, I> requestPayloadTransformer;
        private final Function<O, HttpPayloadChunk> responsePayloadTransformer;
        private final Publisher<HttpPayloadChunk> payloadBody;

        UpgradablePayloadResponse(final UpgradableHttpResponse<I, O> upgradableResponse,
                                  final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                  final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            this(upgradableResponse, requestPayloadTransformer, responsePayloadTransformer,
                    upgradableResponse.getPayloadBody().map(responsePayloadTransformer));
        }

        UpgradablePayloadResponse(final UpgradableHttpResponse<I, O> upgradableResponse,
                                  final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                  final Function<O, HttpPayloadChunk> responsePayloadTransformer,
                                  final Publisher<HttpPayloadChunk> payloadBody) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.requestPayloadTransformer = requireNonNull(requestPayloadTransformer);
            this.responsePayloadTransformer = requireNonNull(responsePayloadTransformer);
            this.payloadBody = requireNonNull(payloadBody);
        }

        @Override
        public ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> getHttpConnection(
                final boolean releaseReturnsToClient) {
            return new ReservedPayloadChunkConnection<>(null,
                    upgradableResponse.getHttpConnection(releaseReturnsToClient),
                    requestPayloadTransformer,
                    responsePayloadTransformer);
        }

        @Override
        public Publisher<HttpPayloadChunk> getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <R> UpgradableHttpResponse<HttpPayloadChunk, R> transformPayloadBody(
                final Function<Publisher<HttpPayloadChunk>, Publisher<R>> transformer) {
            return new AggregatedToUpgradableHttpResponseConverter<>(this, transformer, transformer.apply(payloadBody));
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> setVersion(final HttpProtocolVersion version) {
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
    }

    private static final class ReservedPayloadChunkConnection<I, O> extends
                                                         ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> {
        @Nullable
        private final AggregatedReservedHttpConnection asAggregated;
        private final ReservedHttpConnection<I, O> reservedConnection;
        private final Function<HttpPayloadChunk, I> requestPayloadTransformer;
        private final Function<O, HttpPayloadChunk> responsePayloadTransformer;

        ReservedPayloadChunkConnection(@Nullable final AggregatedReservedHttpConnection asAggregated,
                                       final ReservedHttpConnection<I, O> reservedConnection,
                                       final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                       final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            this.asAggregated = asAggregated;
            this.reservedConnection = requireNonNull(reservedConnection);
            this.requestPayloadTransformer = requireNonNull(requestPayloadTransformer);
            this.responsePayloadTransformer = requireNonNull(responsePayloadTransformer);
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
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return reservedConnection.request(request.transformPayloadBody(
                    requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(response -> response.transformPayloadBody(
                            responsePayload -> responsePayload.map(responsePayloadTransformer)));
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
        AggregatedReservedHttpConnection asAggregatedReservedConnectionInternal(
                Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
            return asAggregated == null ?
                super.asAggregatedReservedConnectionInternal(requestPayloadTransformer, responsePayloadTransformer) :
                    asAggregated;
        }
    }
}
