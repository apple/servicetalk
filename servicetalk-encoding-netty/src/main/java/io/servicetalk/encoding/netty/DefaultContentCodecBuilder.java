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
import io.servicetalk.encoding.api.ContentCodecBuilder;

import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

abstract class DefaultContentCodecBuilder implements ContentCodecBuilder {

    private static final int DEFAULT_MAX_ALLOWED_DECOMPRESSED_PAYLOAD = 16 << 20; //16MiB

    private int maxAllowedPayloadSize = DEFAULT_MAX_ALLOWED_DECOMPRESSED_PAYLOAD;

    protected int maxAllowedPayloadSize() {
        return maxAllowedPayloadSize;
    }

    @Override
    public ContentCodecBuilder maxAllowedPayloadSize(final int maxAllowedPayloadSize) {
        if (maxAllowedPayloadSize <= 0) {
            throw new IllegalArgumentException("maxAllowedPayloadSize: " + maxAllowedPayloadSize + " (expected > 0)");
        }

        this.maxAllowedPayloadSize = maxAllowedPayloadSize;
        return this;
    }

    public abstract static class ZipContentCodecBuilder extends DefaultContentCodecBuilder {

        private int compressionLevel = 6;

        /**
         * Sets the compression level for this codec's encoder.
         * @param compressionLevel 1 yields the fastest compression and 9 yields the best compression.
         * 0 means no compression. The default compression level is 6
         * @return <code>this</code>
         */
        public ZipContentCodecBuilder withCompressionLevel(final int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        protected int compressionLevel() {
            return compressionLevel;
        }
    }

    static final class GzipContentCodecBuilder extends ZipContentCodecBuilder {
        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec("gzip",
                    () -> new JdkZlibEncoder(ZlibWrapper.GZIP, compressionLevel()),
                    () -> new JdkZlibDecoder(ZlibWrapper.GZIP, maxAllowedPayloadSize())
            );
        }
    }

    static final class DeflateContentCodecBuilder extends ZipContentCodecBuilder {
        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec("deflate",
                    () -> new JdkZlibEncoder(ZlibWrapper.ZLIB, compressionLevel()),
                    () -> new JdkZlibDecoder(ZlibWrapper.ZLIB, maxAllowedPayloadSize())
            );
        }
    }

    public static final class SnappyContentCodecBuilder extends DefaultContentCodecBuilder {

        private boolean validateChecksums;

        /**
         * Force snappy to validate checksums, <code>false</code> by default.
         * @param validateChecksums If true, the checksum field will be validated against the actual uncompressed data,
         * and if the checksums do not match, a suitable DecompressionException will be thrown
         * @return <code>this</code>
         */
        public SnappyContentCodecBuilder validateChecksums(boolean validateChecksums) {
            this.validateChecksums = validateChecksums;
            return this;
        }

        @Override
        protected int maxAllowedPayloadSize() {
            throw new UnsupportedOperationException("Max allowed payload size is not supported for snappy.");
        }

        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec("snappy", SnappyFrameEncoder::new,
                    () -> new SnappyFrameDecoder(validateChecksums));
        }
    }
}
