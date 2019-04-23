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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.BlockingIterables;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferFilterIterable;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBuffersAndTrailersIterable;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpObjectsAndTrailersIterable;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.BlockingIterables.from;
import static io.servicetalk.http.api.HttpDataSourceTranformations.consumeOldPayloadBody;
import static io.servicetalk.http.api.HttpDataSourceTranformations.consumeOldPayloadBodySerialized;
import static java.util.Objects.requireNonNull;

class DefaultBlockingStreamingHttpResponse<P> extends DefaultHttpResponseMetaData implements
                                                                                  BlockingStreamingHttpResponse {
    final BlockingIterable<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;
    final boolean aggregated;

    DefaultBlockingStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                         final HttpHeaders headers, final HttpHeaders initialTrailers,
                                         final BufferAllocator allocator, BlockingIterable<P> payloadBody,
                                         final boolean aggregated) {
        this(status, version, headers, succeeded(initialTrailers), allocator, payloadBody, aggregated);
    }

    /**
     * Create a new instance.
     * @param status The {@link HttpResponseStatus}.
     * @param version The {@link HttpProtocolVersion}.
     * @param headers The initial {@link HttpHeaders}.
     * @param trailersSingle The {@link Single} <strong>must</strong> support multiple subscribes, and it is assumed to
     * provide the original data if re-used over transformation operations.
     * @param allocator The {@link BufferAllocator} to use for serialization (if required).
     * @param payloadBody A {@link BlockingIterable} that provide only the payload body.
     * The trailers <strong>must</strong> not be included, and instead are represented by {@code trailersSingle}.
     * @param aggregated The type of API this response was originally created as.
     */
    DefaultBlockingStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                         final HttpHeaders headers, final Single<HttpHeaders> trailersSingle,
                                         final BufferAllocator allocator, final BlockingIterable<P> payloadBody,
                                         final boolean aggregated) {
        super(status, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailersSingle = requireNonNull(trailersSingle);
        this.aggregated = aggregated;
    }

    DefaultBlockingStreamingHttpResponse(final DefaultHttpResponseMetaData oldRequest,
                                         final BufferAllocator allocator,
                                         final BlockingIterable<P> payloadBody,
                                         final Single<HttpHeaders> trailersSingle,
                                         final boolean aggregated) {
        super(oldRequest);
        this.allocator = allocator;
        this.payloadBody = payloadBody;
        this.trailersSingle = trailersSingle;
        this.aggregated = aggregated;
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return new HttpBufferFilterIterable(payloadBody);
    }

    @Override
    public final BlockingStreamingHttpResponse payloadBody(final Iterable<Buffer> payloadBody) {
        return transformPayloadBody(consumeOldPayloadBody(BlockingIterables.from(payloadBody)));
    }

    @Override
    public final BlockingStreamingHttpResponse payloadBody(final CloseableIterable<Buffer> payloadBody) {
        return transformPayloadBody(consumeOldPayloadBody(from(payloadBody)));
    }

    @Override
    public final <T> BlockingStreamingHttpResponse payloadBody(final Iterable<T> payloadBody,
                                                               final HttpSerializer<T> serializer) {
        return transformPayloadBody(consumeOldPayloadBodySerialized(BlockingIterables.from(payloadBody)), serializer);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse payloadBody(final CloseableIterable<T> payloadBody,
                                                               final HttpSerializer<T> serializer) {
        return transformPayloadBody(consumeOldPayloadBodySerialized(from(payloadBody)), serializer);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse transformPayloadBody(
            final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
            final HttpSerializer<T> serializer) {
        return new BufferBlockingStreamingHttpResponse(this, allocator,
                serializer.serialize(headers(), transformer.apply(payloadBody()), allocator),
                trailersSingle, aggregated);
    }

    @Override
    public final BlockingStreamingHttpResponse transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        return new BufferBlockingStreamingHttpResponse(this, allocator, transformer.apply(payloadBody()),
                trailersSingle, aggregated);
    }

    @Override
    public final BlockingStreamingHttpResponse transformRawPayloadBody(
            final UnaryOperator<BlockingIterable<?>> transformer) {
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator, transformer.apply(payloadBody),
                trailersSingle, aggregated);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse transform(final Supplier<T> stateSupplier,
                                                       final BiFunction<Buffer, T, Buffer> transformer,
                                                       final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferBlockingStreamingHttpResponse(this, allocator,
                new HttpBuffersAndTrailersIterable<>(payloadBody(), stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                fromSource(outTrailersSingle), aggregated);
    }

    @Override
    public final <T> BlockingStreamingHttpResponse transformRaw(final Supplier<T> stateSupplier,
                                                          final BiFunction<Object, T, ?> transformer,
                                                          final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator,
                new HttpObjectsAndTrailersIterable<>(payloadBody, stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                fromSource(outTrailersSingle), aggregated);
    }

    @Override
    public final Single<HttpResponse> toResponse() {
        return toStreamingResponse().toResponse();
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        return new DefaultStreamingHttpResponse<>(status(), version(), headers(), trailersSingle, allocator,
                fromIterable(payloadBody), aggregated);
    }

    @Override
    public final BlockingStreamingHttpResponse version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public final BlockingStreamingHttpResponse status(final HttpResponseStatus status) {
        super.status(status);
        return this;
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

        final DefaultBlockingStreamingHttpResponse<?> that = (DefaultBlockingStreamingHttpResponse<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
