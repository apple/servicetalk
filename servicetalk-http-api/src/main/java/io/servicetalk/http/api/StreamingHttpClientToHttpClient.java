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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.http.api.BufferHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.BufferHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToHttpClient extends HttpClient {
    private final StreamingHttpClient client;

    StreamingHttpClientToHttpClient(final StreamingHttpClient client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return client.reserveConnection(toHttpRequest(request))
                .map(ReservedStreamingHttpConnectionToReservedHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final HttpRequest<HttpPayloadChunk> request) {
        return doUpgradeConnection(client, request);
    }

    static Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> doUpgradeConnection(
            StreamingHttpClient client, final HttpRequest<HttpPayloadChunk> request) {
        return client.upgradeConnection(toHttpRequest(request))
                .flatMap(response -> from(response, client.getExecutionContext().getBufferAllocator())
                        .map(fullResponse -> new UpgradableStreamingHttpResponseToUpgradableHttpResponse<>(response,
                                fullResponse.getPayloadBody(), fullResponse.getTrailers())));
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
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
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
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
        public Completable closeAsyncGracefully() {
            return reservedConnection.closeAsyncGracefully();
        }

        @Override
        ReservedStreamingHttpConnection asStreamingConnectionInternal() {
            return reservedConnection;
        }
    }

    static final class UpgradableStreamingHttpResponseToUpgradableHttpResponse<T> implements UpgradableHttpResponse<T> {
        private final UpgradableStreamingHttpResponse<?> upgradableResponse;
        private final T payloadBody;
        private final HttpHeaders trailers;

        UpgradableStreamingHttpResponseToUpgradableHttpResponse(UpgradableStreamingHttpResponse<?> upgradableResponse,
                                                                T payloadBody,
                                                                HttpHeaders trailers) {
            this.upgradableResponse = requireNonNull(upgradableResponse);
            this.payloadBody = requireNonNull(payloadBody);
            this.trailers = requireNonNull(trailers);
        }

        @Override
        public ReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
            return new ReservedStreamingHttpConnectionToReservedHttpConnection(
                    upgradableResponse.getHttpConnection(releaseReturnsToClient));
        }

        @Override
        public <R> UpgradableHttpResponse<R> transformPayloadBody(final Function<T, R> transformer) {
            return new UpgradableStreamingHttpResponseToUpgradableHttpResponse<>(
                    upgradableResponse, transformer.apply(payloadBody), trailers);
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
        public UpgradableHttpResponse<T> setStatus(final HttpResponseStatus status) {
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
