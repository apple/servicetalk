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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.http.api.HttpSerializerUtils.HttpBuffersAndTrailersIterable;
import io.servicetalk.http.api.HttpSerializerUtils.HttpObjectsAndTrailersIterable;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Iterables.emptyBlockingIterable;
import static java.util.Objects.requireNonNull;

class DefaultBlockingStreamingHttpResponse<P> extends DefaultHttpResponseMetaData implements
                                                                                  BlockingStreamingHttpResponse {
    final BlockingIterable<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;

    DefaultBlockingStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                         final HttpHeaders headers, final BufferAllocator allocator,
                                         final HttpHeaders initialTrailers) {
        this(status, version, headers, allocator, emptyBlockingIterable(), success(initialTrailers));
    }

    /**
     * Create a new instance.
     * @param status The {@link HttpResponseStatus}.
     * @param version The {@link HttpProtocolVersion}.
     * @param headers The initial {@link HttpHeaders}.
     * @param allocator The {@link BufferAllocator} to use for serialization (if required).
     * @param payloadBody A {@link BlockingIterable} that provide only the payload body.
     * The trailers <strong>must</strong> not be included, and instead are represented by {@code trailersSingle}.
     * @param trailersSingle The {@link Single} <strong>must</strong> support multiple subscribes, and it is assumed to
     * provide the original data if re-used over transformation operations.
     */
    DefaultBlockingStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                         final HttpHeaders headers, final BufferAllocator allocator,
                                         final BlockingIterable<P> payloadBody,
                                         final Single<HttpHeaders> trailersSingle) {
        super(status, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailersSingle = requireNonNull(trailersSingle);
    }

    DefaultBlockingStreamingHttpResponse(final DefaultHttpResponseMetaData oldRequest,
                                         final BufferAllocator allocator,
                                         final BlockingIterable<P> payloadBody,
                                         final Single<HttpHeaders> trailersSingle) {
        super(oldRequest);
        this.allocator = allocator;
        this.payloadBody = payloadBody;
        this.trailersSingle = trailersSingle;
    }

    @Override
    public final <T> BlockingIterable<T> getPayloadBody(final HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(getHeaders(), payloadBody);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse transformPayloadBody(
            final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
            final HttpSerializer<T> serializer) {
        return new BufferBlockingStreamingHttpResponse(this, allocator,
                serializer.serialize(getHeaders(), transformer.apply(getPayloadBody()), allocator),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpResponse transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        return new BufferBlockingStreamingHttpResponse(this, allocator, transformer.apply(getPayloadBody()),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpResponse transformRawPayloadBody(
            final UnaryOperator<BlockingIterable<?>> transformer) {
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator, transformer.apply(payloadBody),
                trailersSingle);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse transform(final Supplier<T> stateSupplier,
                                                       final BiFunction<Buffer, T, Buffer> transformer,
                                                       final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferBlockingStreamingHttpResponse(this, allocator,
                new HttpBuffersAndTrailersIterable<>(getPayloadBody(), stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                outTrailersSingle);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse transformRaw(final Supplier<T> stateSupplier,
                                                          final BiFunction<Object, T, ?> transformer,
                                                          final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator,
                new HttpObjectsAndTrailersIterable<>(payloadBody, stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                outTrailersSingle);
    }

    @Override
    public final Single<HttpResponse> toResponse() {
        return toStreamingResponse().toResponse();
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        return new DefaultStreamingHttpResponse<>(getStatus(), getVersion(), getHeaders(), allocator, from(payloadBody),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpResponse setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public final BlockingStreamingHttpResponse setStatus(final HttpResponseStatus status) {
        super.setStatus(status);
        return this;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultBlockingStreamingHttpResponse<?> that = (DefaultBlockingStreamingHttpResponse<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
