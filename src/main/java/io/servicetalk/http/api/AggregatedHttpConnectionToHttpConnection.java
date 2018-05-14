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

import static io.servicetalk.http.api.DefaultFullHttpRequest.from;
import static java.util.Objects.requireNonNull;

final class AggregatedHttpConnectionToHttpConnection extends HttpConnection<HttpPayloadChunk, HttpPayloadChunk> {
    private final AggregatedHttpConnection aggregatedConnection;

    AggregatedHttpConnectionToHttpConnection(AggregatedHttpConnection aggregatedConnection) {
        this.aggregatedConnection = requireNonNull(aggregatedConnection);
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return aggregatedConnection.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
        return aggregatedConnection.getSettingStream(settingKey);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return from(request, aggregatedConnection.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedConnection::request).map(DefaultFullHttpResponse::toHttpResponse);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return aggregatedConnection.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return aggregatedConnection.onClose();
    }

    @Override
    public Completable closeAsync() {
        return aggregatedConnection.closeAsync();
    }

    @Override
    AggregatedHttpConnection asAggregatedInternal(
                                  Function<HttpPayloadChunk, HttpPayloadChunk> requestPayloadTransformer,
                                  Function<HttpPayloadChunk, HttpPayloadChunk> responsePayloadTransformer) {
        return aggregatedConnection;
    }
}
