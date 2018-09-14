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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.http.api.BlockingStreamingHttpClientToStreamingHttpClient.BlockingToUpgradableStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.HttpSerializerUtils.HttpBuffersAndTrailersIterable;
import io.servicetalk.http.api.HttpSerializerUtils.HttpObjectsAndTrailersIterable;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingStreamingHttpClient extends BlockingStreamingHttpClient {
    private final StreamingHttpClient client;

    StreamingHttpClientToBlockingStreamingHttpClient(StreamingHttpClient client) {
        super(new StreamingHttpRequestFactoryToBlockingStreamingHttpRequestFactory(client));
        this.client = requireNonNull(client);
    }

    @Override
    public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
        return BlockingUtils.request(client, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return client.getExecutionContext();
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final BlockingStreamingHttpRequest request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return new ReservedStreamingHttpConnectionToBlockingStreaming(
                blockingInvocation(client.reserveConnection(request.toStreamingRequest())));
    }

    @Override
    public UpgradableBlockingStreamingHttpResponse upgradeConnection(
            final BlockingStreamingHttpRequest request) throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return blockingInvocation(client.upgradeConnection(
                request.toStreamingRequest())).toBlockingStreamingResponse();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    Completable onClose() {
        return client.onClose();
    }

    @Override
    StreamingHttpClient asStreamingClientInternal() {
        return client;
    }

    static final class ReservedStreamingHttpConnectionToBlockingStreaming extends
                                                                          ReservedBlockingStreamingHttpConnection {
        private final ReservedStreamingHttpConnection connection;

        ReservedStreamingHttpConnectionToBlockingStreaming(ReservedStreamingHttpConnection connection) {
            super(new StreamingHttpRequestFactoryToBlockingStreamingHttpRequestFactory(connection));
            this.connection = requireNonNull(connection);
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connection.getConnectionContext();
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final StreamingHttpConnection.SettingKey<T> settingKey) {
            return connection.getSettingStream(settingKey).toIterable();
        }

        @Override
        public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
            return BlockingUtils.request(connection, request);
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public void close() throws Exception {
            blockingInvocation(connection.closeAsync());
        }

        Completable onClose() {
            return connection.onClose();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return connection;
        }
    }

    static final class UpgradableStreamingHttpResponseToBlockingStreaming implements
                                                                    UpgradableBlockingStreamingHttpResponse {
        private final UpgradableStreamingHttpResponse upgradeResponse;
        private final BlockingIterable<?> payloadBody;
        private final Single<HttpHeaders> trailersSingle;
        private final BufferAllocator allocator;

        UpgradableStreamingHttpResponseToBlockingStreaming(UpgradableStreamingHttpResponse upgradeResponse,
                                                           BlockingIterable<?> payloadBody,
                                                           Single<HttpHeaders> trailersSingle,
                                                           BufferAllocator allocator) {
            this.upgradeResponse = requireNonNull(upgradeResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.trailersSingle = requireNonNull(trailersSingle);
            this.allocator = requireNonNull(allocator);
        }

        @Override
        public ReservedBlockingStreamingHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedStreamingHttpConnectionToBlockingStreaming(
                    upgradeResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public <T> BlockingIterable<T> getPayloadBody(final HttpDeserializer<T> deserializer) {
            return deserializer.deserialize(getHeaders(), payloadBody);
        }

        @Override
        public <T> UpgradableBlockingStreamingHttpResponse transformPayloadBody(
                final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
                final HttpSerializer<T> serializer) {
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradeResponse,
                    serializer.serialize(getHeaders(), transformer.apply(getPayloadBody()), allocator),
                    trailersSingle, allocator);
        }

        @Override
        public UpgradableBlockingStreamingHttpResponse transformPayloadBody(
                final UnaryOperator<BlockingIterable<Buffer>> transformer) {
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradeResponse, transformer.apply(getPayloadBody()),
                    trailersSingle, allocator);
        }

        @Override
        public UpgradableBlockingStreamingHttpResponse transformRawPayloadBody(
                final UnaryOperator<BlockingIterable<?>> transformer) {
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradeResponse, transformer.apply(payloadBody),
                    trailersSingle, allocator);
        }

        @Override
        public <T> UpgradableBlockingStreamingHttpResponse transform(
                final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer,
                final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
            final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradeResponse,
                    new HttpBuffersAndTrailersIterable<>(getPayloadBody(), stateSupplier,
                            transformer, trailersTrans, trailersSingle, outTrailersSingle),
                    outTrailersSingle, allocator);
        }

        @Override
        public <T> UpgradableBlockingStreamingHttpResponse transformRaw(
                final Supplier<T> stateSupplier, final BiFunction<Object, T, ?> transformer,
                final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
            final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradeResponse,
                    new HttpObjectsAndTrailersIterable<>(payloadBody, stateSupplier,
                            transformer, trailersTrans, trailersSingle, outTrailersSingle),
                    outTrailersSingle, allocator);
        }

        @Override
        public Single<? extends UpgradableHttpResponse> toResponse() {
            return toStreamingResponse().toResponse();
        }

        @Override
        public UpgradableStreamingHttpResponse toStreamingResponse() {
            return new BlockingToUpgradableStreamingHttpResponse(this, from(payloadBody), trailersSingle, allocator);
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradeResponse.getVersion();
        }

        @Override
        public UpgradableStreamingHttpResponseToBlockingStreaming setVersion(final HttpProtocolVersion version) {
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
        public UpgradableStreamingHttpResponseToBlockingStreaming setStatus(final HttpResponseStatus status) {
            upgradeResponse.setStatus(status);
            return this;
        }
    }
}
