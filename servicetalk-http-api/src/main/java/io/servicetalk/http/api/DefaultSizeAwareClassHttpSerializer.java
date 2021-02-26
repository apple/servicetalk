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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serialization.api.Serializer;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/**
 * An {@link HttpSerializer} that serializes a {@link Class} of type {@link T}. This {@link HttpSerializer} can control
 * sizes of intermediary {@link Buffer}s for serializing a stream.
 * @deprecated Will be removed with {@link HttpSerializer}.
 * @param <T> Type to serialize
 * @see DefaultTypeHttpSerializer
 * @see DefaultClassHttpSerializer
 */
@Deprecated
final class DefaultSizeAwareClassHttpSerializer<T> implements HttpSerializer<T> {

    private final Consumer<HttpHeaders> addContentType;
    private final Serializer serializer;
    private final Class<T> type;
    private final IntUnaryOperator bytesEstimator;

    DefaultSizeAwareClassHttpSerializer(final Class<T> type, final Serializer serializer,
                                        final Consumer<HttpHeaders> addContentType,
                                        final IntUnaryOperator bytesEstimator) {
        this.addContentType = addContentType;
        this.serializer = serializer;
        this.type = type;
        this.bytesEstimator = bytesEstimator;
    }

    @Override
    public Buffer serialize(final HttpHeaders headers, final T value, final BufferAllocator allocator) {
        addContentType.accept(headers);
        return serializer.serialize(value, allocator, bytesEstimator.applyAsInt(0));
    }

    @Override
    public BlockingIterable<Buffer> serialize(final HttpHeaders headers, final BlockingIterable<T> value,
                                              final BufferAllocator allocator) {
        addContentType.accept(headers);
        return serializer.serialize(value, allocator, type, bytesEstimator);
    }

    @Override
    public Publisher<Buffer> serialize(final HttpHeaders headers, final Publisher<T> value,
                                       final BufferAllocator allocator) {
        addContentType.accept(headers);
        return serializer.serialize(value, allocator, type, bytesEstimator);
    }

    @Override
    public HttpPayloadWriter<T> serialize(final HttpHeaders headers, final HttpPayloadWriter<Buffer> payloadWriter,
                                          final BufferAllocator allocator) {
        addContentType.accept(headers);
        return new DelegatingToBufferHttpPayloadWriter<T>(payloadWriter, allocator) {
            @Override
            public void write(final T object) throws IOException {
                delegate.write(serializer.serialize(object, allocator, bytesEstimator.applyAsInt(0)));
            }
        };
    }
}
