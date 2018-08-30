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
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static java.lang.System.lineSeparator;

final class DefaultHttpRequest<T> implements HttpRequest<T> {
    private final HttpRequestMetaData original;
    private final T payloadBody;
    private final HttpHeaders trailers;

    DefaultHttpRequest(final HttpRequestMetaData original, final T payloadBody, final HttpHeaders trailers) {
        this.original = original;
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public T getPayloadBody() {
        return payloadBody;
    }

    @Override
    public HttpHeaders getTrailers() {
        return trailers;
    }

    @Override
    public <R> HttpRequest<R> transformPayloadBody(final Function<T, R> transformer) {
        return new DefaultHttpRequest<>(original, transformer.apply(payloadBody), trailers);
    }

    @Override
    public HttpRequest<T> setRawPath(final String path) {
        original.setRawPath(path);
        return this;
    }

    @Override
    public HttpRequest<T> setPath(final String path) {
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
    public HttpRequest<T> setRawQuery(final String query) {
        original.setRawQuery(query);
        return this;
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return original.getVersion();
    }

    @Override
    public HttpRequest<T> setVersion(final HttpProtocolVersion version) {
        original.setVersion(version);
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return original.getHeaders();
    }

    @Override
    public String toString(
            final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return original.toString(headerFilter) + lineSeparator() + trailers.toString(headerFilter);
    }

    @Override
    public HttpRequestMethod getMethod() {
        return original.getMethod();
    }

    @Override
    public HttpRequest<T> setMethod(final HttpRequestMethod method) {
        original.setMethod(method);
        return this;
    }

    @Override
    public String getRequestTarget() {
        return original.getRequestTarget();
    }

    @Override
    public HttpRequest<T> setRequestTarget(final String requestTarget) {
        original.setRequestTarget(requestTarget);
        return this;
    }

    @Nullable
    @Override
    public String getScheme() {
        return original.getScheme();
    }

    @Nullable
    @Override
    public String getUserInfo() {
        return original.getUserInfo();
    }

    @Nullable
    @Override
    public String getHost() {
        return original.getHost();
    }

    @Override
    public int getPort() {
        return original.getPort();
    }

    @Override
    public String getRawPath() {
        return original.getRawPath();
    }

    @Override
    public String getPath() {
        return original.getPath();
    }

    @Nullable
    @Override
    public String getEffectiveHost() {
        return original.getEffectiveHost();
    }

    @Override
    public int getEffectivePort() {
        return original.getEffectivePort();
    }

    static StreamingHttpRequest<HttpPayloadChunk> toHttpRequest(final HttpRequest<HttpPayloadChunk> request) {
        return new DefaultStreamingHttpRequest<>(request.getMethod(), request.getRequestTarget(), request.getVersion(),
                // We can not simply write "request" here as the encoder will see two metadata objects,
                // one created by splice and the next the chunk itself.
                just(newLastPayloadChunk(request.getPayloadBody().getContent(), request.getTrailers())),
                request.getHeaders());
    }

    static Single<HttpRequest<HttpPayloadChunk>> from(final StreamingHttpRequest<HttpPayloadChunk> original,
                                                      final BufferAllocator allocator) {
        final Single<LastHttpPayloadChunk> reduce = aggregateChunks(original.getPayloadBody(), allocator);
        return reduce.map(payload -> new DefaultHttpRequest<>(original, payload, payload.getTrailers()));
    }
}
