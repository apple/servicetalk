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

abstract class DefaultStreamingContentCodecBuilder implements StreamingContentCodecBuilder {

    private static final int CHUNK_SIZE = 1 << 10; //1KiB

    protected int maxAllowedPayloadSize = DEFAULT_MAX_ALLOWED_DECOMPRESSED_PAYLOAD;

    @Override
    public StreamingContentCodecBuilder setMaxAllowedPayloadSize(final int maxAllowedPayloadSize) {
        this.maxAllowedPayloadSize = maxAllowedPayloadSize;
        return this;
    }

    static class GzipStreamingContentCodecBuilder extends DefaultStreamingContentCodecBuilder {
        @Override
        public StreamingContentCodec build() {
            return new GzipContentCodec(CHUNK_SIZE, maxAllowedPayloadSize);
        }
    }

    static class DeflateStreamingContentCodecBuilder extends DefaultStreamingContentCodecBuilder {
        @Override
        public StreamingContentCodec build() {
            return new DeflateContentCodec(CHUNK_SIZE, maxAllowedPayloadSize);
        }
    }
}
