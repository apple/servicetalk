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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

class DefaultStreamingHttpRequest extends DefaultHttpRequestMetaData implements StreamingHttpRequest, PayloadInfo {

    private final StreamingHttpPayloadHolder payloadHolder;

    DefaultStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                final HttpProtocolVersion version, final HttpHeaders headers,
                                final BufferAllocator allocator, @Nullable final Publisher payloadBody,
                                final DefaultPayloadInfo payloadInfo,
                                final HttpHeadersFactory headersFactory) {
        super(method, requestTarget, version, headers);
        payloadHolder = new StreamingHttpPayloadHolder(headers, allocator, payloadBody, payloadInfo, headersFactory);
    }

    @Override
    public final StreamingHttpRequest version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public final StreamingHttpRequest method(final HttpRequestMethod method) {
        super.method(method);
        return this;
    }

    @Override
    public final StreamingHttpRequest requestTarget(final String requestTarget) {
        super.requestTarget(requestTarget);
        return this;
    }

    @Override
    public final StreamingHttpRequest path(final String path) {
        super.path(path);
        return this;
    }

    @Override
    public final StreamingHttpRequest appendPathSegments(final String... segments) {
        super.appendPathSegments(segments);
        return this;
    }

    @Override
    public final StreamingHttpRequest rawPath(final String path) {
        super.rawPath(path);
        return this;
    }

    @Override
    public final StreamingHttpRequest rawQuery(final String query) {
        super.rawQuery(query);
        return this;
    }

    @Override
    public final StreamingHttpRequest addQueryParameter(String key, String value) {
        super.addQueryParameter(key, value);
        return this;
    }

    @Override
    public final StreamingHttpRequest addQueryParameters(String key, Iterable<String> values) {
        super.addQueryParameters(key, values);
        return this;
    }

    @Override
    public final StreamingHttpRequest addQueryParameters(String key, String... values) {
        super.addQueryParameters(key, values);
        return this;
    }

    @Override
    public final StreamingHttpRequest setQueryParameter(String key, String value) {
        super.setQueryParameter(key, value);
        return this;
    }

    @Override
    public final StreamingHttpRequest setQueryParameters(String key, Iterable<String> values) {
        super.setQueryParameters(key, values);
        return this;
    }

    @Override
    public final StreamingHttpRequest setQueryParameters(String key, String... values) {
        super.setQueryParameters(key, values);
        return this;
    }

    @Override
    public Publisher<Buffer> payloadBody() {
        return payloadHolder.payloadBody();
    }

    @Override
    public final Publisher<Object> payloadBodyAndTrailers() {
        return payloadHolder.payloadBodyAndTrailers();
    }

    @Override
    public final StreamingHttpRequest payloadBody(final Publisher<Buffer> payloadBody) {
        payloadHolder.payloadBody(payloadBody);
        return this;
    }

    @Override
    public final <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody,
                                                      final HttpSerializer<T> serializer) {
        payloadHolder.payloadBody(payloadBody, serializer);
        return this;
    }

    @Override
    public final <T> StreamingHttpRequest transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                               HttpSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(transformer, serializer);
        return this;
    }

    @Override
    public final StreamingHttpRequest transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        payloadHolder.transformPayloadBody(transformer);
        return this;
    }

    @Override
    public final StreamingHttpRequest transformRawPayloadBody(UnaryOperator<Publisher<?>> transformer) {
        payloadHolder.transformRawPayloadBody(transformer);
        return this;
    }

    @Override
    public final <T> StreamingHttpRequest transform(Supplier<T> stateSupplier,
                                                    BiFunction<Buffer, T, Buffer> transformer,
                                                    BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        payloadHolder.transform(stateSupplier, transformer, trailersTransformer);
        return this;
    }

    @Override
    public final <T> StreamingHttpRequest transformRaw(Supplier<T> stateSupplier,
                                                       BiFunction<Object, T, ?> transformer,
                                                       BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        payloadHolder.transformRaw(stateSupplier, transformer, trailersTransformer);
        return this;
    }

    @Override
    public final Single<HttpRequest> toRequest() {
        return payloadHolder.aggregate()
                .map(pair -> new DefaultHttpRequest(this, pair.compositeBuffer, pair.trailers));
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return new DefaultBlockingStreamingHttpRequest(this);
    }

    @Override
    public boolean safeToAggregate() {
        return payloadHolder.safeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return payloadHolder.mayHaveTrailers();
    }

    @Override
    public boolean onlyEmitsBuffer() {
        return payloadHolder.onlyEmitsBuffer();
    }

    StreamingHttpPayloadHolder payloadHolder() {
        return payloadHolder;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultStreamingHttpRequest that = (DefaultStreamingHttpRequest) o;

        return payloadHolder.equals(that.payloadHolder);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + payloadHolder.hashCode();
        return result;
    }
}
