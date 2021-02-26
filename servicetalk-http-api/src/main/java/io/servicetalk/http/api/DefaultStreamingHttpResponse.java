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
import io.servicetalk.encoding.api.ContentCodec;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

final class DefaultStreamingHttpResponse extends DefaultHttpResponseMetaData
        implements StreamingHttpResponse, PayloadInfo {

    private final StreamingHttpPayloadHolder payloadHolder;

    DefaultStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                 final HttpHeaders headers, final BufferAllocator allocator,
                                 @Nullable final Publisher<?> payloadBody, final DefaultPayloadInfo payloadInfo,
                                 final HttpHeadersFactory headersFactory) {
        super(status, version, headers);
        payloadHolder = new StreamingHttpPayloadHolder(headers, allocator, payloadBody, payloadInfo, headersFactory);
    }

    @Override
    public StreamingHttpResponse version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public StreamingHttpResponse status(final HttpResponseStatus status) {
        super.status(status);
        return this;
    }

    @Deprecated
    @Override
    public StreamingHttpResponse encoding(final ContentCodec encoding) {
        super.encoding(encoding);
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
    public StreamingHttpResponse payloadBody(final Publisher<Buffer> payloadBody) {
        payloadHolder.payloadBody(payloadBody);
        return this;
    }

    @Deprecated
    @Override
    public <T> StreamingHttpResponse payloadBody(final Publisher<T> payloadBody,
                                                 final HttpSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(bufPub ->
                serializer.serialize(headers(), payloadBody, payloadHolder.allocator()));
        return this;
    }

    @Override
    public <T> StreamingHttpResponse payloadBody(final Publisher<T> payloadBody,
                                                 final HttpStreamingSerializer<T> serializer) {
        payloadHolder.payloadBody(payloadBody, serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T> StreamingHttpResponse transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                          HttpSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(bufPub ->
                serializer.serialize(headers(), transformer.apply(bufPub), payloadHolder.allocator()));
        return this;
    }

    @Override
    public <T> StreamingHttpResponse transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                          final HttpStreamingSerializer<T> serializer) {
        payloadHolder.transformPayloadBody(transformer, serializer);
        return this;
    }

    @Override
    public <T, R> StreamingHttpResponse transformPayloadBody(final Function<Publisher<T>, Publisher<R>> transformer,
                                                             final HttpStreamingDeserializer<T> deserializer,
                                                             final HttpStreamingSerializer<R> serializer) {
        return transformPayloadBody(bufPub ->
                        transformer.apply(deserializer.deserialize(headers(), bufPub, payloadHolder.allocator())),
                serializer);
    }

    @Override
    public StreamingHttpResponse transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        payloadHolder.transformPayloadBody(transformer);
        return this;
    }

    @Override
    public StreamingHttpResponse transformMessageBody(final UnaryOperator<Publisher<?>> transformer) {
        payloadHolder.transformMessageBody(transformer);
        return this;
    }

    @Override
    public <T> StreamingHttpResponse transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        payloadHolder.transform(trailersTransformer);
        return this;
    }

    @Override
    public <T, S> StreamingHttpResponse transform(final TrailersTransformer<T, S> trailersTransformer,
                                                  final HttpStreamingDeserializer<S> serializer) {
        payloadHolder.transform(trailersTransformer, serializer);
        return this;
    }

    @Override
    public Single<HttpResponse> toResponse() {
        return payloadHolder.aggregate().map(pair -> new DefaultHttpResponse(this, pair.payload, pair.trailers));
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return new DefaultBlockingStreamingHttpResponse(this);
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

        final DefaultStreamingHttpResponse that = (DefaultStreamingHttpResponse) o;

        return payloadHolder.equals(that.payloadHolder);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + payloadHolder.hashCode();
        return result;
    }
}
