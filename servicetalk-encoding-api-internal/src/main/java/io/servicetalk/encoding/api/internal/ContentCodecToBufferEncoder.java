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
package io.servicetalk.encoding.api.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;

/**
 * Convert from {@link ContentCodec} to {@link BufferEncoder}.
 * @deprecated Use {@link BufferEncoder}. This type will be removed along with {@link ContentCodec}.
 */
@Deprecated
public final class ContentCodecToBufferEncoder implements BufferEncoder {
    private final CharSequence name;
    private final ContentCodecToSerializer serializer;

    /**
     * Create a new instance.
     * @param codec The codec to convert.
     */
    public ContentCodecToBufferEncoder(final ContentCodec codec) {
        this.name = codec.name();
        serializer = new ContentCodecToSerializer(codec);
    }

    @Override
    public Serializer<Buffer> encoder() {
        return serializer;
    }

    @Override
    public StreamingSerializer<Buffer> streamingEncoder() {
        return serializer;
    }

    @Override
    public CharSequence encodingName() {
        return name;
    }

    private static final class ContentCodecToSerializer implements Serializer<Buffer>, StreamingSerializer<Buffer> {
        private final ContentCodec codec;

        private ContentCodecToSerializer(final ContentCodec codec) {
            this.codec = codec;
        }

        @Override
        public void serialize(final Buffer toSerialize, final BufferAllocator allocator, final Buffer buffer) {
            buffer.writeBytes(codec.encode(toSerialize, allocator));
        }

        @Override
        public Buffer serialize(Buffer toSerialize, BufferAllocator allocator) {
            return codec.encode(toSerialize, allocator);
        }

        @Override
        public Publisher<Buffer> serialize(final Publisher<Buffer> toSerialize, final BufferAllocator allocator) {
            return codec.encode(toSerialize, allocator);
        }
    }
}
