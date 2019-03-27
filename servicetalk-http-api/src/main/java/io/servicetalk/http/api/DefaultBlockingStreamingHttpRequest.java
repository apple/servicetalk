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
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.api.Single;
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

class DefaultBlockingStreamingHttpRequest<P> extends DefaultHttpRequestMetaData implements
                                                                                BlockingStreamingHttpRequest {
    final BlockingIterable<P> payloadBody;
    final BufferAllocator allocator;
    final Single<HttpHeaders> trailersSingle;

    DefaultBlockingStreamingHttpRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final HttpHeaders initialTrailers, final BufferAllocator allocator,
            final BlockingIterable<P> payloadBody) {
        this(method, requestTarget, version, headers, succeeded(initialTrailers), allocator, payloadBody);
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
    DefaultBlockingStreamingHttpRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final Single<HttpHeaders> trailersSingle, final BufferAllocator allocator,
            final BlockingIterable<P> payloadBody) {
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
    public final BlockingStreamingHttpRequest version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest method(final HttpRequestMethod method) {
        super.method(method);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest requestTarget(final String requestTarget) {
        super.requestTarget(requestTarget);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest path(final String path) {
        super.path(path);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest appendPathSegments(final String... segments) {
        super.appendPathSegments(segments);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest rawPath(final String path) {
        super.rawPath(path);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest rawQuery(final String query) {
        super.rawQuery(query);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest addQueryParameter(String key, String value) {
        super.addQueryParameter(key, value);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest addQueryParameters(String key, Iterable<String> values) {
        super.addQueryParameters(key, values);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest addQueryParameters(String key, String... values) {
        super.addQueryParameters(key, values);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setQueryParameter(String key, String value) {
        super.setQueryParameter(key, value);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setQueryParameters(String key, Iterable<String> values) {
        super.setQueryParameters(key, values);
        return this;
    }

    @Override
    public final BlockingStreamingHttpRequest setQueryParameters(String key, String... values) {
        super.setQueryParameters(key, values);
        return this;
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return new HttpBufferFilterIterable(payloadBody);
    }

    @Override
    public final BlockingStreamingHttpRequest payloadBody(final Iterable<Buffer> payloadBody) {
        return transformPayloadBody(consumeOldPayloadBody(from(payloadBody)));
    }

    @Override
    public final BlockingStreamingHttpRequest payloadBody(final CloseableIterable<Buffer> payloadBody) {
        return transformPayloadBody(consumeOldPayloadBody(from(payloadBody)));
    }

    @Override
    public final <T> BlockingStreamingHttpRequest payloadBody(final Iterable<T> payloadBody,
                                                              final HttpSerializer<T> serializer) {
        return transformPayloadBody(consumeOldPayloadBodySerialized(from(payloadBody)), serializer);
    }

    @Override
    public final <T> BlockingStreamingHttpRequest payloadBody(final CloseableIterable<T> payloadBody,
                                                              final HttpSerializer<T> serializer) {
        return transformPayloadBody(consumeOldPayloadBodySerialized(from(payloadBody)), serializer);
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transformPayloadBody(
            final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
            final HttpSerializer<T> serializer) {
        return new BufferBlockingStreamingHttpRequest(this, allocator,
                serializer.serialize(headers(), transformer.apply(payloadBody()), allocator),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpRequest transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        return new BufferBlockingStreamingHttpRequest(this, allocator, transformer.apply(payloadBody()),
                trailersSingle);
    }

    @Override
    public final BlockingStreamingHttpRequest transformRawPayloadBody(
            final UnaryOperator<BlockingIterable<?>> transformer) {
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator, transformer.apply(payloadBody),
                trailersSingle);
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transform(
            final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer,
            final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferBlockingStreamingHttpRequest(this, allocator,
                new HttpBuffersAndTrailersIterable<>(payloadBody(), stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                fromSource(outTrailersSingle));
    }

    @Override
    public final <T> BlockingStreamingHttpRequest transformRaw(final Supplier<T> stateSupplier,
                                                         final BiFunction<Object, T, ?> transformer,
                                                         final BiFunction<T, HttpHeaders, HttpHeaders> trailersTrans) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator,
                new HttpObjectsAndTrailersIterable<>(payloadBody, stateSupplier,
                        transformer, trailersTrans, trailersSingle, outTrailersSingle),
                fromSource(outTrailersSingle));
    }

    @Override
    public final Single<HttpRequest> toRequest() {
        return toStreamingRequest().toRequest();
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return new DefaultStreamingHttpRequest<>(method(), requestTarget(), version(), headers(),
                trailersSingle, allocator, fromIterable(payloadBody));
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

        final DefaultBlockingStreamingHttpRequest<?> that = (DefaultBlockingStreamingHttpRequest<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
