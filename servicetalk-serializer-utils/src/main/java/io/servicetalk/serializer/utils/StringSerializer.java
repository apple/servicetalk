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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Serialize/deserialize {@link String}s encoded with a {@link Charset}.
 */
public final class StringSerializer extends AbstractStringSerializer {
    private static final SerializerDeserializer<String> UTF_8_SERIALIZER = new AbstractStringSerializer(UTF_8) {
        @Override
        public void serialize(final String toSerialize, final BufferAllocator allocator, final Buffer buffer) {
            buffer.writeUtf8(toSerialize);
        }
    };
    private static final SerializerDeserializer<String> US_ASCII_SERIALIZER = new AbstractStringSerializer(US_ASCII) {
        @Override
        public void serialize(final String toSerialize, final BufferAllocator allocator, final Buffer buffer) {
            buffer.writeAscii(toSerialize);
        }
    };

    /**
     * Create a new instance.
     * @param charset The charset used for encoding.
     */
    private StringSerializer(final Charset charset) {
        super(charset);
    }

    /**
     * Create a new instance.
     * @param charset The charset used for encoding.
     * @return A serializer that uses {@code charset} for encoding.
     */
    public static SerializerDeserializer<String> stringSerializer(final Charset charset) {
        if (UTF_8.equals(charset)) {
            return UTF_8_SERIALIZER;
        } else if (US_ASCII.equals(charset)) {
            return US_ASCII_SERIALIZER;
        }
        return new StringSerializer(charset);
    }
}
