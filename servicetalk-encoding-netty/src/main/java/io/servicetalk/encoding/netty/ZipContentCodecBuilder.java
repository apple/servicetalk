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

import io.servicetalk.encoding.api.ContentCodec;

import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Base class for Zip based content-codecs.
 * @deprecated Use {@link ZipCompressionBuilder}.
 */
@Deprecated
public abstract class ZipContentCodecBuilder {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 4 << 20; //4MiB

    private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    private int compressionLevel = 6;

    ZipContentCodecBuilder() {
        // pkg private
    }

    /**
     * Sets the compression level for this codec's encoder.
     * @param compressionLevel 1 yields the fastest compression and 9 yields the best compression,
     * 0 means no compression.
     * @return {@code this}
     */
    public final ZipContentCodecBuilder withCompressionLevel(final int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }

        this.compressionLevel = compressionLevel;
        return this;
    }

    /**
     * Set the max allowed chunk size to inflate during decoding.
     * @param maxChunkSize the max allowed chunk size to inflate during decoding.
     * @return {@code this}
     */
    public final ZipContentCodecBuilder maxChunkSize(final int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize: " + maxChunkSize + " (expected > 0)");
        }

        this.maxChunkSize = maxChunkSize;
        return this;
    }

    /**
     * Build and return an instance of the {@link ContentCodec} with the configuration of the builder.
     * @return the {@link ContentCodec} with the configuration of the builder
     */
    public abstract ContentCodec build();

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

    static final class GzipContentCodecBuilder extends ZipContentCodecBuilder {

        private static final CharSequence GZIP = newAsciiString("gzip");

        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec(GZIP,
                    () -> new JdkZlibEncoder(ZlibWrapper.GZIP, compressionLevel()),
                    () -> new JdkZlibDecoder(ZlibWrapper.GZIP, maxChunkSize())
            );
        }
    }

    static final class DeflateContentCodecBuilder extends ZipContentCodecBuilder {

        private static final CharSequence DEFLATE = newAsciiString("deflate");

        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec(DEFLATE,
                    () -> new JdkZlibEncoder(ZlibWrapper.ZLIB, compressionLevel()),
                    () -> new JdkZlibDecoder(ZlibWrapper.ZLIB, maxChunkSize())
            );
        }
    }
}
