/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.api;

@Deprecated
abstract class DefaultContentCodecBuilder implements ContentCodecBuilder {

    private static final int CHUNK_SIZE = 1 << 10; //1KiB
    private static final int DEFAULT_MAX_ALLOWED_DECOMPRESSED_PAYLOAD = 16 << 20; //16MiB

    private int maxAllowedPayloadSize = DEFAULT_MAX_ALLOWED_DECOMPRESSED_PAYLOAD;

    protected int maxAllowedPayloadSize() {
        return maxAllowedPayloadSize;
    }

    @Override
    public ContentCodecBuilder setMaxAllowedPayloadSize(final int maxAllowedPayloadSize) {
        if (maxAllowedPayloadSize <= 0) {
            throw new IllegalArgumentException("maxAllowedPayloadSize: " + maxAllowedPayloadSize + " (expected > 0)");
        }

        this.maxAllowedPayloadSize = maxAllowedPayloadSize;
        return this;
    }

    static final class GzipContentCodecBuilder extends DefaultContentCodecBuilder {
        @Override
        public ContentCodec build() {
            return new GzipContentCodec(CHUNK_SIZE, maxAllowedPayloadSize());
        }
    }

    static final class DeflateContentCodecBuilder extends DefaultContentCodecBuilder {
        @Override
        public ContentCodec build() {
            return new DeflateContentCodec(CHUNK_SIZE, maxAllowedPayloadSize());
        }
    }
}
