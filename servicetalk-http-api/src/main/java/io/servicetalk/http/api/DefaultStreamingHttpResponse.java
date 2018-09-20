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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.http.api.HttpDataSourceTranformations.BridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpPayloadAndTrailersFromSingleOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

class DefaultStreamingHttpResponse<P> extends DefaultHttpResponseMetaData implements StreamingHttpResponse {
    final Publisher<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;

    DefaultStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                 final HttpHeaders headers, final HttpHeaders initialTrailers,
                                 final BufferAllocator allocator, final Publisher<P> payloadBody) {
        this(status, version, headers, success(initialTrailers), allocator, payloadBody);
    }

    /**
     * Create a new instance.
     * @param status The {@link HttpResponseStatus}.
     * @param version The {@link HttpProtocolVersion}.
     * @param headers The initial {@link HttpHeaders}.
     * @param allocator The {@link BufferAllocator} to use for serialization (if required).
     * @param payloadBody A {@link Publisher} that provide only the payload body. The trailers <strong>must</strong>
     * not be included, and instead are represented by {@code trailersSingle}.
     * @param trailersSingle The {@link Single} <strong>must</strong> support multiple subscribes, and it is assumed to
     * provide the original data if re-used over transformation operations.
     */
    DefaultStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                 final HttpHeaders headers, final Single<HttpHeaders> trailersSingle,
                                 final BufferAllocator allocator, final Publisher<P> payloadBody) {
        super(status, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailersSingle = requireNonNull(trailersSingle);
    }

    DefaultStreamingHttpResponse(final DefaultHttpResponseMetaData oldRequest,
                                 final BufferAllocator allocator,
                                 final Publisher<P> payloadBody,
                                 final Single<HttpHeaders> trailersSingle) {
        super(oldRequest);
        this.allocator = allocator;
        this.payloadBody = payloadBody;
        this.trailersSingle = trailersSingle;
    }

    @Override
    public final StreamingHttpResponse setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public final StreamingHttpResponse setStatus(final HttpResponseStatus status) {
        super.setStatus(status);
        return this;
    }

    @Override
    public Publisher<Buffer> getPayloadBody() {
        return payloadBody.liftSynchronous(HttpBufferFilterOperator.INSTANCE);
    }

    @Override
    public final Publisher<Object> getPayloadBodyAndTrailers() {
        return payloadBody
                .map(payload -> (Object) payload) // down cast to Object
                .concatWith(trailersSingle);
    }

    @Override
    public final StreamingHttpResponse setPayloadBody(final Publisher<Buffer> payloadBody) {
        return new BufferStreamingHttpResponse(this, allocator,
                payloadBody.liftSynchronous(new BridgeFlowControlAndDiscardOperator(getPayloadBody())), trailersSingle);
    }

    @Override
    public final <T> StreamingHttpResponse setPayloadBody(final Publisher<T> payloadBody,
                                                          final HttpSerializer<T> serializer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator, serializer.serialize(getHeaders(),
                    payloadBody.liftSynchronous(new SerializeBridgeFlowControlAndDiscardOperator<>(getPayloadBody())),
                    allocator),
                outTrailersSingle);
    }

    @Override
    public final <T> StreamingHttpResponse transformPayloadBody(
            final Function<Publisher<Buffer>, Publisher<T>> transformer, final HttpSerializer<T> serializer) {
        return new BufferStreamingHttpResponse(this, allocator,
                serializer.serialize(getHeaders(), transformer.apply(getPayloadBody()), allocator),
                trailersSingle);
    }

    @Override
    public final StreamingHttpResponse transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        return new BufferStreamingHttpResponse(this, allocator, transformer.apply(getPayloadBody()), trailersSingle);
    }

    @Override
    public final StreamingHttpResponse transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
        return new DefaultStreamingHttpResponse<>(this, allocator, transformer.apply(payloadBody), trailersSingle);
    }

    @Override
    public final <T> StreamingHttpResponse transform(final Supplier<T> stateSupplier,
                                                     final BiFunction<Buffer, T, Buffer> transformer,
                                                     final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator, getPayloadBody()
                .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                        trailersTrans, trailersSingle, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public final <T> StreamingHttpResponse transformRaw(final Supplier<T> stateSupplier,
                                                        final BiFunction<Object, T, ?> transformer,
                                                        final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultStreamingHttpResponse<>(this, allocator, payloadBody
                .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                        trailersTrans, trailersSingle, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public final Single<HttpResponse> toResponse() {
        return aggregatePayloadAndTrailers(getPayloadBodyAndTrailers(), allocator).map(pair -> {
            assert pair.trailers != null;
            return new BufferHttpResponse(getStatus(), getVersion(), getHeaders(), pair.trailers, pair.compositeBuffer,
                    allocator);
        });
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator, payloadBody.toIterable(), trailersSingle);
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

        final DefaultStreamingHttpResponse<?> that = (DefaultStreamingHttpResponse<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
