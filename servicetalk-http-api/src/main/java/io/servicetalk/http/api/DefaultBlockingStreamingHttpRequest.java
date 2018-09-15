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

class DefaultBlockingStreamingHttpRequest<P> extends DefaultHttpRequestMetaData implements
                                                                                BlockingStreamingHttpRequest {
    final BlockingIterable<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;

    DefaultBlockingStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                        final HttpProtocolVersion version, final HttpHeaders headers,
                                        final BufferAllocator allocator, final HttpHeaders initialTrailers) {
        this(method, requestTarget, version, headers, allocator, emptyBlockingIterable(),
                success(initialTrailers));
    }

    /**
     * Create a new instance.
     * @param method The {@link HttpRequestMethod}.
     * @param requestTarget The request-target.
     * @param version The {@link HttpProtocolVersion}.
     * @param headers The initial {@link HttpHeaders}.
     * @param allocator The {@link BufferAllocator} to use for serialization (if required).
     * @param payloadBody A {@link BlockingIterable} that provide only the payload body. The trailers
     * <strong>must</strong> not be included, and instead are represented by {@code trailersSingle}.
     * @param trailersSingle The {@link Single} <strong>must</strong> support multiple subscribes, and it is assumed to
     * provide the original data if re-used over transformation operations.
     */
    DefaultBlockingStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                        final HttpProtocolVersion version, final HttpHeaders headers,
                                        final BufferAllocator allocator, final BlockingIterable<P> payloadBody,
                                        final Single<HttpHeaders> trailersSingle) {
        super(method, requestTarget, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailersSingle = requireNonNull(trailersSingle);
    }

    DefaultBlockingStreamingHttpRequest(final DefaultHttpRequestMetaData oldRequest,
                                        final BufferAllocator allocator,
                                        final BlockingIterable<P> payloadBody,
                                        final Single<HttpHeaders> trailersSingle) {
        super(oldRequest);
        this.allocator = allocator;
        this.payloadBody = payloadBody;
        this.trailersSingle = trailersSingle;
    }

    @Override
    public final BlockingStreamingHttpRequest setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setMethod(final HttpRequestMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setRequestTarget(final String requestTarget) {
        super.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setRawPath(final String path) {
        super.setRawPath(path);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setRawQuery(final String query) {
        super.setRawQuery(query);
        return this;
    }

    @Override
    public final <T> BlockingIterable<T> getPayloadBody(final HttpDeserializer<T> deserializer) {
        return deserializer.deserialize(getHeaders(), payloadBody);
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transformPayloadBody(
            final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
            final HttpSerializer<T> serializer) {
        return new BufferBlockingStreamingHttpRequest(this, allocator,
                serializer.serialize(getHeaders(), transformer.apply(getPayloadBody()), allocator),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpRequest transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        return new BufferBlockingStreamingHttpRequest(this, allocator, transformer.apply(getPayloadBody()),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpRequest transformRawPayloadBody(
            final UnaryOperator<BlockingIterable<?>> transformer) {
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator, transformer.apply(payloadBody),
                trailersSingle);
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transform(final Supplier<T> stateSupplier,
                                                            final BiFunction<Buffer, T, Buffer> transformer,
                                                            final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new BufferBlockingStreamingHttpRequest(this, allocator,
                new HttpBuffersAndTrailersIterable<>(getPayloadBody(), stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                outTrailersSingle);
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transformRaw(final Supplier<T> stateSupplier,
                                                         final BiFunction<Object, T, ?> transformer,
                                                         final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final SingleProcessor<HttpHeaders> outTrailersSingle = new SingleProcessor<>();
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator,
                new HttpObjectsAndTrailersIterable<>(payloadBody, stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                outTrailersSingle);
    }

    @Override
    public final Single<? extends HttpRequest> toRequest() {
        return toStreamingRequest().toRequest();
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return new DefaultStreamingHttpRequest<>(getMethod(), getRequestTarget(), getVersion(), getHeaders(), allocator,
                from(payloadBody), trailersSingle);
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

        final DefaultBlockingStreamingHttpRequest<?> that = (DefaultBlockingStreamingHttpRequest<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
