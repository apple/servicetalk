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

abstract class DefaultContentCodecBuilder implements ContentCodecBuilder {

    /**
     * Base class for Zip based content-codecs.
     */
    public abstract static class ZipContentCodecBuilder extends DefaultContentCodecBuilder {

        private static final int DEFAULT_MAX_CHUNK_SIZE = 4 << 20; //4MiB

        private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        private int compressionLevel = 6;

        /**
         * Sets the compression level for this codec's encoder.
         * @param compressionLevel 1 yields the fastest compression and 9 yields the best compression.
         * 0 means no compression.
         * @return {@code this}
         */
        public ZipContentCodecBuilder withCompressionLevel(final int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        /**
         * Returns the compression level for this codec.
         * @return return the compression level set for this codec.
         */
        protected int compressionLevel() {
            return compressionLevel;
        }

        protected int maxChunkSize() {
            return maxChunkSize;
        }

        public ContentCodecBuilder maxChunkSize(final int maxChunkSize) {
            if (maxChunkSize <= 0) {
                throw new IllegalArgumentException("maxChunkSize: " + maxChunkSize + " (expected > 0)");
            }

            this.maxChunkSize = maxChunkSize;
            return this;
        }
    }

    static final class GzipContentCodecBuilder extends ZipContentCodecBuilder {

        public static final CharSequence GZIP = newAsciiString("gzip");

        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec(GZIP,
                    () -> new JdkZlibEncoder(ZlibWrapper.GZIP, compressionLevel()),
                    () -> new JdkZlibDecoder(ZlibWrapper.GZIP, maxChunkSize())
            );
        }
    }

    static final class DeflateContentCodecBuilder extends ZipContentCodecBuilder {

        public static final CharSequence DEFLATE = newAsciiString("deflate");

        @Override
        public ContentCodec build() {
            return new NettyChannelContentCodec(DEFLATE,
                    () -> new JdkZlibEncoder(ZlibWrapper.ZLIB, compressionLevel()),
                    () -> new JdkZlibDecoder(ZlibWrapper.ZLIB, maxChunkSize())
            );
        }
    }
}
