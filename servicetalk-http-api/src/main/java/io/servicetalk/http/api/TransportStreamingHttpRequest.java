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
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpTransportBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

final class TransportStreamingHttpRequest extends DefaultHttpRequestMetaData implements StreamingHttpRequest {
    final Publisher<Object> payloadAndTrailers;
    final BufferAllocator allocator;

    TransportStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                  final HttpProtocolVersion version, final HttpHeaders headers,
                                  final BufferAllocator allocator, final Publisher<Object> payloadAndTrailers) {
        super(method, requestTarget, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadAndTrailers = requireNonNull(payloadAndTrailers);
    }

    @Override
    public StreamingHttpRequest setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public StreamingHttpRequest setMethod(final HttpRequestMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public StreamingHttpRequest setRequestTarget(final String requestTarget) {
        super.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public StreamingHttpRequest setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public StreamingHttpRequest setRawPath(final String path) {
        super.setRawPath(path);
        return this;
    }

    @Override
    public StreamingHttpRequest setRawQuery(final String query) {
        super.setRawQuery(query);
        return this;
    }

    @Override
    public Publisher<Buffer> getPayloadBody() {
        return payloadAndTrailers.liftSynchronous(HttpTransportBufferFilterOperator.INSTANCE);
    }

    @Override
    public Publisher<Object> getPayloadBodyAndTrailers() {
        return payloadAndTrailers;
    }

    @Override
    public StreamingHttpRequest setPayloadBody(Publisher<Buffer> payloadBody) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpRequest(this, allocator,
                payloadBody.liftSynchronous(new BridgeFlowControlAndDiscardOperator(payloadAndTrailers.liftSynchronous(
                        new HttpBufferTrailersSpliceOperator(outTrailersSingle)))),
                outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpRequest setPayloadBody(final Publisher<T> payloadBody, final HttpSerializer<T> serializer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpRequest(this, allocator, serializer.serialize(getHeaders(),
                payloadBody.liftSynchronous(new SerializeBridgeFlowControlAndDiscardOperator<>(
                        payloadAndTrailers.liftSynchronous(new HttpBufferTrailersSpliceOperator(outTrailersSingle)))),
                allocator),
            outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpRequest transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                         final HttpSerializer<T> serializer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpRequest(this, allocator, serializer.serialize(getHeaders(),
                transformer.apply(payloadAndTrailers.liftSynchronous(new HttpBufferTrailersSpliceOperator(
                        outTrailersSingle))), allocator),
                outTrailersSingle);
    }

    @Override
    public StreamingHttpRequest transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpRequest(this, allocator, transformer.apply(payloadAndTrailers.liftSynchronous(
                new HttpBufferTrailersSpliceOperator(outTrailersSingle))), outTrailersSingle);
    }

    @Override
    public StreamingHttpRequest transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultStreamingHttpRequest<>(this, allocator, transformer.apply(payloadAndTrailers.liftSynchronous(
                new HttpObjectTrailersSpliceOperator(outTrailersSingle))), outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpRequest transform(final Supplier<T> stateSupplier,
                                              final BiFunction<Buffer, T, Buffer> transformer,
                                              final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpRequest(this, allocator, payloadAndTrailers.liftSynchronous(
                new HttpDataSourceTranformations.HttpRawBuffersAndTrailersOperator<>(stateSupplier, transformer,
                        trailersTransformer, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public <T> StreamingHttpRequest transformRaw(final Supplier<T> stateSupplier,
                                                 final BiFunction<Object, T, ?> transformer,
                                                 final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultStreamingHttpRequest<>(this, allocator, payloadAndTrailers.liftSynchronous(
                new HttpDataSourceTranformations.HttpRawObjectsAndTrailersOperator<>(stateSupplier, transformer,
                        trailersTransformer, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public Single<HttpRequest> toRequest() {
        return aggregatePayloadAndTrailers(getPayloadBodyAndTrailers(), allocator).map(pair -> {
            assert pair.trailers != null;
            return new BufferHttpRequest(getMethod(), getRequestTarget(), getVersion(), getHeaders(), pair.trailers,
                    pair.compositeBuffer, allocator);
        });
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator, payloadAndTrailers.liftSynchronous(
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

        final TransportStreamingHttpRequest that = (TransportStreamingHttpRequest) o;

        return payloadAndTrailers.equals(that.payloadAndTrailers);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadAndTrailers.hashCode();
    }
}
