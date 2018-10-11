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
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.HttpDataSourceTranformations.BridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpPayloadAndTrailersFromSingleOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.ReservedStreamingHttpConnectionToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.UpgradableStreamingHttpResponseToBlockingStreaming;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.UpgradableStreamingHttpResponseToUpgradableHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingHttpClientToStreamingHttpClient extends StreamingHttpClient {
    private final BlockingStreamingHttpClient blockingClient;

    BlockingStreamingHttpClientToStreamingHttpClient(BlockingStreamingHttpClient blockingClient) {
        super(new BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(
                blockingClient.reqRespFactory));
        this.blockingClient = requireNonNull(blockingClient);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return BlockingUtils.request(blockingClient, request);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest request) {
        return blockingToSingle(() -> new BlockingToReservedStreamingHttpConnection(
                    blockingClient.reserveConnection(request.toBlockingStreamingRequest())));
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(final StreamingHttpRequest request) {
        return blockingToSingle(() ->
                blockingClient.upgradeConnection(request.toBlockingStreamingRequest()).toStreamingResponse());
    }

    @Override
    public ExecutionContext executionContext() {
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

        BlockingToReservedStreamingHttpConnection(ReservedBlockingStreamingHttpConnection connection) {
            super(new BlockingStreamingHttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(
                    connection.reqRespFactory));
            this.blockingReservedConnection = requireNonNull(connection);
        }

        @Override
        public Completable releaseAsync() {
            return blockingToCompletable(blockingReservedConnection::release);
        }

        @Override
        public ConnectionContext connectionContext() {
            return blockingReservedConnection.connectionContext();
        }

        @Override
        public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
            return from(blockingReservedConnection.settingIterable(settingKey));
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return BlockingUtils.request(blockingReservedConnection, request);
        }

        @Override
        public ExecutionContext executionContext() {
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

    static final class BlockingToUpgradableStreamingHttpResponse implements UpgradableStreamingHttpResponse {
        private final UpgradableBlockingStreamingHttpResponse upgradeResponse;
        private final Publisher<?> payloadBody;
        private final Single<HttpHeaders> trailersSingle;
        private final BufferAllocator allocator;

        BlockingToUpgradableStreamingHttpResponse(UpgradableBlockingStreamingHttpResponse upgradeResponse,
                                                  Publisher<?> payloadBody,
                                                  Single<HttpHeaders> trailersSingle,
                                                  BufferAllocator allocator) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.trailersSingle = requireNonNull(trailersSingle);
            this.allocator = requireNonNull(allocator);
        }

        @Override
        public ReservedStreamingHttpConnection httpConnection(final boolean releaseReturnsToClient) {
            return new BlockingToReservedStreamingHttpConnection(
                    upgradeResponse.httpConnection(releaseReturnsToClient));
        }

        @Override
        public UpgradableStreamingHttpResponse payloadBody(final Publisher<Buffer> payloadBody) {
            return transformPayloadBody(old -> payloadBody.liftSynchronous(
                    new BridgeFlowControlAndDiscardOperator(old)));
        }

        @Override
        public <T> UpgradableStreamingHttpResponse payloadBody(final Publisher<T> payloadBody,
                                                               final HttpSerializer<T> serializer) {
            return transformPayloadBody(old -> payloadBody.liftSynchronous(
                    new SerializeBridgeFlowControlAndDiscardOperator<>(old)), serializer);
        }

        @Override
        public Publisher<Buffer> payloadBody() {
            return payloadBody.liftSynchronous(HttpBufferFilterOperator.INSTANCE);
        }

        @Override
        public Publisher<Object> payloadBodyAndTrailers() {
            return payloadBody
                    .map(payload -> (Object) payload) // down cast to Object
                    .concatWith(trailersSingle);
        }

        @Override
        public <T> UpgradableStreamingHttpResponse transformPayloadBody(
                final Function<Publisher<Buffer>, Publisher<T>> transformer, final HttpSerializer<T> serializer) {
            return new BlockingToUpgradableStreamingHttpResponse(upgradeResponse,
                    serializer.serialize(headers(), transformer.apply(payloadBody()), allocator),
                    trailersSingle, allocator);
        }

        @Override
        public UpgradableStreamingHttpResponse transformPayloadBody(
                final UnaryOperator<Publisher<Buffer>> transformer) {
            return new BlockingToUpgradableStreamingHttpResponse(upgradeResponse,
                    transformer.apply(payloadBody()), trailersSingle, allocator);
        }

        @Override
        public UpgradableStreamingHttpResponse transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
            return new BlockingToUpgradableStreamingHttpResponse(upgradeResponse, transformer.apply(payloadBody),
                    trailersSingle, allocator);
        }

        @Override
        public <T> UpgradableStreamingHttpResponse transform(
                final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer,
                final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
            final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
            return new BlockingToUpgradableStreamingHttpResponse(upgradeResponse, payloadBody()
                    .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                            trailersTrans, trailersSingle, outTrailersSingle)),
                    outTrailersSingle, allocator);
        }

        @Override
        public <T> UpgradableStreamingHttpResponse transformRaw(
                final Supplier<T> stateSupplier, final BiFunction<Object, T, ?> transformer,
                final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
            final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
            return new BlockingToUpgradableStreamingHttpResponse(upgradeResponse, payloadBody
                    .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                            trailersTrans, trailersSingle, outTrailersSingle)),
                    outTrailersSingle, allocator);
        }

        @Override
        public Single<? extends UpgradableHttpResponse> toResponse() {
            return aggregatePayloadAndTrailers(payloadBodyAndTrailers(), allocator).map(pair -> {
                assert pair.trailers != null;
                return new UpgradableStreamingHttpResponseToUpgradableHttpResponse(
                        upgradeResponse.toStreamingResponse(), pair.compositeBuffer, pair.trailers, allocator);
            });
        }

        @Override
        public UpgradableBlockingStreamingHttpResponse toBlockingStreamingResponse() {
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradeResponse.toStreamingResponse(),
                    payloadBody.toIterable(), trailersSingle, allocator);
        }

        @Override
        public HttpProtocolVersion version() {
            return upgradeResponse.version();
        }

        @Override
        public BlockingToUpgradableStreamingHttpResponse version(final HttpProtocolVersion version) {
            upgradeResponse.version(version);
            return this;
        }

        @Override
        public HttpHeaders headers() {
            return upgradeResponse.headers();
        }

        @Override
        public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence>
                                               headerFilter) {
            return upgradeResponse.toString(headerFilter);
        }

        @Override
        public HttpResponseStatus status() {
            return upgradeResponse.status();
        }

        @Override
        public BlockingToUpgradableStreamingHttpResponse status(final HttpResponseStatus status) {
            upgradeResponse.status(status);
            return this;
        }
    }
}
