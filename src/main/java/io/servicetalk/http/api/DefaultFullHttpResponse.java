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

import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;

final class DefaultFullHttpResponse implements FullHttpResponse {

    private final HttpResponseMetaData original;
    private final Buffer payloadBody;
    private final HttpHeaders trailers;

    DefaultFullHttpResponse(final HttpResponseMetaData original, final Buffer payloadBody,
                            final HttpHeaders trailers) {
        this.original = original;
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return original.getVersion();
    }

    @Override
    public FullHttpResponse setVersion(final HttpProtocolVersion version) {
        original.setVersion(version);
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return original.getHeaders();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return original.toString(headerFilter);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return original.getStatus();
    }

    @Override
    public FullHttpResponse setStatus(final HttpResponseStatus status) {
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
    public FullHttpResponse duplicate() {
        return new DefaultFullHttpResponse(original, payloadBody.duplicate(), trailers);
    }

    @Override
    public FullHttpResponse replace(final Buffer content) {
        return new DefaultFullHttpResponse(original, content, trailers);
    }

    static HttpResponse<HttpPayloadChunk> toHttpResponse(FullHttpResponse response) {
        return new DefaultHttpResponse<>(response.getStatus(), response.getVersion(), response.getHeaders(),
                // We can not simply write "request" here as the encoder will see two metadata objects,
                // one created by splice and the next the chunk itself.
                just(newLastPayloadChunk(response.getContent(), response.getTrailers())));
    }

    static Single<FullHttpResponse> from(HttpResponse<HttpPayloadChunk> original, BufferAllocator allocator) {
        final Single<LastHttpPayloadChunk> reduce = aggregateChunks(original.getPayloadBody(), allocator);
        return reduce.map(payload -> new DefaultFullHttpResponse(original, payload.getContent(), payload.getTrailers()));
    }
}
