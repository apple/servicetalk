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
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferTrailersSpliceOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpObjectTrailersSpliceOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpRawBuffersAndTrailersOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpRawObjectsAndTrailersOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpTransportBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

final class TransportStreamingHttpResponse extends DefaultHttpResponseMetaData implements StreamingHttpResponse {
    final Publisher<Object> payloadAndTrailers;
    final BufferAllocator allocator;

    TransportStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                   final HttpHeaders headers, final BufferAllocator allocator,
                                   final Publisher<Object> payloadAndTrailers) {
        super(status, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadAndTrailers = requireNonNull(payloadAndTrailers);
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

    @Override
    public Publisher<Buffer> payloadBody() {
        return payloadAndTrailers.liftSynchronous(HttpTransportBufferFilterOperator.INSTANCE);
    }

    @Override
    public Publisher<Object> payloadBodyAndTrailers() {
        return payloadAndTrailers;
    }

    @Override
    public StreamingHttpResponse payloadBody(final Publisher<Buffer> payloadBody) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator,
                payloadBody.liftSynchronous(new BridgeFlowControlAndDiscardOperator(payloadAndTrailers.liftSynchronous(
                        new HttpBufferTrailersSpliceOperator(outTrailersSingle)))),
                outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpResponse payloadBody(final Publisher<T> payloadBody,
                                                 final HttpSerializer<T> serializer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator, serializer.serialize(headers(),
                payloadBody.liftSynchronous(new SerializeBridgeFlowControlAndDiscardOperator<>(
                        payloadAndTrailers.liftSynchronous(new HttpBufferTrailersSpliceOperator(outTrailersSingle)))),
                allocator),
                outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpResponse transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                          final HttpSerializer<T> serializer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator, serializer.serialize(headers(),
                transformer.apply(payloadAndTrailers.liftSynchronous(new HttpBufferTrailersSpliceOperator(
                        outTrailersSingle))), allocator),
                outTrailersSingle);
    }

    @Override
    public StreamingHttpResponse transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator, transformer.apply(payloadAndTrailers.liftSynchronous(
                new HttpBufferTrailersSpliceOperator(outTrailersSingle))), outTrailersSingle);
    }

    @Override
    public StreamingHttpResponse transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultStreamingHttpResponse<>(this, allocator, transformer.apply(payloadAndTrailers.liftSynchronous(
                new HttpObjectTrailersSpliceOperator(outTrailersSingle))), outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpResponse transform(final Supplier<T> stateSupplier,
                                               final BiFunction<Buffer, T, Buffer> transformer,
                                               final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpResponse(this, allocator, payloadAndTrailers.liftSynchronous(
                new HttpRawBuffersAndTrailersOperator<>(stateSupplier, transformer,
                        trailersTransformer, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpResponse transformRaw(final Supplier<T> stateSupplier,
                                                  final BiFunction<Object, T, ?> transformer,
                                                  final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultStreamingHttpResponse<>(this, allocator, payloadAndTrailers.liftSynchronous(
                new HttpRawObjectsAndTrailersOperator<>(stateSupplier, transformer,
                        trailersTransformer, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public Single<HttpResponse> toResponse() {
        return aggregatePayloadAndTrailers(payloadAndTrailers, allocator).map(pair -> {
            assert pair.trailers != null;
            return new BufferHttpResponse(status(), version(), headers(), pair.trailers, pair.compositeBuffer,
                    allocator);
        });
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator, payloadAndTrailers.liftSynchronous(
                new HttpObjectTrailersSpliceOperator(outTrailersSingle)).toIterable(), outTrailersSingle);
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

        final TransportStreamingHttpResponse that = (TransportStreamingHttpResponse) o;

        return payloadAndTrailers.equals(that.payloadAndTrailers);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadAndTrailers.hashCode();
    }
}
