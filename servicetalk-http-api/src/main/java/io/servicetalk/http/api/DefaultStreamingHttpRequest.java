/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;

import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

final class DefaultStreamingHttpRequest extends DefaultHttpRequestMetaData
        implements StreamingHttpRequest, PayloadInfo {

    private final StreamingHttpPayloadHolder payloadHolder;

    DefaultStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                final HttpProtocolVersion version, final HttpHeaders headers,
                                @Nullable final ContentCodec encoding, @Nullable final BufferEncoder encoder,
                                final BufferAllocator allocator, @Nullable final Publisher<?> payloadBody,
                                final DefaultPayloadInfo payloadInfo, final HttpHeadersFactory headersFactory) {
        super(method, requestTarget, version, headers);
        if (encoding != null) {
            encoding(encoding);
        }
        payloadHolder = new StreamingHttpPayloadHolder(headers, allocator, payloadBody, payloadInfo, headersFactory);
        this.contentEncoding(encoder);
    }

    @Override
    public StreamingHttpRequest version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Deprecated
    @Override
    public StreamingHttpRequest encoding(final ContentCodec encoding) {
        super.encoding(encoding);
        return this;
    }

    @Override
    public StreamingHttpRequest contentEncoding(@Nullable final BufferEncoder encoder) {
        super.contentEncoding(encoder);
        return this;
    }

    @Override
    public StreamingHttpRequest method(final HttpRequestMethod method) {
        super.method(method);
        return this;
    }

    @Override
    public StreamingHttpRequest requestTarget(final String requestTarget) {
        super.requestTarget(requestTarget);
        return this;
    }

    @Override
    public StreamingHttpRequest requestTarget(final String requestTarget, Charset encoding) {
        super.requestTarget(requestTarget, encoding);
        return this;
    }

    @Override
    public StreamingHttpRequest path(final String path) {
        super.path(path);
        return this;
    }

    @Override
    public StreamingHttpRequest appendPathSegments(final String... segments) {
        super.appendPathSegments(segments);
        return this;
    }

    @Override
    public StreamingHttpRequest rawPath(final String path) {
        super.rawPath(path);
        return this;
    }

    @Override
    public StreamingHttpRequest rawQuery(@Nullable final String query) {
        super.rawQuery(query);
        return this;
    }

    @Override
    public StreamingHttpRequest query(@Nullable final String query) {
        super.query(query);
        return this;
    }

    @Override
    public StreamingHttpRequest addQueryParameter(String key, String value) {
        super.addQueryParameter(key, value);
        return this;
    }

    @Override
    public StreamingHttpRequest addQueryParameters(String key, Iterable<String> values) {
        super.addQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest addQueryParameters(String key, String... values) {
        super.addQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest setQueryParameter(String key, String value) {
        super.setQueryParameter(key, value);
        return this;
    }

    @Override
    public StreamingHttpRequest setQueryParameters(String key, Iterable<String> values) {
        super.setQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest setQueryParameters(String key, String... values) {
        super.setQueryParameters(key, values);
        return this;
    }

    @Override
    public Publisher<Buffer> payloadBody() {
        return payloadHolder.payloadBody();
    }

    @Override
    public <T> Publisher<T> payloadBody(final HttpStreamingDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody(), payloadHolder.allocator());
    }

    @Override
    public Publisher<Object> messageBody() {
        return payloadHolder.messageBody();
    }

    @Override
    public StreamingHttpRequest payloadBody(final Publisher<Buffer> payloadBody) {
        payloadHolder.payloadBody(payloadBody);
        return this;
    }

    @Deprecated
    @Override
    public <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody, final HttpSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(bufPub ->
                serializer.serialize(headers(), payloadBody, payloadHolder.allocator()));
        return this;
    }

    @Override
    public <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody,
                                                final HttpStreamingSerializer<T> serializer) {
        payloadHolder.payloadBody(payloadBody, serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T> StreamingHttpRequest transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                         HttpSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(bufPub ->
                serializer.serialize(headers(), transformer.apply(bufPub), payloadHolder.allocator()));
        return this;
    }

    @Override
    public <T> StreamingHttpRequest transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                         final HttpStreamingSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(transformer, serializer);
        return this;
    }

    @Override
    public <T, R> StreamingHttpRequest transformPayloadBody(final Function<Publisher<T>, Publisher<R>> transformer,
                                                            final HttpStreamingDeserializer<T> deserializer,
                                                            final HttpStreamingSerializer<R> serializer) {
        return transformPayloadBody(bufPub ->
                        transformer.apply(deserializer.deserialize(headers(), bufPub, payloadHolder.allocator())),
                serializer);
    }

    @Override
    public StreamingHttpRequest transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        payloadHolder.transformPayloadBody(transformer);
        return this;
    }

    @Override
    public StreamingHttpRequest transformMessageBody(final UnaryOperator<Publisher<?>> transformer) {
        payloadHolder.transformMessageBody(transformer);
        return this;
    }

    @Override
    public <T> StreamingHttpRequest transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        payloadHolder.transform(trailersTransformer);
        return this;
    }

    @Override
    public <T, S> StreamingHttpRequest transform(final TrailersTransformer<T, S> trailersTransformer,
                                                 final HttpStreamingDeserializer<S> deserializer) {
        payloadHolder.transform(trailersTransformer, deserializer);
        return this;
    }

    @Override
    public Single<HttpRequest> toRequest() {
        return payloadHolder.aggregate().map(pair -> new DefaultHttpRequest(this, pair.payload, pair.trailers));
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return new DefaultBlockingStreamingHttpRequest(this);
    }

    @Override
    public boolean isEmpty() {
        return payloadHolder.isEmpty();
    }

    @Override
    public boolean isSafeToAggregate() {
        return payloadHolder.isSafeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return payloadHolder.mayHaveTrailers();
    }

    @Override
    public boolean isGenericTypeBuffer() {
        return payloadHolder.isGenericTypeBuffer();
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
