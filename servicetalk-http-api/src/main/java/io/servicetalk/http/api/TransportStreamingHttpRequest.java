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
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpBufferTrailersSpliceOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpObjectTrailersSpliceOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.HttpTransportBufferFilterOperator;
import io.servicetalk.http.api.HttpDataSourceTranformations.SerializeBridgeFlowControlAndDiscardOperator;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpDataSourceTranformations.aggregatePayloadAndTrailers;
import static java.util.Objects.requireNonNull;

final class TransportStreamingHttpRequest extends DefaultHttpRequestMetaData implements StreamingHttpRequest,
                                                                                        EffectiveApiType {
    final Publisher<Object> payloadAndTrailers;
    final BufferAllocator allocator;
    final boolean aggregated;

    TransportStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                  final HttpProtocolVersion version, final HttpHeaders headers,
                                  final BufferAllocator allocator, final Publisher<Object> payloadAndTrailers,
                                  final boolean aggregated) {
        super(method, requestTarget, version, headers);
        this.allocator = requireNonNull(allocator);
        this.payloadAndTrailers = requireNonNull(payloadAndTrailers);
        this.aggregated = aggregated;
    }

    @Override
    public StreamingHttpRequest version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public StreamingHttpRequest method(final HttpRequestMethod method) {
        super.method(method);
        return this;
    }

    @Override
    public StreamingHttpRequest requestTarget(final String requestTarget) {
        super.requestTarget(requestTarget);
        return this;
    }

    @Override
    public StreamingHttpRequest path(final String path) {
        super.path(path);
        return this;
    }

    @Override
    public StreamingHttpRequest appendPathSegments(final String... segments) {
        super.appendPathSegments(segments);
        return this;
    }

    @Override
    public StreamingHttpRequest rawPath(final String path) {
        super.rawPath(path);
        return this;
    }

    @Override
    public StreamingHttpRequest rawQuery(final String query) {
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
        return payloadAndTrailers.liftSync(HttpTransportBufferFilterOperator.INSTANCE);
    }

    @Override
    public Publisher<Object> payloadBodyAndTrailers() {
        return payloadAndTrailers;
    }

    @Override
    public StreamingHttpRequest payloadBody(Publisher<Buffer> payloadBody) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferStreamingHttpRequest(this, allocator,
                payloadBody.liftSync(new BridgeFlowControlAndDiscardOperator(payloadAndTrailers.liftSync(
                        new HttpBufferTrailersSpliceOperator(outTrailersSingle)))),
                fromSource(outTrailersSingle), aggregated);
    }

    @Override
    public <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody, final HttpSerializer<T> serializer) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferStreamingHttpRequest(this, allocator, serializer.serialize(headers(),
                payloadBody.liftSync(new SerializeBridgeFlowControlAndDiscardOperator<>(
                        payloadAndTrailers.liftSync(new HttpBufferTrailersSpliceOperator(outTrailersSingle)))),
                allocator),
                fromSource(outTrailersSingle), aggregated);
    }

    @Override
    public <T> StreamingHttpRequest transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                         final HttpSerializer<T> serializer) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferStreamingHttpRequest(this, allocator, serializer.serialize(headers(),
                transformer.apply(payloadAndTrailers.liftSync(new HttpBufferTrailersSpliceOperator(
                        outTrailersSingle))), allocator),
                fromSource(outTrailersSingle), aggregated);
    }

    @Override
    public StreamingHttpRequest transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferStreamingHttpRequest(this, allocator, transformer.apply(payloadAndTrailers.liftSync(
                new HttpBufferTrailersSpliceOperator(outTrailersSingle))), fromSource(outTrailersSingle),
                aggregated);
    }

    @Override
    public StreamingHttpRequest transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new DefaultStreamingHttpRequest<>(this, allocator, transformer.apply(payloadAndTrailers.liftSync(
                new HttpObjectTrailersSpliceOperator(outTrailersSingle))), fromSource(outTrailersSingle),
                aggregated);
    }

    @Override
    public <T> StreamingHttpRequest transform(final Supplier<T> stateSupplier,
                                              final BiFunction<Buffer, T, Buffer> transformer,
                                              final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new BufferStreamingHttpRequest(this, allocator, payloadAndTrailers.liftSync(
                new HttpDataSourceTranformations.HttpRawBuffersAndTrailersOperator<>(stateSupplier, transformer,
                        trailersTransformer, outTrailersSingle)), fromSource(outTrailersSingle), false);
        // This transform may add trailers, and if there are trailers present we must send `transfer-encoding: chunked`
        // not `content-length`, so force the API type to non-aggregated to indicate that.
    }

    @Override
    public <T> StreamingHttpRequest transformRaw(final Supplier<T> stateSupplier,
                                                 final BiFunction<Object, T, ?> transformer,
                                                 final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new DefaultStreamingHttpRequest<>(this, allocator, payloadAndTrailers.liftSync(
                new HttpDataSourceTranformations.HttpRawObjectsAndTrailersOperator<>(stateSupplier, transformer,
                        trailersTransformer, outTrailersSingle)), fromSource(outTrailersSingle), false);
        // This transform may add trailers, and if there are trailers present we must send `transfer-encoding: chunked`
        // not `content-length`, so force the API type to non-aggregated to indicate that.
    }

    @Override
    public Single<HttpRequest> toRequest() {
        return aggregatePayloadAndTrailers(payloadBodyAndTrailers(), allocator).map(pair -> {
            assert pair.trailers != null;
            return new BufferHttpRequest(method(), requestTarget(), version(), headers(), pair.trailers,
                    pair.compositeBuffer, allocator);
        });
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        final Processor<HttpHeaders, HttpHeaders> outTrailersSingle = newSingleProcessor();
        return new DefaultBlockingStreamingHttpRequest<>(this, allocator, payloadAndTrailers.liftSync(
                new HttpObjectTrailersSpliceOperator(outTrailersSingle)).toIterable(), fromSource(outTrailersSingle),
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

        final TransportStreamingHttpRequest that = (TransportStreamingHttpRequest) o;

        return payloadAndTrailers.equals(that.payloadAndTrailers);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadAndTrailers.hashCode();
    }
}
