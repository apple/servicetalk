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

import org.reactivestreams.Subscriber;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

final class TransportStreamingHttpResponse extends DefaultHttpResponseMetaData implements StreamingHttpResponse {
    final Publisher<Object> payloadAndTrailers;
    final BufferAllocator allocator;

    TransportStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                   final HttpHeaders headers, final BufferAllocator allocator,
                                   final Publisher<Object> rawContent) {
        super(status, version, headers);
        this.allocator = requireNonNull(allocator);
        payloadAndTrailers = new Publisher<Object>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super Object> subscriber) {

            }
        };
    }

    @Override
    public <T> Publisher<T> getPayloadBody(final HttpDeserializer<T> deserializer) {
        return null;
    }

    @Override
    public Publisher<Object> getPayloadBodyAndTrailers() {
        return payloadAndTrailers;
    }

    @Override
    public <T> StreamingHttpResponse transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer, final HttpSerializer<T> serializer) {
        return null;
    }

    @Override
    public StreamingHttpResponse transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        return null;
    }

    @Override
    public StreamingHttpResponse transformRawPayloadBody(final UnaryOperator<Publisher<?>> transformer) {
        return null;
    }

    @Override
    public <T> StreamingHttpResponse transform(final Supplier<T> stateSupplier, final BiFunction<Buffer, T, Buffer> transformer, final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        return null;
    }

    @Override
    public <T> StreamingHttpResponse transformRaw(final Supplier<T> stateSupplier,
                                                  final BiFunction<Object, T, ?> transformer,
                                                  final BiFunction<T, HttpHeaders, HttpHeaders> trailersTransformer) {
        return null;
    }

    @Override
    public Single<HttpResponse> toResponse() {
        return null;
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return null;
    }
}
