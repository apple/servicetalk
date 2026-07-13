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
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.SerializationException;

import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStreamingDeserializer.isCompressed;
import static io.servicetalk.grpc.api.GrpcStreamingSerializer.METADATA_SIZE;
import static java.util.Objects.requireNonNull;

final class GrpcDeserializer<T> implements Deserializer<T> {
    private final Deserializer<T> deserializer;
    @Nullable
    private final BufferDecoder decompressor;
    // Invoked with the declared length of the message before it is read; rejects/warns on oversized messages.
    private final GrpcMessageSizeLimiter sizeLimiter;

    GrpcDeserializer(final Deserializer<T> deserializer, final GrpcMessageSizeLimiter sizeLimiter) {
        this(deserializer, null, sizeLimiter);
    }

    GrpcDeserializer(final Deserializer<T> deserializer,
                     @Nullable final BufferDecoder decompressor,
                     final GrpcMessageSizeLimiter sizeLimiter) {
        this.deserializer = requireNonNull(deserializer);
        this.sizeLimiter = requireNonNull(sizeLimiter);
        // When enforcing, bound the decompressor at the limit so an oversized compressed message aborts mid-inflate.
        this.decompressor = decompressor == null ? null : sizeLimiter.capDecoder(decompressor);
    }

    @Nullable
    CharSequence messageEncoding() {
        return decompressor == null ? null : decompressor.encodingName();
    }

    @Override
    public T deserialize(final Buffer buffer, final BufferAllocator allocator) {
        if (buffer.readableBytes() < METADATA_SIZE) {
            throw new SerializationException("Not enough data");
        }
        boolean compressed = isCompressed(buffer);
        if (compressed && decompressor == null) {
            throw new SerializationException("Compressed flag set, but no compressor");
        }
        int expectedLength = buffer.readInt();
        if (expectedLength < 0) {
            throw new SerializationException("Message-Length invalid: " + expectedLength);
        }
        // Enforce the inbound message-size limit against the declared length before reading the message bytes.
        sizeLimiter.accept(expectedLength);

        Buffer result = buffer.readBytes(expectedLength);
        if (compressed) {
            result = decompressor.decoder().deserialize(result, allocator);
            // The check above only bounds the compressed wire size, so a small frame can inflate past the limit. A
            // compressor should have already aborted mid-inflate before reaching here, but if it's a custom compressor
            // that doesn't support a limit we check just in case.
            sizeLimiter.accept(result.readableBytes());
        }
        return deserializer.deserialize(result, allocator);
    }
}
