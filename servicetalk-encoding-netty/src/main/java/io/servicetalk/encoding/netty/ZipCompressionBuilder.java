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
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * Base class for Zip based content-codecs.
 */
public abstract class ZipCompressionBuilder {
    private static final int DEFAULT_MAX_CHUNK_SIZE = 4 << 20; //4MiB

    private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    private long maxDecompressedBytes = CompressionDefaults.defaultMaxDecompressedBytes();
    private int compressionLevel = 6;

    ZipCompressionBuilder() {
        // pkg private
    }

    /**
     * Sets the compression level for this codec's encoder.
     * @param compressionLevel 1 yields the fastest compression and 9 yields the best compression,
     * 0 means no compression.
     * @return {@code this}
     */
    public final ZipCompressionBuilder withCompressionLevel(final int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }

        this.compressionLevel = compressionLevel;
        return this;
    }

    /**
     * Set the max allowed chunk size the decoder may emit in a single inflate step. This does
     * <em>not</em> bound the total decompressed payload size; see
     * {@link #maxDecompressedBytes(long)} for that.
     * @param maxChunkSize the max allowed chunk size to inflate during decoding.
     * @return {@code this}
     */
    public final ZipCompressionBuilder maxChunkSize(final int maxChunkSize) {
        this.maxChunkSize = ensurePositive(maxChunkSize, "maxChunkSize");
        return this;
    }

    /**
     * Set the maximum cumulative size in bytes of the decompressed payload produced by a single
     * aggregated {@link SerializerDeserializer#deserialize deserialize} call. When the limit is
     * exceeded the operation fails with a
     * {@link io.servicetalk.encoding.api.BufferEncodingException}. Used to defend against
     * decompression-bomb inputs where a small compressed payload expands to an unbounded amount
     * of memory.
     * <p>
     * Unlike {@link #maxChunkSize(int)}, which bounds only a single inflate step inside the codec,
     * this bound is tracked across every chunk produced for the same input.
     * <p>
     * Defaults to 64 MiB. The default can be overridden process-wide via the
     * {@value CompressionDefaults#MAX_DECOMPRESSED_BYTES_PROPERTY} system property (intended as
     * an operational escape hatch for unforeseen issues — prefer per-builder configuration).
     * A value of {@code 0} disables the limit; callers that decode trusted input and need
     * unbounded decompression can opt out, but doing so re-exposes the decompression-bomb
     * attack surface.
     * <p>
     * This setting does <em>not</em> apply to the streaming {@code buildStreaming()} path.
     * Streaming consumers typically process much larger payloads and are assumed to apply
     * back-pressure; a streaming client that aggregates decoded chunks into a single buffer
     * should enforce its own size bound.
     * @param maxDecompressedBytes the max total decompressed bytes allowed per aggregated
     * deserialize call, or {@code 0} for unbounded.
     * @return {@code this}
     */
    public final ZipCompressionBuilder maxDecompressedBytes(final long maxDecompressedBytes) {
        this.maxDecompressedBytes = ensureNonNegative(maxDecompressedBytes, "maxDecompressedBytes");
        return this;
    }

    /**
     * Build and return an instance of the {@link SerializerDeserializer} with the configuration of the builder.
     * @return the {@link SerializerDeserializer} with the configuration of the builder
     */
    public abstract SerializerDeserializer<Buffer> build();

    /**
     * Build and return an instance of the {@link StreamingSerializerDeserializer} with the configuration of the
     * builder.
     * @return the {@link StreamingSerializerDeserializer} with the configuration of the builder
     */
    public abstract StreamingSerializerDeserializer<Buffer> buildStreaming();

    /**
     * Returns the compression level for this codec.
     * @return return the compression level set for this codec.
     */
    final int compressionLevel() {
        return compressionLevel;
    }

    /**
     * Returns the max chunk size allowed to inflate during decoding.
     * @return Returns the max chunk size allowed to inflate during decoding.
     */
    final int maxChunkSize() {
        return maxChunkSize;
    }

    /**
     * Returns the max total decompressed bytes allowed per aggregated deserialize call, or
     * {@code 0} for unbounded.
     * @return the configured cap, or {@code 0} for unbounded.
     */
    final long maxDecompressedBytes() {
        return maxDecompressedBytes;
    }
}
