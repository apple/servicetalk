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
package io.servicetalk.encoding.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferEncoderDecoder;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingDeserializer;
import io.servicetalk.serializer.api.StreamingSerializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static java.util.Objects.requireNonNull;

final class DefaultBufferEncoderDecoder implements BufferEncoderDecoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBufferEncoderDecoder.class);

    private final SerializerDeserializer<Buffer> compressor;
    private final StreamingSerializerDeserializer<Buffer> streamingCompressor;
    private final CharSequence encodingName;
    // Guards the "decompression could not be bounded" warning so it is logged at most once per codec instance. Not
    // part of value identity (see equals/hashCode).
    private final AtomicBoolean unboundedWarningLogged = new AtomicBoolean();

    DefaultBufferEncoderDecoder(SerializerDeserializer<Buffer> compressor,
                                StreamingSerializerDeserializer<Buffer> streamingCompressor,
                                CharSequence encodingName) {
        this.compressor = requireNonNull(compressor);
        this.streamingCompressor = requireNonNull(streamingCompressor);
        this.encodingName = requireNonNull(encodingName);
    }

    @Override
    public Serializer<Buffer> encoder() {
        return compressor;
    }

    @Override
    public StreamingSerializer<Buffer> streamingEncoder() {
        return streamingCompressor;
    }

    @Override
    public Deserializer<Buffer> decoder() {
        return compressor;
    }

    @Override
    public StreamingDeserializer<Buffer> streamingDecoder() {
        return streamingCompressor;
    }

    @Override
    public BufferDecoder withMaxDecompressedBytes(final int maxDecompressedBytes) {
        // The public bufferEncoder(...) factory accepts an arbitrary SerializerDeserializer and erases its concrete
        // type, so a runtime check is required here.
        if (compressor instanceof NettyCompressionSerializer) {
            final NettyCompressionSerializer recapped =
                    ((NettyCompressionSerializer) compressor).withMaxDecompressedBytes(maxDecompressedBytes);
            if (recapped != compressor) {
                // Only the decoder is re-capped; the (unused-for-decoding) encoder side is unaffected by the cap.
                return new DefaultBufferEncoderDecoder(recapped, streamingCompressor, encodingName);
            }
            // recapped == compressor: the codec's existing cap is already at least this restrictive; nothing to do.
            return this;
        }
        if (unboundedWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn("Codec for encoding '{}' does not support bounding decompressed size; the requested maximum " +
                    "of {} bytes was not applied. Decompression is bounded only by the codec's own configuration.",
                    encodingName, maxDecompressedBytes);
        }
        return this;
    }

    @Override
    public CharSequence encodingName() {
        return encodingName;
    }

    @Override
    public boolean equals(final Object o) {
        return this == o ||
                o instanceof DefaultBufferEncoderDecoder &&
                        contentEqualsIgnoreCase(encodingName(), ((DefaultBufferEncoderDecoder) o).encodingName());
    }

    @Override
    public int hashCode() {
        return caseInsensitiveHashCode(encodingName);
    }

    @Override
    public String toString() {
        return encodingName.toString();
    }
}
