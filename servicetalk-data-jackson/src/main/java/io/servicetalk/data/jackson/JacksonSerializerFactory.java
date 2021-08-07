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

import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches instances of {@link SerializerDeserializer} and {@link StreamingSerializerDeserializer} for
 * <a href="https://github.com/FasterXML/jackson">jackson</a>.
 */
public final class JacksonSerializerFactory {
    /**
     * Singleton instance which creates <a href="https://github.com/FasterXML/jackson">jackson</a> serializers.
     */
    public static final JacksonSerializerFactory JACKSON = new JacksonSerializerFactory();
    private final ObjectMapper mapper;
    @SuppressWarnings("rawtypes")
    private final Map<Object, StreamingSerializerDeserializer> streamingSerializerMap;
    @SuppressWarnings("rawtypes")
    private final Map<Object, SerializerDeserializer> serializerMap;

    /**
     * Create a new instance.
     */
    private JacksonSerializerFactory() {
        this(new ObjectMapper());
    }

    /**
     * Create a new instance.
     * @param mapper {@link ObjectMapper} to use.
     */
    public JacksonSerializerFactory(final ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        streamingSerializerMap = new ConcurrentHashMap<>();
        serializerMap = new ConcurrentHashMap<>();
    }

    /**
     * Get a {@link SerializerDeserializer}.
     * @param clazz The class to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link SerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T> SerializerDeserializer<T> serializerDeserializer(final Class<T> clazz) {
        return serializerMap.computeIfAbsent(clazz, clazz2 -> new JacksonSerializer<>(mapper, (Class<T>) clazz2));
    }

    /**
     * Get a {@link SerializerDeserializer}.
     * @param typeRef The type reference to serialize and deserialize (captures generic type arguments at runtime).
     * @param <T> The type to serialize and deserialize.
     * @return a {@link SerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T> SerializerDeserializer<T> serializerDeserializer(final TypeReference<T> typeRef) {
        return serializerMap.computeIfAbsent(typeRef, typeRef2 ->
                new JacksonSerializer<>(mapper, (TypeReference<T>) typeRef2));
    }

    /**
     * Get a {@link SerializerDeserializer}.
     * @param type The type to serialize and deserialize (captures generic type arguments at runtime).
     * @param <T> The type to serialize and deserialize.
     * @return a {@link SerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T> SerializerDeserializer<T> serializerDeserializer(final JavaType type) {
        return serializerMap.computeIfAbsent(type, type2 -> new JacksonSerializer<>(mapper, (JavaType) type2));
    }

    /**
     * Get a {@link StreamingSerializerDeserializer}.
     * @param clazz The class to serialize and deserialize.
     * @param <T> The type to serialize and deserialize.
     * @return a {@link StreamingSerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T> StreamingSerializerDeserializer<T> streamingSerializerDeserializer(final Class<T> clazz) {
        return streamingSerializerMap.computeIfAbsent(clazz, clazz2 ->
                new JacksonStreamingSerializer<>(mapper, (Class<T>) clazz2));
    }

    /**
     * Get a {@link StreamingSerializerDeserializer}.
     * @param typeRef The type reference to serialize and deserialize (captures generic type arguments at runtime).
     * @param <T> The type to serialize and deserialize.
     * @return a {@link StreamingSerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T> StreamingSerializerDeserializer<T> streamingSerializerDeserializer(final TypeReference<T> typeRef) {
        return streamingSerializerMap.computeIfAbsent(typeRef, typeRef2 ->
                new JacksonStreamingSerializer<>(mapper, (TypeReference<T>) typeRef2));
    }

    /**
     * Get a {@link StreamingSerializerDeserializer}.
     * @param type The type to serialize and deserialize (captures generic type arguments at runtime).
     * @param <T> The type to serialize and deserialize.
     * @return a {@link StreamingSerializerDeserializer}.
     */
    @SuppressWarnings("unchecked")
    public <T> StreamingSerializerDeserializer<T> streamingSerializerDeserializer(final JavaType type) {
        return streamingSerializerMap.computeIfAbsent(type, type2 ->
                new JacksonStreamingSerializer<>(mapper, (JavaType) type2));
    }
}
