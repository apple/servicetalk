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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;

import java.util.function.BiFunction;

import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;

final class DefaultAggregatedUpgradableHttpResponse implements AggregatedUpgradableHttpResponse {

    private final UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> original;
    private final Buffer payloadBody;
    private final HttpHeaders trailers;

    private DefaultAggregatedUpgradableHttpResponse(final UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> original,
                                                    final Buffer payloadBody, final HttpHeaders trailers) {
        this.original = original;
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public AggregatedReservedHttpConnection getHttpConnection(final boolean releaseReturnsToClient) {
        return new AggregatedReservedHttpConnection(original.getHttpConnection(releaseReturnsToClient));
    }

    @Override
    public AggregatedUpgradableHttpResponse setVersion(final HttpProtocolVersion version) {
        original.setVersion(version);
        return this;
    }

    @Override
    public AggregatedUpgradableHttpResponse setStatus(final HttpResponseStatus status) {
        original.setStatus(status);
        return this;
    }

    @Override
    public Buffer getPayloadBody() {
        return payloadBody;
    }

    @Override
    public HttpHeaders getTrailers() {
        return trailers;
    }

    @Override
    public AggregatedUpgradableHttpResponse duplicate() {
        return new DefaultAggregatedUpgradableHttpResponse(original, payloadBody.duplicate(), trailers);
    }

    @Override
    public AggregatedUpgradableHttpResponse replace(final Buffer content) {
        return new DefaultAggregatedUpgradableHttpResponse(original, content, trailers);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return original.getStatus();
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return original.getVersion();
    }

    @Override
    public HttpHeaders getHeaders() {
        return original.getHeaders();
    }

    @Override
    public String toString() {
        return original.toString();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return original.toString(headerFilter);
    }

    static Single<AggregatedUpgradableHttpResponse> from(UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk> original,
                                         BufferAllocator allocator) {
        final Single<LastHttpPayloadChunk> reduce = aggregateChunks(original.getPayloadBody(), allocator);
        return reduce.map(payload -> new DefaultAggregatedUpgradableHttpResponse(original, payload.getContent(),
                payload.getTrailers()));
    }
}
