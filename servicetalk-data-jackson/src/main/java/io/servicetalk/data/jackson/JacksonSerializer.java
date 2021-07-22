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
package io.servicetalk.data.jackson;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.SerializerDeserializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;

import static io.servicetalk.buffer.api.Buffer.asOutputStream;

/**
 * Serializes and deserializes to/from JSON via jackson.
 * @param <T> The type of objects to serialize.
 */
final class JacksonSerializer<T> implements SerializerDeserializer<T> {
    private final ObjectWriter writer;
    private final ObjectReader reader;

    JacksonSerializer(ObjectMapper mapper, Class<T> clazz) {
        writer = mapper.writerFor(clazz);
        reader = mapper.readerFor(clazz);
    }

    JacksonSerializer(ObjectMapper mapper, TypeReference<T> typeRef) {
        writer = mapper.writerFor(typeRef);
        reader = mapper.readerFor(typeRef);
    }

    JacksonSerializer(ObjectMapper mapper, JavaType type) {
        writer = mapper.writerFor(type);
        reader = mapper.readerFor(type);
    }

    @Override
    public void serialize(final T toSerialize, final BufferAllocator allocator, final Buffer buffer) {
        doSerialize(writer, toSerialize, buffer);
    }

    @Override
    public T deserialize(final Buffer serializedData, final BufferAllocator allocator) {
        try {
            return reader.readValue(Buffer.asInputStream(serializedData));
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    static <T> void doSerialize(final ObjectWriter writer, T t, Buffer destination) {
        try {
            writer.writeValue(asOutputStream(destination), t);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }
}
