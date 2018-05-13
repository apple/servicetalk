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

final class DefaultFullHttpRequest implements FullHttpRequest {

    private final HttpRequestMetaData original;
    private final Buffer payloadBody;
    private final HttpHeaders trailers;

    DefaultFullHttpRequest(HttpRequestMetaData original, Buffer payloadBody, HttpHeaders trailers) {
        this.original = original;
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public FullHttpRequest setRawPath(final String path) {
        original.setRawPath(path);
        return this;
    }

    @Override
    public FullHttpRequest setPath(final String path) {
        original.setPath(path);
        return this;
    }

    @Override
    public HttpQuery parseQuery() {
        return original.parseQuery();
    }

    @Override
    public String getRawQuery() {
        return original.getRawQuery();
    }

    @Override
    public FullHttpRequest setRawQuery(final String query) {
        original.setRawQuery(query);
        return this;
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return original.getVersion();
    }

    @Override
    public FullHttpRequest setVersion(final HttpProtocolVersion version) {
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
    public HttpRequestMethod getMethod() {
        return original.getMethod();
    }

    @Override
    public FullHttpRequest setMethod(final HttpRequestMethod method) {
        original.setMethod(method);
        return this;
    }

    @Override
    public String getRequestTarget() {
        return original.getRequestTarget();
    }

    @Override
    public FullHttpRequest setRequestTarget(final String requestTarget) {
        original.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public String getRawPath() {
        return original.getRawPath();
    }

    @Override
    public String getPath() {
        return original.getPath();
    }

    @Override
    public HttpHeaders getTrailers() {
        return trailers;
    }

    @Override
    public Buffer getPayloadBody() {
        return payloadBody;
    }

    @Override
    public FullHttpRequest duplicate() {
        return new DefaultFullHttpRequest(original, payloadBody.duplicate(), trailers);
    }

    @Override
    public FullHttpRequest replace(final Buffer content) {
        return new DefaultFullHttpRequest(original, content, trailers);
    }

    static HttpRequest<HttpPayloadChunk> toHttpRequest(FullHttpRequest request) {
        return new DefaultHttpRequest<>(request.getMethod(), request.getRequestTarget(), request.getVersion(),
                // We can not simply write "request" here as the encoder will see two metadata objects,
                // one created by splice and the next the chunk itself.
                just(newLastPayloadChunk(request.getPayloadBody(), request.getTrailers())), request.getHeaders());
    }

    static Single<FullHttpRequest> from(HttpRequest<HttpPayloadChunk> original, BufferAllocator allocator) {
        final Single<LastHttpPayloadChunk> reduce = aggregateChunks(original.getPayloadBody(), allocator);
        return reduce.map(payload -> new DefaultFullHttpRequest(original, payload.getContent(), payload.getTrailers()));
    }
}
