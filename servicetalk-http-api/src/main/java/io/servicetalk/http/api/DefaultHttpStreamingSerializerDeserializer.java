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
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.http.api.HeaderUtils.deserializeCheckContentType;
import static java.util.Objects.requireNonNull;

final class DefaultHttpStreamingSerializerDeserializer<T> implements HttpStreamingSerializerDeserializer<T> {
    private final StreamingSerializerDeserializer<T> serializer;
    private final Consumer<HttpHeaders> headersSerializeConsumer;
    private final Predicate<HttpHeaders> headersDeserializePredicate;

    DefaultHttpStreamingSerializerDeserializer(final StreamingSerializerDeserializer<T> serializer,
                                               final Consumer<HttpHeaders> headersSerializeConsumer,
                                               final Predicate<HttpHeaders> headersDeserializePredicate) {
        this.serializer = requireNonNull(serializer);
        this.headersSerializeConsumer = requireNonNull(headersSerializeConsumer);
        this.headersDeserializePredicate = requireNonNull(headersDeserializePredicate);
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

    @Override
    public BlockingIterable<T> deserialize(final HttpHeaders headers, final BlockingIterable<Buffer> payload,
                                           final BufferAllocator allocator) {
        deserializeCheckContentType(headers, headersDeserializePredicate);
        return serializer.deserialize(payload, allocator);
    }

    @Override
    public Publisher<T> deserialize(final HttpHeaders headers, final Publisher<Buffer> payload,
                                    final BufferAllocator allocator) {
        deserializeCheckContentType(headers, headersDeserializePredicate);
        return serializer.deserialize(payload, allocator);
    }
}
