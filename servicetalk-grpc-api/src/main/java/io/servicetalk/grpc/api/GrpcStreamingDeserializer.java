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
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.StreamingDeserializer;
import io.servicetalk.serializer.utils.FramedDeserializerOperator;

import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStreamingSerializer.FLAG_COMPRESSED;
import static io.servicetalk.grpc.api.GrpcStreamingSerializer.FLAG_UNCOMPRESSED;
import static io.servicetalk.grpc.api.GrpcStreamingSerializer.METADATA_SIZE;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

final class GrpcStreamingDeserializer<T> implements StreamingDeserializer<T> {
    private final Deserializer<T> serializer;
    @Nullable
    private final BufferDecoder compressor;
    // Invoked with the declared length of each message before it is buffered; rejects/warns on oversized messages.
    private final GrpcMessageSizeLimiter sizeLimiter;

    GrpcStreamingDeserializer(final Deserializer<T> serializer, final GrpcMessageSizeLimiter sizeLimiter) {
        this.serializer = requireNonNull(serializer);
        this.compressor = null;
        this.sizeLimiter = requireNonNull(sizeLimiter);
    }

    GrpcStreamingDeserializer(final Deserializer<T> serializer,
                              final BufferDecoder compressor,
                              final GrpcMessageSizeLimiter sizeLimiter) {
        this.serializer = requireNonNull(serializer);
        this.compressor = requireNonNull(compressor);
        this.sizeLimiter = requireNonNull(sizeLimiter);
    }

    @Nullable
    CharSequence messageEncoding() {
        return compressor == null ? null : compressor.encodingName();
    }

    @Override
    public Publisher<T> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
        return serializedData.liftSync(new FramedDeserializerOperator<>(serializer, GrpcDeframer::new, allocator))
                .flatMapConcatIterable(identity());
    }

    private final class GrpcDeframer implements BiFunction<Buffer, BufferAllocator, Buffer> {
        private int expectedLength = -1;
        private boolean compressed;

        @Nullable
        @Override
        public Buffer apply(final Buffer buffer, final BufferAllocator allocator) {
            if (expectedLength < 0) {
                if (buffer.readableBytes() < METADATA_SIZE) {
                    return null;
                }
                compressed = isCompressed(buffer);
                if (compressed && compressor == null) {
                    throw new SerializationException("Compressed flag set, but no compressor");
                }
                expectedLength = buffer.readInt();
                if (expectedLength < 0) {
                    throw new SerializationException("Message-Length invalid: " + expectedLength);
                }
                // Enforce the inbound message-size limit against the declared length, before buffering any bytes
                // toward it, so an oversized (or maliciously large) frame is rejected without accumulating memory.
                sizeLimiter.accept(expectedLength);
            }
            if (buffer.readableBytes() < expectedLength) {
                return null;
            }
            Buffer result = buffer.readBytes(expectedLength);
            expectedLength = -1;
            if (compressed) {
                assert compressor != null;
                final Buffer decompressed = compressor.decoder().deserialize(result, allocator);
                // The wire-length check above only bounds the compressed frame; reject a decoded message that exceeds
                // the limit. Decompression memory is bounded separately by the codec's own decompressed-bytes cap.
                sizeLimiter.accept(decompressed.readableBytes(), true);
                return decompressed;
            }
            return result;
        }
    }

    static boolean isCompressed(Buffer buffer) throws SerializationException {
        final byte compressionFlag = buffer.readByte();
        if (compressionFlag == FLAG_UNCOMPRESSED) {
            return false;
        } else if (compressionFlag == FLAG_COMPRESSED) {
            return true;
        }
        throw new SerializationException("Compression flag must be 0 or 1 but was: " + compressionFlag);
    }
}
