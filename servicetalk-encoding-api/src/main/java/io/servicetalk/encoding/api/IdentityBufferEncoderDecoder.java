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
package io.servicetalk.encoding.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.BlockingIterables;
import io.servicetalk.oio.api.PayloadWriter;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingDeserializer;
import io.servicetalk.serializer.api.StreamingSerializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

final class IdentityBufferEncoderDecoder implements BufferEncoderDecoder {
    private static final CharSequence IDENTITY_NAME = newAsciiString("identity");
    private static final int HASH_CODE = caseInsensitiveHashCode(IDENTITY_NAME);
    static final BufferEncoderDecoder INSTANCE = new IdentityBufferEncoderDecoder();

    private IdentityBufferEncoderDecoder() {
    }

    @Override
    public Deserializer<Buffer> decoder() {
        return NoopBufferSerializer.INSTANCE;
    }

    @Override
    public StreamingDeserializer<Buffer> streamingDecoder() {
        return NoopStreamingBufferSerializer.INSTANCE;
    }

    @Override
    public Serializer<Buffer> encoder() {
        return NoopBufferSerializer.INSTANCE;
    }

    @Override
    public StreamingSerializer<Buffer> streamingEncoder() {
        return NoopStreamingBufferSerializer.INSTANCE;
    }

    @Override
    public CharSequence encodingName() {
        return IDENTITY_NAME;
    }

    @Override
    public String toString() {
        return IDENTITY_NAME.toString();
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                o instanceof BufferEncoderDecoder &&
                        contentEqualsIgnoreCase(encodingName(), ((BufferEncoderDecoder) o).encodingName());
    }

    @Override
    public int hashCode() {
        return HASH_CODE;
    }

    private static final class NoopBufferSerializer implements SerializerDeserializer<Buffer> {
        private static final SerializerDeserializer<Buffer> INSTANCE = new NoopBufferSerializer();

        private NoopBufferSerializer() {
        }

        @Override
        public Buffer deserialize(final Buffer serializedData, final BufferAllocator allocator) {
            return serializedData;
        }

        @Override
        public void serialize(final Buffer toSerialize, final BufferAllocator allocator, final Buffer buffer) {
            if (toSerialize != buffer) {
                buffer.writeBytes(toSerialize);
            }
        }

        @Override
        public Buffer serialize(Buffer toSerialize, BufferAllocator allocator) {
            return toSerialize;
        }

        @Override
        public String toString() {
            return IDENTITY_NAME.toString();
        }
    }

    private static final class NoopStreamingBufferSerializer implements StreamingSerializerDeserializer<Buffer> {
        private static final StreamingSerializerDeserializer<Buffer> INSTANCE = new NoopStreamingBufferSerializer();
        private NoopStreamingBufferSerializer() {
        }

        @Override
        public Publisher<Buffer> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
            return serializedData;
        }

        @Override
        public BlockingIterable<Buffer> deserialize(final Iterable<Buffer> serializedData,
                                                    final BufferAllocator allocator) {
            return BlockingIterables.from(serializedData);
        }

        @Override
        public Publisher<Buffer> serialize(final Publisher<Buffer> toSerialize, final BufferAllocator allocator) {
            return toSerialize;
        }

        @Override
        public BlockingIterable<Buffer> serialize(final Iterable<Buffer> toSerialize, final BufferAllocator allocator) {
            return BlockingIterables.from(toSerialize);
        }

        @Override
        public PayloadWriter<Buffer> serialize(final PayloadWriter<Buffer> writer, final BufferAllocator allocator) {
            return writer;
        }

        @Override
        public String toString() {
            return IDENTITY_NAME.toString();
        }
    }
}
