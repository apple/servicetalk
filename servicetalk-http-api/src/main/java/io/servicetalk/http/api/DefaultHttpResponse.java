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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static java.lang.System.lineSeparator;

final class DefaultHttpResponse<T> implements HttpResponse<T> {

    private final HttpResponseMetaData original;
    private final T payloadBody;
    private final HttpHeaders trailers;

    DefaultHttpResponse(final HttpResponseMetaData original,
                        final T payloadBody,
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
    public HttpResponse<T> setVersion(final HttpProtocolVersion version) {
        original.setVersion(version);
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return original.getHeaders();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return original.toString(headerFilter) + lineSeparator() + trailers.toString(headerFilter);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return original.getStatus();
    }

    @Override
    public HttpResponse<T> setStatus(final HttpResponseStatus status) {
        original.setStatus(status);
        return this;
    }

    @Override
    public T getPayloadBody() {
        return payloadBody;
    }

    @Override
    public <R> HttpResponse<R> transformPayloadBody(final Function<T, R> transformer) {
        return new DefaultHttpResponse<>(original, transformer.apply(payloadBody), trailers);
    }

    @Override
    public HttpHeaders getTrailers() {
        return trailers;
    }

    static StreamingHttpResponse<HttpPayloadChunk> toHttpResponse(HttpResponse<HttpPayloadChunk> response) {
        return new DefaultStreamingHttpResponse<>(response.getStatus(), response.getVersion(), response.getHeaders(),
                // We can not simply write "request" here as the encoder will see two metadata objects,
                // one created by splice and the next the chunk itself.
                just(newLastPayloadChunk(response.getPayloadBody().getContent(), response.getTrailers())));
    }

    static Single<HttpResponse<HttpPayloadChunk>> from(StreamingHttpResponse<HttpPayloadChunk> original,
                                                       BufferAllocator allocator) {
        final Single<LastHttpPayloadChunk> reduce = aggregateChunks(original.getPayloadBody(), allocator);
        return reduce.map(payload -> new DefaultHttpResponse<>(original, payload, payload.getTrailers()));
    }
}
