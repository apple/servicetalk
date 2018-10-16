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

class DefaultStreamingHttpRequest<P> extends DefaultHttpRequestMetaData implements StreamingHttpRequest {
    final Publisher<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;

    DefaultStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                final HttpProtocolVersion version, final HttpHeaders headers,
                                final HttpHeaders initialTrailers, final BufferAllocator allocator,
                                final Publisher<P> payloadBody) {
        this(method, requestTarget, version, headers, success(initialTrailers), allocator, payloadBody);
    }

    /**
     * Create a new instance.
     * @param method The {@link HttpRequestMethod}.
     * @param requestTarget The request-target.
     * @param version The {@link HttpProtocolVersion}.
     * @param headers The initial {@link HttpHeaders}.
     * @param allocator The {@link BufferAllocator} to use for serialization (if required).
     * @param payloadBody A {@link Publisher} that provide only the payload body. The trailers <strong>must</strong>
     * not be included, and instead are represented by {@code trailersSingle}.
     * @param trailersSingle The {@link Single} <strong>must</strong> support multiple subscribes, and it is assumed to
     * provide the original data if re-used over transformation operations.
     */
    DefaultStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                final HttpProtocolVersion version, final HttpHeaders headers,
                                final Single<HttpHeaders> trailersSingle, final BufferAllocator allocator,
                                final Publisher<P> payloadBody) {
        super(method, requestTarget, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailersSingle = requireNonNull(trailersSingle);
    }

    DefaultStreamingHttpRequest(final DefaultHttpRequestMetaData oldRequest,
                                final BufferAllocator allocator,
                                final Publisher<P> payloadBody,
                                final Single<HttpHeaders> trailersSingle) {
        super(oldRequest);
        this.allocator = allocator;
        this.payloadBody = payloadBody;
        this.trailersSingle = trailersSingle;
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
        return payloadBody.liftSynchronous(HttpBufferFilterOperator.INSTANCE);
    }

    @Override
    public final Publisher<Object> payloadBodyAndTrailers() {
        return payloadBody
                .map(payload -> (Object) payload) // down cast to Object
                .concatWith(trailersSingle);
    }

    @Override
    public final StreamingHttpRequest payloadBody(Publisher<Buffer> payloadBody) {
        return new BufferStreamingHttpRequest(this, allocator,
                payloadBody.liftSynchronous(new BridgeFlowControlAndDiscardOperator(payloadBody())), trailersSingle);
    }

    @Override
    public final <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody,
                                                      final HttpSerializer<T> serializer) {
        return new BufferStreamingHttpRequest(this, allocator, serializer.serialize(headers(),
                    payloadBody.liftSynchronous(new SerializeBridgeFlowControlAndDiscardOperator<>(payloadBody())),
                    allocator),
                trailersSingle);
    }

    @Override
    public final <T> StreamingHttpRequest transformPayloadBody(Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                               HttpSerializer<T> serializer) {
        return new BufferStreamingHttpRequest(this, allocator,
                serializer.serialize(headers(), transformer.apply(payloadBody()), allocator),
                trailersSingle);
    }

    @Override
    public final StreamingHttpRequest transformPayloadBody(UnaryOperator<Publisher<Buffer>> transformer) {
        return new BufferStreamingHttpRequest(this, allocator, transformer.apply(payloadBody()), trailersSingle);
    }

    @Override
    public final StreamingHttpRequest transformRawPayloadBody(UnaryOperator<Publisher<?>> transformer) {
        return new DefaultStreamingHttpRequest<>(this, allocator, transformer.apply(payloadBody), trailersSingle);
    }

    @Override
    public final <T> StreamingHttpRequest transform(Supplier<T> stateSupplier,
                                                    BiFunction<Buffer, T, Buffer> transformer,
                                                    BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferStreamingHttpRequest(this, allocator, payloadBody()
                .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                        trailersTransformer, trailersSingle, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public final <T> StreamingHttpRequest transformRaw(Supplier<T> stateSupplier,
                                                       BiFunction<Object, T, ?> transformer,
                                                       BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultStreamingHttpRequest<>(this, allocator, payloadBody
                .liftSynchronous(new HttpPayloadAndTrailersFromSingleOperator<>(stateSupplier, transformer,
                        trailersTransformer, trailersSingle, outTrailersSingle)),
                outTrailersSingle);
    }

    @Override
    public final Single<HttpRequest> toRequest() {
        return aggregatePayloadAndTrailers(payloadBodyAndTrailers(), allocator).map(pair -> {
             assert pair.trailers != null;
             return new BufferHttpRequest(method(), requestTarget(), version(), headers(), pair.trailers,
                     pair.compositeBuffer, allocator);
        });
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator, payloadBody.toIterable(), trailersSingle);
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

        final DefaultStreamingHttpRequest<?> that = (DefaultStreamingHttpRequest<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
