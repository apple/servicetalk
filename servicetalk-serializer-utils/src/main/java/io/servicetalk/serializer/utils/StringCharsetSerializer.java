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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Serialize/deserialize {@link String}s encoded with a {@link Charset}.
 * @see StringAsciiSerializer
 * @see StringUtf8Serializer
 */
public final class StringCharsetSerializer implements SerializerDeserializer<String> {
    private static final Map<Charset, Integer> MAX_BYTES_PER_CHAR_MAP = new HashMap<>();
    static {
        for (Charset charset : Charset.availableCharsets().values()) {
            MAX_BYTES_PER_CHAR_MAP.put(charset, (int) charset.newEncoder().maxBytesPerChar());
        }
    }

    private final Charset charset;
    private final int maxBytesPerChar;

    /**
     * Create a new instance.
     * @param charset The charset used for encoding.
     */
    public StringCharsetSerializer(final Charset charset) {
        this.charset = charset;
        maxBytesPerChar = MAX_BYTES_PER_CHAR_MAP.getOrDefault(charset, 1);
    }

    @Override
    public String deserialize(final Buffer serializedData, final BufferAllocator allocator) {
        String result = serializedData.toString(charset);
        serializedData.skipBytes(serializedData.readableBytes());
        return result;
    }

    @Override
    public Buffer serialize(String toSerialize, BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(toSerialize.length() * maxBytesPerChar);
        serialize(toSerialize, allocator, buffer);
        return buffer;
    }

    @Override
    public void serialize(final String toSerialize, final BufferAllocator allocator, final Buffer buffer) {
        buffer.writeCharSequence(toSerialize, charset);
    }
}
