/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.api.Single;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;

final class DefaultBlockingStreamingHttpResponse implements BlockingStreamingHttpResponse, PayloadInfo {

    private final DefaultStreamingHttpResponse original;

    DefaultBlockingStreamingHttpResponse(final DefaultStreamingHttpResponse original) {
        this.original = original;
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return original.payloadBody().toIterable();
    }

    @Override
    public BlockingStreamingHttpResponse payloadBody(final Iterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse payloadBody(final CloseableIterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse payloadBody(final Iterable<T> payloadBody,
                                                         final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse payloadBody(final CloseableIterable<T> payloadBody,
                                                         final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse transformPayloadBody(final Function<BlockingIterable<Buffer>,
            BlockingIterable<T>> transformer, final HttpSerializer<T> serializer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())),
                serializer);
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())));
        return this;
    }

    @Override
    public BlockingStreamingHttpResponse transformRawPayloadBody(final UnaryOperator<BlockingIterable<?>> transformer) {
        original.transformRawPayloadBody(bufferPublisher ->
                fromIterable(transformer.apply(bufferPublisher.toIterable())));
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse transform(
            final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer,
            final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        original.transform(stateSupplier, transformer, trailersTransformer);
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpResponse transformRaw(
            final Supplier<T> stateSupplier, final BiFunction<Object, T, ?> transformer,
            final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        original.transformRaw(stateSupplier, transformer, trailersTransformer);
        return this;
    }

    @Override
    public Single<HttpResponse> toResponse() {
        return original.toResponse();
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        return original;
    }

    @Override
    public HttpProtocolVersion version() {
        return original.version();
    }

    @Override
    public BlockingStreamingHttpResponse version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return original.headers();
    }

    @Override
    public HttpResponseStatus status() {
        return original.status();
    }

    @Override
    public BlockingStreamingHttpResponse status(final HttpResponseStatus status) {
        original.status(status);
        return this;
    }

    @Override
    public boolean safeToAggregate() {
        return original.safeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return original.mayHaveTrailers();
    }

    @Override
    public boolean onlyEmitsBuffer() {
        return original.onlyEmitsBuffer();
    }
}
