/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.encoding.api.ContentCodec;

import java.io.InputStream;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.http.api.BlockingStreamingHttpMessageBodyUtils.newMessageBody;

final class DefaultBlockingStreamingHttpResponse extends AbstractDelegatingHttpResponse
        implements BlockingStreamingHttpResponse {

    @Nullable
    private InputStream inputStream;

    DefaultBlockingStreamingHttpResponse(final DefaultStreamingHttpResponse original) {
        super(original);
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return original.payloadBody().toIterable();
    }

    @Override
    public InputStream payloadBodyInputStream() {
        if (inputStream == null) {
            inputStream = BlockingStreamingHttpResponse.super.payloadBodyInputStream();
        }
        return inputStream;
    }

    @Override
    public <T> BlockingIterable<T> payloadBody(final HttpStreamingDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody(), original.payloadHolder().allocator());
    }

    @Override
    public HttpMessageBodyIterable<Buffer> messageBody() {
        return newMessageBody(original.messageBody().toIterable());
    }

    @Override
    public <T> HttpMessageBodyIterable<T> messageBody(final HttpStreamingDeserializer<T> deserializer) {
        return newMessageBody(original.messageBody().toIterable(), headers(), deserializer,
                original.payloadHolder().allocator());
    }

    @Override
    public BlockingStreamingHttpResponse payloadBody(final Iterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse payloadBody(final InputStream payloadBody) {
        original.payloadBody(fromInputStream(payloadBody)
                .map(bytes -> original.payloadHolder().allocator().wrap(bytes)));
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse messageBody(final HttpMessageBodyIterable<Buffer> messageBody) {
        original.payloadHolder().messageBody(defer(() -> {
            HttpMessageBodyIterator<Buffer> body = messageBody.iterator();
            return fromIterable(() -> body)
                    .cast(Object.class)
                    .concat(defer(() -> fromFilterNull(body.trailers())).shareContextOnSubscribe())
                    .shareContextOnSubscribe();
        }));
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse messageBody(final HttpMessageBodyIterable<T> messageBody,
                                                         final HttpStreamingSerializer<T> serializer) {
        original.payloadHolder().messageBody(defer(() -> {
            HttpMessageBodyIterator<T> body = messageBody.iterator();
            return from(serializer.serialize(headers(), () -> body, original.payloadHolder().allocator()))
                    .cast(Object.class)
                    .concat(defer(() -> fromFilterNull(body.trailers())).shareContextOnSubscribe())
                    .shareContextOnSubscribe();
        }));
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpResponse payloadBody(final Iterable<T> payloadBody,
                                                         final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse payloadBody(final Iterable<T> payloadBody,
                                                         final HttpStreamingSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpResponse transformPayloadBody(final Function<BlockingIterable<Buffer>,
            BlockingIterable<T>> transformer, final HttpSerializer<T> serializer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())),
                serializer);
        return this;
    }

    @Deprecated
    @Override
    public BlockingStreamingHttpResponse transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())));
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpResponse transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        original.transform(trailersTransformer);
        return this;
    }

    @Override
    public Single<HttpResponse> toResponse() {
        return toStreamingResponse().toResponse();
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        return original;
    }

    @Override
    public BlockingStreamingHttpResponse version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Deprecated
    @Override
    public BlockingStreamingHttpResponse encoding(final ContentCodec encoding) {
        original.encoding(encoding);
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse context(final ContextMap context) {
        original.context(context);
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse status(final HttpResponseStatus status) {
        original.status(status);
        return this;
    }

    static <T> Publisher<T> fromFilterNull(@Nullable T value) {
        return value == null ? empty() : from(value);
    }
}
