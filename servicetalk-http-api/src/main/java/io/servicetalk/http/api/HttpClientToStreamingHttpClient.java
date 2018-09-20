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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.http.api.BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.HttpDataSourceTranformations.BridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpPayloadAndTrailersFromSingleOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.UpgradableStreamingHttpResponseToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.UpgradableStreamingHttpResponseToUpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpClientToStreamingHttpClient.UpgradableHttpResponseToUpgradableStreamingHttpResponse.newUpgradeResponse;
import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

final class HttpClientToStreamingHttpClient extends StreamingHttpClient {
    private final HttpClient client;

    HttpClientToStreamingHttpClient(HttpClient client) {
        super(new HttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(client.reqRespFactory));
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest request) {
        return request.toRequest().flatMap(client::reserveConnection)
                .map(ReservedHttpConnectionToReservedStreamingHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(final StreamingHttpRequest request) {
        return request.toRequest().flatMap(client::upgradeConnection).map(resp ->
                        newUpgradeResponse(resp, getExecutionContext().getBufferAllocator()));
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return request.toRequest().flatMap(client::request).map(HttpResponse::toStreamingResponse);
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
            super(new HttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(
                    reservedConnection.reqRespFactory));
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
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return request.toRequest().flatMap(reservedConnection::request).map(HttpResponse::toStreamingResponse);
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

    static final class UpgradableHttpResponseToUpgradableStreamingHttpResponse implements
                                                                   StreamingHttpClient.UpgradableStreamingHttpResponse {
        private final UpgradableHttpResponse upgradableResponse;
        private final Publisher<?> payloadBody;
        private final Single<HttpHeaders> trailersSingle;
        private final BufferAllocator allocator;

        UpgradableHttpResponseToUpgradableStreamingHttpResponse(UpgradableHttpResponse upgradableResponse,
                                                                Publisher<?> payloadBody,
                                                                Single<HttpHeaders> trailersSingle,
                                                                BufferAllocator allocator) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.allocator = requireNonNull(allocator);
            this.payloadBody = requireNonNull(payloadBody);
            this.trailersSingle = requireNonNull(trailersSingle);
        }

        static UpgradableHttpResponseToUpgradableStreamingHttpResponse newUpgradeResponse(
                UpgradableHttpResponse upgradableResponse, BufferAllocator allocator) {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(upgradableResponse,
                    just(upgradableResponse.getPayloadBody()), success(upgradableResponse.getTrailers()), allocator);
        }

        @Override
        public ReservedStreamingHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedHttpConnectionToReservedStreamingHttpConnection(
                    upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public UpgradableStreamingHttpResponse setPayloadBody(final Publisher<Buffer> payloadBody) {
            return transformPayloadBody(old -> payloadBody.liftSynchronous(
                    new BridgeFlowControlAndDiscardOperator(old)));
        }

        @Override
        public <T> UpgradableStreamingHttpResponse setPayloadBody(final Publisher<T> payloadBody,
                                                                  final HttpSerializer<T> serializer) {
            return transformPayloadBody(old -> payloadBody.liftSynchronous(
                    new SerializeBridgeFlowControlAndDiscardOperator<>(old)), serializer);
        }

        @Override
        public Publisher<Buffer> getPayloadBody() {
            return payloadBody.liftSynchronous(HttpBufferFilterOperator.INSTANCE);
        }

        @Override
        public Publisher<Object> getPayloadBodyAndTrailers() {
            return payloadBody.map(obj -> (Object) obj) // down cast to Object
                    .concatWith(trailersSingle);
        }

        @Override
        public <T> UpgradableStreamingHttpResponse transformPayloadBody(
                final Function<Publisher<Buffer>, Publisher<T>> transformer, final HttpSerializer<T> serializer) {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(upgradableResponse,
                    serializer.serialize(getHeaders(), transformer.apply(getPayloadBody()), allocator),
                    trailersSingle, allocator);
        }

        @Override
        public UpgradableStreamingHttpResponse transformPayloadBody(
                final UnaryOperator<Publisher<Buffer>> transformer) {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(upgradableResponse,
                    transformer.apply(getPayloadBody()), trailersSingle, allocator);
        }

        @Override
        public UpgradableStreamingHttpResponse transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(upgradableResponse,
                    transformer.apply(payloadBody), trailersSingle, allocator);
        }

        @Override
        public <T> UpgradableStreamingHttpResponse transform(
                final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer,
                final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
            final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(upgradableResponse, getPayloadBody()
                    .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                            trailersTrans, trailersSingle, outTrailersSingle)),
                    outTrailersSingle, allocator);
        }

        @Override
        public <T> UpgradableStreamingHttpResponse transformRaw(
                final Supplier<T> stateSupplier, final BiFunction<Object, T, ?> transformer,
                final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
            final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(upgradableResponse, payloadBody
                    .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                            trailersTrans, trailersSingle, outTrailersSingle)),
                    outTrailersSingle, allocator);
        }

        @Override
        public Single<UpgradableHttpResponse> toResponse() {
            return aggregatePayloadAndTrailers(getPayloadBodyAndTrailers(), allocator).map(pair -> {
                assert pair.trailers != null;
                return new UpgradableStreamingHttpResponseToUpgradableHttpResponse(
                        UpgradableHttpResponseToUpgradableStreamingHttpResponse.this, pair.compositeBuffer,
                        pair.trailers, allocator);
            });
        }

        @Override
        public UpgradableBlockingStreamingHttpResponse toBlockingStreamingResponse() {
            return new UpgradableStreamingHttpResponseToBlockingStreaming(this, payloadBody.toIterable(),
                    trailersSingle, allocator);
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public UpgradableStreamingHttpResponse setVersion(final HttpProtocolVersion version) {
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
        public UpgradableStreamingHttpResponse setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }
    }
}
