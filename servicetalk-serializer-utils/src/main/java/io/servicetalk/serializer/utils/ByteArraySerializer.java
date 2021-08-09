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
package io.servicetalk.serializer.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.serializer.api.SerializerDeserializer;

/**
 * Serialize/deserialize {@code byte[]}.
 */
public final class ByteArraySerializer implements SerializerDeserializer<byte[]> {
    private static final SerializerDeserializer<byte[]> BYTE_SERIALIZER = new ByteArraySerializer(false);
    private static final SerializerDeserializer<byte[]> BYTE_SERIALIZER_COPY = new ByteArraySerializer(true);

    private final boolean forceCopy;

    private ByteArraySerializer(boolean forceCopy) {
        this.forceCopy = forceCopy;
    }

    /**
     * Create a new instance.
     * @param forceCopy {@code true} means that data will always be copied from {@link Buffer} memory. {@code false}
     * means that if {@link Buffer#hasArray()} is {@code true} and the array offsets are aligned the result of
     * serialization doesn't have to be copied.
     * @return A serializer that produces/consumes {@code byte[]}.
     */
    public static SerializerDeserializer<byte[]> byteArraySerializer(boolean forceCopy) {
        return forceCopy ? BYTE_SERIALIZER_COPY : BYTE_SERIALIZER;
    }

    @Override
    public byte[] deserialize(final Buffer serializedData, final BufferAllocator allocator) {
        // First try to return the raw underlying array, otherwise fallback to copy.
        byte[] result;
        if (!forceCopy && serializedData.hasArray() && serializedData.arrayOffset() == 0 &&
                (result = serializedData.array()).length == serializedData.readableBytes()) {
            serializedData.skipBytes(result.length);
            return result;
        }
        result = new byte[serializedData.readableBytes()];
        serializedData.readBytes(result);
        return result;
    }

    @Override
    public Buffer serialize(byte[] toSerialize, BufferAllocator allocator) {
        return allocator.wrap(toSerialize);
    }

    @Override
    public void serialize(final byte[] toSerialize, BufferAllocator allocator, final Buffer buffer) {
        buffer.writeBytes(toSerialize);
    }
}
