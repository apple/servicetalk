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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static io.servicetalk.http.api.DefaultFullHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultFullHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class HttpConnectionToAggregatedHttpConnection<I, O> extends AggregatedHttpConnection {
    private final HttpPayloadChunkConnection payloadConnection;

    HttpConnectionToAggregatedHttpConnection(final HttpConnection<I, O> connection,
                                             final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                             final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        payloadConnection = new HttpPayloadChunkConnection(
                connection, requestPayloadTransformer, responsePayloadTransformer);
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return payloadConnection.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(final HttpConnection.SettingKey<T> settingKey) {
        return payloadConnection.getSettingStream(settingKey);
    }

    @Override
    public Single<FullHttpResponse> request(final FullHttpRequest request) {
        return payloadConnection.request(toHttpRequest(request)).flatMap(response ->
                from(response, payloadConnection.getExecutionContext().getBufferAllocator()));
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return payloadConnection.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return payloadConnection.onClose();
    }

    @Override
    public Completable closeAsync() {
        return payloadConnection.closeAsync();
    }

    @Override
    HttpConnection<HttpPayloadChunk, HttpPayloadChunk> asClientInternal() {
        return payloadConnection;
    }

    private final class HttpPayloadChunkConnection extends HttpConnection<HttpPayloadChunk, HttpPayloadChunk> {
        private final HttpConnection<I, O> connection;
        private final Function<HttpPayloadChunk, I> requestPayloadTransformer;
        private final Function<O, HttpPayloadChunk> responsePayloadTransformer;

        HttpPayloadChunkConnection(final HttpConnection<I, O> connection,
                                   final Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                   final Function<O, HttpPayloadChunk> responsePayloadTransformer) {
            this.connection = requireNonNull(connection);
            this.requestPayloadTransformer = requireNonNull(requestPayloadTransformer);
            this.responsePayloadTransformer = requireNonNull(responsePayloadTransformer);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connection.getConnectionContext();
        }

        @Override
        public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return connection.getSettingStream(settingKey);
        }

        @Override
        public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
            return connection.request(request.transformPayloadBody(
                        requestPayload -> requestPayload.map(requestPayloadTransformer)))
                    .map(response -> response.transformPayloadBody(
                        responsePayload -> responsePayload.map(responsePayloadTransformer)));
        }

        @Override
        public ExecutionContext getExecutionContext() {
            return connection.getExecutionContext();
        }

        @Override
        public Completable onClose() {
            return connection.onClose();
        }

        @Override
        public Completable closeAsync() {
            return connection.closeAsync();
        }

        @Override
        AggregatedHttpConnection asAggregatedInternal(
                                    Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                    Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
            return HttpConnectionToAggregatedHttpConnection.this;
        }
    }
}
