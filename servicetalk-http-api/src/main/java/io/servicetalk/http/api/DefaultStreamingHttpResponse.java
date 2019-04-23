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
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpDataSourceTranformations.BridgeFlowControlAndDiscardOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpPayloadAndTrailersFromSingleOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

class DefaultStreamingHttpResponse<P> extends DefaultHttpResponseMetaData implements StreamingHttpResponse,
                                                                                     EffectiveApiType {
    final Publisher<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;
    final boolean aggregated;

    DefaultStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                 final HttpHeaders headers, final HttpHeaders initialTrailers,
                                 final BufferAllocator allocator, final Publisher<P> payloadBody,
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
     * @param payloadBody A {@link Publisher} that provide only the payload body. The trailers <strong>must</strong>
     * not be included, and instead are represented by {@code trailersSingle}.
     * @param aggregated The type of API this response was originally created as.
     */
    DefaultStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                 final HttpHeaders headers, final Single<HttpHeaders> trailersSingle,
                                 final BufferAllocator allocator, final Publisher<P> payloadBody,
                                 final boolean aggregated) {
        super(status, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailersSingle = requireNonNull(trailersSingle);
        this.aggregated = aggregated;
    }

    DefaultStreamingHttpResponse(final DefaultHttpResponseMetaData oldRequest,
                                 final BufferAllocator allocator,
                                 final Publisher<P> payloadBody,
                                 final Single<HttpHeaders> trailersSingle,
                                 final boolean aggregated) {
        super(oldRequest);
        this.allocator = allocator;
        this.payloadBody = payloadBody;
        this.trailersSingle = trailersSingle;
        this.aggregated = aggregated;
    }

    @Override
    public final StreamingHttpResponse version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public final StreamingHttpResponse status(final HttpResponseStatus status) {
        super.status(status);
        return this;
    }

    @Override
    public Publisher<Buffer> payloadBody() {
        return payloadBody.liftSync(HttpBufferFilterOperator.INSTANCE);
    }

    @Override
    public final Publisher<Object> payloadBodyAndTrailers() {
        return payloadBody
                .map(payload -> (Object) payload) // down cast to Object
                .concat(trailersSingle);
    }

    @Override
    public final StreamingHttpResponse payloadBody(final Publisher<Buffer> payloadBody) {
        return new BufferStreamingHttpResponse(this, allocator,
                payloadBody.liftSync(new BridgeFlowControlAndDiscardOperator(payloadBody())), trailersSingle,
                aggregated);
    }

    @Override
    public final <T> StreamingHttpResponse payloadBody(final Publisher<T> payloadBody,
                                                       final HttpSerializer<T> serializer) {
        return new BufferStreamingHttpResponse(this, allocator, serializer.serialize(headers(),
                    payloadBody.liftSync(new SerializeBridgeFlowControlAndDiscardOperator<>(payloadBody())),
                    allocator),
                trailersSingle, aggregated);
    }

    @Override
    public final <T> StreamingHttpResponse transformPayloadBody(
            final Function<Publisher<Buffer>, Publisher<T>> transformer, final HttpSerializer<T> serializer) {
        return new BufferStreamingHttpResponse(this, allocator,
                serializer.serialize(headers(), transformer.apply(payloadBody()), allocator),
                trailersSingle, aggregated);
    }

    @Override
    public final StreamingHttpResponse transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        return new BufferStreamingHttpResponse(this, allocator, transformer.apply(payloadBody()), trailersSingle,
                aggregated);
    }

    @Override
    public final StreamingHttpResponse transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
        return new DefaultStreamingHttpResponse<>(this, allocator, transformer.apply(payloadBody), trailersSingle,
                aggregated);
    }

    @Override
    public final <T> StreamingHttpResponse transform(final Supplier<T> stateSupplier,
                                                     final BiFunction<Buffer, T, Buffer> transformer,
                                                     final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferStreamingHttpResponse(this, allocator, payloadBody()
                .liftSync(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                        trailersTrans, trailersSingle, outTrailersSingle)),
                fromSource(outTrailersSingle), false);
        // This transform may add trailers, and if there are trailers present we must send `transfer-encoding: chunked`
        // not `content-length`, so force the API type to `non-aggregated to indicate that.
    }

    @Override
    public final <T> StreamingHttpResponse transformRaw(final Supplier<T> stateSupplier,
                                                        final BiFunction<Object, T, ?> transformer,
                                                        final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new DefaultStreamingHttpResponse<>(this, allocator, payloadBody
                .liftSync(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                        trailersTrans, trailersSingle, outTrailersSingle)),
                fromSource(outTrailersSingle), false);
        // This transform may add trailers, and if there are trailers present we must send `transfer-encoding: chunked`
        // not `content-length`, so force the API type to non-aggregated to indicate that.
    }

    @Override
    public final Single<HttpResponse> toResponse() {
        return aggregatePayloadAndTrailers(payloadBodyAndTrailers(), allocator).map(pair -> {
            assert pair.trailers != null;
            return new BufferHttpResponse(status(), version(), headers(), pair.trailers, pair.compositeBuffer,
                    allocator);
        });
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return new DefaultBlockingStreamingHttpResponse<>(this, allocator, payloadBody.toIterable(), trailersSingle,
                aggregated);
    }

    @Override
    public boolean isAggregated() {
        return aggregated;
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
