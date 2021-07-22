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
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.StreamingDeserializer;

/**
 * Convert from {@link ContentCodec} to {@link BufferDecoder}.
 * @deprecated Use {@link BufferDecoder}. This type will be removed along with {@link ContentCodec}.
 */
@Deprecated
public final class ContentCodecToBufferDecoder implements BufferDecoder {
    private final CharSequence name;
    private final ContentCodecToDeserializer deserializer;

    /**
     * Create a new instance.
     * @param codec The codec to convert.
     */
    public ContentCodecToBufferDecoder(final ContentCodec codec) {
        this.name = codec.name();
        deserializer = new ContentCodecToDeserializer(codec);
    }

    @Override
    public Deserializer<Buffer> decoder() {
        return deserializer;
    }

    @Override
    public StreamingDeserializer<Buffer> streamingDecoder() {
        return deserializer;
    }

    @Override
    public CharSequence encodingName() {
        return name;
    }

    private static final class ContentCodecToDeserializer implements
                                                          Deserializer<Buffer>, StreamingDeserializer<Buffer> {
        private final ContentCodec codec;

        private ContentCodecToDeserializer(final ContentCodec codec) {
            this.codec = codec;
        }

        @Override
        public Buffer deserialize(final Buffer serializedData, final BufferAllocator allocator) {
            return codec.decode(serializedData, allocator);
        }

        @Override
        public Publisher<Buffer> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
            return codec.decode(serializedData, allocator);
        }
    }
}
