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
package io.servicetalk.http.api;

/**
 * Builder of {@link StreamingContentCoding}
 */
abstract class StreamingContentCodingBuilder {

    public static final int DEFAULT_MAX_ALLOWED_COMPRESSED_PAYLOAD = 2 << 20; //2MiB
    private static final int CHUNK_SIZE = 1 << 10; //1KiB

    protected int maxAllowedPayloadSize = DEFAULT_MAX_ALLOWED_COMPRESSED_PAYLOAD;

    /**
     * Sets the maximum allowed compressed payload size that the codec can process.
     * This can help prevent malicious attempts to decompress malformed payloads that can drain resources of the
     * running instance.
     *
     * @param maxAllowedPayloadSize the maximum allowed payload size
     * @return {@code this}
     * @see <a href="https://en.wikipedia.org/wiki/Zip_bomb">Zip Bomb</a>
     */
    public StreamingContentCodingBuilder setMaxAllowedPayloadSize(final int maxAllowedPayloadSize) {
        this.maxAllowedPayloadSize = maxAllowedPayloadSize;
        return this;
    }

    abstract StreamingContentCoding build();

    static class GzipStreamingContentCodingBuilder extends StreamingContentCodingBuilder {
        @Override
        StreamingContentCoding build() {
            return new GzipContentCoding(CHUNK_SIZE, maxAllowedPayloadSize);
        }
    }

    static class DeflateStreamingContentCodingBuilder extends StreamingContentCodingBuilder {
        @Override
        StreamingContentCoding build() {
            return new DeflateContentCoding(CHUNK_SIZE, maxAllowedPayloadSize);
        }
    }
}
