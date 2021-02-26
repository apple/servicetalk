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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.serializer.api.Serializer;

import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStreamingSerializer.FLAG_COMPRESSED;
import static io.servicetalk.grpc.api.GrpcStreamingSerializer.FLAG_UNCOMPRESSED;
import static io.servicetalk.grpc.api.GrpcStreamingSerializer.METADATA_SIZE;
import static java.util.Objects.requireNonNull;

final class GrpcSerializer<T> implements Serializer<T> {
    private final ToIntFunction<T> serializedBytesEstimator;
    private final Serializer<T> serializer;
    @Nullable
    private final BufferEncoder compressor;

    GrpcSerializer(final ToIntFunction<T> serializedBytesEstimator,
                   final Serializer<T> serializer) {
        this.serializedBytesEstimator = requireNonNull(serializedBytesEstimator);
        this.serializer = requireNonNull(serializer);
        this.compressor = null;
    }

    GrpcSerializer(final ToIntFunction<T> serializedBytesEstimator,
                   final Serializer<T> serializer,
                   final BufferEncoder compressor) {
        this.serializedBytesEstimator = requireNonNull(serializedBytesEstimator);
        this.serializer = requireNonNull(serializer);
        this.compressor = requireNonNull(compressor);
    }

    @Nullable
    CharSequence messageEncoding() {
        return compressor == null ? null : compressor.encodingName();
    }

    @Override
    public void serialize(final T t, final BufferAllocator allocator, final Buffer buffer) {
        if (compressor == null) {
            final int writerIndexBefore = buffer.writerIndex();
            buffer.writerIndex(writerIndexBefore + METADATA_SIZE);
            serializer.serialize(t, allocator, buffer);
            buffer.setByte(writerIndexBefore, FLAG_UNCOMPRESSED);
            buffer.setInt(writerIndexBefore + 1, buffer.writerIndex() - writerIndexBefore - METADATA_SIZE);
        } else {
            // First do the serialization.
            final int sizeEstimate = serializedBytesEstimator.applyAsInt(t);
            Buffer serializedBuffer = allocator.newBuffer(sizeEstimate);
            serializer.serialize(t, allocator, serializedBuffer);

            // Compress into the same buffer that we return, so advance the writer index metadata
            // bytes and then we fill in the meta data after compression is done and the final size is known.
            final int writerIndexBefore = buffer.writerIndex();
            buffer.writerIndex(writerIndexBefore + METADATA_SIZE);
            compressor.encoder().serialize(serializedBuffer, allocator, buffer);
            buffer.setByte(writerIndexBefore, FLAG_COMPRESSED);
            buffer.setInt(writerIndexBefore + 1, buffer.writerIndex() - writerIndexBefore - METADATA_SIZE);
        }
    }

    @Override
    public Buffer serialize(final T t, final BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(serializedBytesEstimator.applyAsInt(t) + METADATA_SIZE);
        serialize(t, allocator, buffer);
        return buffer;
    }
}
