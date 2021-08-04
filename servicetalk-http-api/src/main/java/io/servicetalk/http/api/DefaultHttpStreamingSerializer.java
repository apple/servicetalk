/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import static java.util.Objects.requireNonNull;

final class DefaultHttpStreamingSerializer<T> implements HttpStreamingSerializer<T> {
    private final StreamingSerializer<T> serializer;
    private final Consumer<HttpHeaders> headersSerializeConsumer;

    DefaultHttpStreamingSerializer(final Serializer<T> serializer,
                                   final ToIntFunction<T> bytesEstimator,
                                   final Consumer<HttpHeaders> headersSerializeConsumer) {
        this(new NonFramedStreamingSerializer<>(serializer, bytesEstimator), headersSerializeConsumer);
    }

    DefaultHttpStreamingSerializer(final StreamingSerializer<T> serializer,
                                   final Consumer<HttpHeaders> headersSerializeConsumer) {
        this.serializer = requireNonNull(serializer);
        this.headersSerializeConsumer = requireNonNull(headersSerializeConsumer);
    }

    @Override
    public BlockingIterable<Buffer> serialize(final HttpHeaders headers, final BlockingIterable<T> value,
                                              final BufferAllocator allocator) {
        // Do the headers modification eagerly, otherwise if this is done lazily the headers would have already been
        // written and the modification will not be applied in time.
        headersSerializeConsumer.accept(headers);
        return serializer.serialize(value, allocator);
    }

    @Override
    public Publisher<Buffer> serialize(final HttpHeaders headers, final Publisher<T> value,
                                       final BufferAllocator allocator) {
        // Do the headers modification eagerly, otherwise if this is done lazily the headers would have already been
        // written and the modification will not be applied in time.
        headersSerializeConsumer.accept(headers);
        return serializer.serialize(value, allocator);
    }

    @Override
    public HttpPayloadWriter<T> serialize(final HttpHeaders headers, final HttpPayloadWriter<Buffer> payloadWriter,
                                          final BufferAllocator allocator) {
        // Do the headers modification eagerly, otherwise if this is done lazily the headers would have already been
        // written and the modification will not be applied in time.
        headersSerializeConsumer.accept(headers);
        PayloadWriter<T> result = serializer.serialize(payloadWriter, allocator);
        return new HttpPayloadWriter<T>() {
            @Override
            public HttpHeaders trailers() {
                return payloadWriter.trailers();
            }

            @Override
            public void write(final T t) throws IOException {
                result.write(t);
            }

            @Override
            public void close(final Throwable cause) throws IOException {
                result.close(cause);
            }

            @Override
            public void close() throws IOException {
                result.close();
            }

            @Override
            public void flush() throws IOException {
                result.flush();
            }
        };
    }
}
