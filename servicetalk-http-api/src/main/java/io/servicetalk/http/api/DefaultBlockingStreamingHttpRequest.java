/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.api.Single;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;

class DefaultBlockingStreamingHttpRequest implements BlockingStreamingHttpRequest, PayloadInfo {

    private final DefaultStreamingHttpRequest original;

    DefaultBlockingStreamingHttpRequest(final DefaultStreamingHttpRequest original) {
        this.original = original;
    }

    @Override
    public HttpProtocolVersion version() {
        return original.version();
    }

    @Override
    public final BlockingStreamingHttpRequest version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return original.headers();
    }

    @Override
    public HttpRequestMethod method() {
        return original.method();
    }

    @Override
    public final BlockingStreamingHttpRequest method(final HttpRequestMethod method) {
        original.method(method);
        return this;
    }

    @Override
    public String requestTarget() {
        return original.requestTarget();
    }

    @Override
    public final BlockingStreamingHttpRequest requestTarget(final String requestTarget) {
        original.requestTarget(requestTarget);
        return this;
    }

    @Nullable
    @Override
    public String scheme() {
        return original.scheme();
    }

    @Nullable
    @Override
    public String userInfo() {
        return original.userInfo();
    }

    @Nullable
    @Override
    public String host() {
        return original.host();
    }

    @Override
    public int port() {
        return original.port();
    }

    @Override
    public String rawPath() {
        return original.rawPath();
    }

    @Override
    public String path() {
        return original.path();
    }

    @Override
    public final BlockingStreamingHttpRequest path(final String path) {
        original.path(path);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest appendPathSegments(final String... segments) {
        original.appendPathSegments(segments);
        return this;
    }

    @Override
    public String rawQuery() {
        return original.rawQuery();
    }

    @Override
    public final BlockingStreamingHttpRequest rawPath(final String path) {
        original.rawPath(path);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest rawQuery(final String query) {
        original.rawQuery(query);
        return this;
    }

    @Nullable
    @Override
    public String queryParameter(final String key) {
        return original.queryParameter(key);
    }

    @Override
    public Iterable<Map.Entry<String, String>> queryParameters() {
        return original.queryParameters();
    }

    @Override
    public Iterator<String> queryParameters(final String key) {
        return original.queryParameters(key);
    }

    @Override
    public Set<String> queryParametersKeys() {
        return original.queryParametersKeys();
    }

    @Override
    public boolean hasQueryParameter(final String key, final String value) {
        return original.hasQueryParameter(key, value);
    }

    @Override
    public int queryParametersSize() {
        return original.queryParametersSize();
    }

    @Override
    public final BlockingStreamingHttpRequest addQueryParameter(String key, String value) {
        original.addQueryParameter(key, value);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest addQueryParameters(String key, Iterable<String> values) {
        original.addQueryParameters(key, values);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest addQueryParameters(String key, String... values) {
        original.addQueryParameters(key, values);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setQueryParameter(String key, String value) {
        original.setQueryParameter(key, value);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setQueryParameters(String key, Iterable<String> values) {
        original.setQueryParameters(key, values);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setQueryParameters(String key, String... values) {
        original.setQueryParameters(key, values);
        return this;
    }

    @Override
    public boolean removeQueryParameters(final String key) {
        return original.removeQueryParameters(key);
    }

    @Override
    public boolean removeQueryParameters(final String key, final String value) {
        return original.removeQueryParameters(key, value);
    }

    @Nullable
    @Override
    public String effectiveHost() {
        return original.effectiveHost();
    }

    @Override
    public int effectivePort() {
        return original.effectivePort();
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return original.payloadBody().toIterable();
    }

    @Override
    public final BlockingStreamingHttpRequest payloadBody(final Iterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest payloadBody(final CloseableIterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public final <T> BlockingStreamingHttpRequest payloadBody(final Iterable<T> payloadBody,
                                                              final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Override
    public final <T> BlockingStreamingHttpRequest payloadBody(final CloseableIterable<T> payloadBody,
                                                              final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transformPayloadBody(
            final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
            final HttpSerializer<T> serializer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())),
                serializer);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())));
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest transformRawPayloadBody(
            final UnaryOperator<BlockingIterable<?>> transformer) {
        original.transformRawPayloadBody(bufferPublisher ->
                fromIterable(transformer.apply(bufferPublisher.toIterable())));
        return this;
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transform(
            final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer,
            final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        original.transform(stateSupplier, transformer, trailersTransformer);
        return this;
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transformRaw(
            final Supplier<T> stateSupplier, final BiFunction<Object, T, ?> transformer,
            final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        original.transformRaw(stateSupplier, transformer, trailersTransformer);
        return this;
    }

    @Override
    public final Single<HttpRequest> toRequest() {
        return original.toRequest();
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return original;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultBlockingStreamingHttpRequest that = (DefaultBlockingStreamingHttpRequest) o;

        return original.equals(that.original);
    }

    @Override
    public int hashCode() {
        return original.hashCode();
    }

    @Override
    public boolean safeToAggregate() {
        return original.safeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return original.mayHaveTrailers();
    }

    @Override
    public boolean onlyEmitsBuffer() {
        return original.onlyEmitsBuffer();
    }
}
