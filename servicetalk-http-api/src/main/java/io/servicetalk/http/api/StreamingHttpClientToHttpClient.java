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
import io.servicetalk.http.api.BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClientToStreamingHttpClient.UpgradableHttpResponseToUpgradableStreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpClientToBlockingStreamingHttpClient.UpgradableStreamingHttpResponseToBlockingStreaming;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToHttpClient extends HttpClient {
    private final StreamingHttpClient client;

    StreamingHttpClientToHttpClient(final StreamingHttpClient client) {
        super(new StreamingHttpRequestFactoryToHttpRequestFactory(client.requestFactory),
                new StreamingHttpResponseFactoryToHttpResponseFactory(client.getHttpResponseFactory()));
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest request) {
        return client.reserveConnection(request.toStreamingRequest())
                .map(ReservedStreamingHttpConnectionToReservedHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableHttpResponse> upgradeConnection(final HttpRequest request) {
        return doUpgradeConnection(client, request);
    }

    static Single<? extends UpgradableHttpResponse> doUpgradeConnection(
            StreamingHttpClient client, HttpRequest request) {
        return client.upgradeConnection(request.toStreamingRequest())
                      .flatMap(UpgradableStreamingHttpResponse::toResponse);
    }

    @Override
    public Single<? extends HttpResponse> request(final HttpRequest request) {
        return client.request(request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
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
    StreamingHttpClient asStreamingClientInternal() {
        return client;
    }

    static final class ReservedStreamingHttpConnectionToReservedHttpConnection extends ReservedHttpConnection {
        private final ReservedStreamingHttpConnection reservedConnection;

        ReservedStreamingHttpConnectionToReservedHttpConnection(ReservedStreamingHttpConnection reservedConnection) {
            super(new StreamingHttpRequestFactoryToHttpRequestFactory(reservedConnection.requestFactory),
                    new StreamingHttpResponseFactoryToHttpResponseFactory(reservedConnection.getHttpResponseFactory()));
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
        public <T> Publisher<T> getSettingStream(final StreamingHttpConnection.SettingKey<T> settingKey) {
            return reservedConnection.getSettingStream(settingKey);
        }

        @Override
        public Single<? extends HttpResponse> request(final HttpRequest request) {
            return reservedConnection.request(request.toStreamingRequest())
                    .flatMap(StreamingHttpResponse::toResponse);
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
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return reservedConnection;
        }
    }

    static final class UpgradableStreamingHttpResponseToUpgradableHttpResponse implements UpgradableHttpResponse {
        private final UpgradableStreamingHttpResponse upgradableResponse;
        private final Buffer payloadBody;
        private final HttpHeaders trailers;
        private final BufferAllocator allocator;

        UpgradableStreamingHttpResponseToUpgradableHttpResponse(UpgradableStreamingHttpResponse upgradableResponse,
                                                                Buffer payloadBody,
                                                                HttpHeaders trailers,
                                                                BufferAllocator allocator) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.trailers = requireNonNull(trailers);
            this.allocator = requireNonNull(allocator);
        }

        @Override
        public ReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedStreamingHttpConnectionToReservedHttpConnection(
                    upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public UpgradableHttpResponse setPayloadBody(final Buffer payloadBody) {
            return new UpgradableStreamingHttpResponseToUpgradableHttpResponse(upgradableResponse, payloadBody,
                    trailers, allocator);
        }

        @Override
        public <T> UpgradableHttpResponse setPayloadBody(final T pojo, final HttpSerializer<T> serializer) {
            return new UpgradableStreamingHttpResponseToUpgradableHttpResponse(upgradableResponse,
                    serializer.serialize(getHeaders(), pojo, allocator), trailers, allocator);
        }

        @Override
        public UpgradableStreamingHttpResponse toStreamingResponse() {
            return new UpgradableHttpResponseToUpgradableStreamingHttpResponse(this, just(payloadBody),
                    success(trailers), allocator);
        }

        @Override
        public UpgradableBlockingStreamingHttpResponse toBlockingStreamingResponse() {
            return new UpgradableStreamingHttpResponseToBlockingStreaming(upgradableResponse, just(payloadBody).toIterable(),
                    success(trailers), allocator);
        }

        @Override
        public HttpProtocolVersion getVersion() {
            return upgradableResponse.getVersion();
        }

        @Override
        public UpgradableHttpResponse setVersion(final HttpProtocolVersion version) {
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
        public UpgradableHttpResponse setStatus(final HttpResponseStatus status) {
            upgradableResponse.setStatus(status);
            return this;
        }

        @Override
        public Buffer getPayloadBody() {
            return payloadBody;
        }

        @Override
        public <T> T getPayloadBody(final HttpDeserializer<T> deserializer) {
            return deserializer.deserialize(getHeaders(), payloadBody);
        }

        @Override
        public HttpHeaders getTrailers() {
            return trailers;
        }
    }
}
