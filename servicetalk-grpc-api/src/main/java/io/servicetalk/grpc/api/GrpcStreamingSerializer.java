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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;

import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Serializes gRPC <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">Length-Prefixed-Message</a>
 * tokens in a stream.
 * @param <T> The type of object to serialize.
 */
final class GrpcStreamingSerializer<T> implements StreamingSerializer<T> {
    static final int METADATA_SIZE = 5; // 1 byte for compression flag and 4 bytes for length of data
    static final byte FLAG_UNCOMPRESSED = 0x0;
    static final byte FLAG_COMPRESSED = 0x1;
    private final ToIntFunction<T> serializedBytesEstimator;
    private final Serializer<T> serializer;
    @Nullable
    private final BufferEncoder compressor;

    GrpcStreamingSerializer(final ToIntFunction<T> serializedBytesEstimator,
                            final Serializer<T> serializer) {
        this.serializedBytesEstimator = requireNonNull(serializedBytesEstimator);
        this.serializer = requireNonNull(serializer);
        this.compressor = null;
    }

    GrpcStreamingSerializer(final ToIntFunction<T> serializedBytesEstimator,
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
    public Publisher<Buffer> serialize(final Publisher<T> toSerialize, final BufferAllocator allocator) {
        return compressor == null ?
                toSerialize.map(t -> {
                    final int sizeEstimate = serializedBytesEstimator.applyAsInt(t);
                    Buffer buffer = allocator.newBuffer(METADATA_SIZE + sizeEstimate);
                    final int writerIndexBefore = buffer.writerIndex();
                    buffer.writerIndex(writerIndexBefore + METADATA_SIZE);
                    serializer.serialize(t, allocator, buffer);
                    buffer.setByte(writerIndexBefore, FLAG_UNCOMPRESSED);
                    buffer.setInt(writerIndexBefore + 1, buffer.writerIndex() - writerIndexBefore - METADATA_SIZE);
                    return buffer;
                }) :
                toSerialize.map(t -> {
                    // First do the serialization.
                    final int sizeEstimate = serializedBytesEstimator.applyAsInt(t);
                    Buffer serializedBuffer = allocator.newBuffer(sizeEstimate);
                    serializer.serialize(t, allocator, serializedBuffer);

                    // Next do the compression, pessimistically assume the size won't decrease when allocating.
                    Buffer resultBuffer = allocator.newBuffer(METADATA_SIZE + sizeEstimate);

                    // Compress into the same buffer that we return, so advance the writer index metadata
                    // bytes and then we fill in the meta data after compression is done and the final size is known.
                    final int writerIndexBefore = resultBuffer.writerIndex();
                    resultBuffer.writerIndex(writerIndexBefore + METADATA_SIZE);
                    compressor.encoder().serialize(serializedBuffer, allocator, resultBuffer);
                    resultBuffer.setByte(writerIndexBefore, FLAG_COMPRESSED);
                    resultBuffer.setInt(writerIndexBefore + 1,
                            resultBuffer.writerIndex() - writerIndexBefore - METADATA_SIZE);
                    return resultBuffer;
                });
    }
}
