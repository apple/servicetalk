/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.serialization.api;

import io.servicetalk.buffer.api.Buffer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * A provider of serialization implementation for {@link Serializer}.
 * @deprecated General {@link Type} serialization is not supported by all serializers. Defer
 * to your specific {@link io.servicetalk.serializer.api.Serializer} implementation.
 */
@Deprecated
public interface SerializationProvider {

    /**
     * Provide a {@link StreamingSerializer} instance that can serialize instances of {@link T}.
     *
     * @param classToSerialize The class for {@link T}, the object to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return An instance of {@link StreamingSerializer} that can serialize instances of {@link T}.
     */
    <T> StreamingSerializer getSerializer(Class<T> classToSerialize);

    /**
     * Provide a {@link StreamingSerializer} instance that can serialize instances of {@link T}.
     *
     * @param typeToSerialize {@link TypeHolder} holding the {@link ParameterizedType} to be serialized.
     * @param <T> The data type to serialize.
     *
     * @return An instance of {@link StreamingSerializer} that can serialize instances of {@link T}.
     */
    <T> StreamingSerializer getSerializer(TypeHolder<T> typeToSerialize);

    /**
     * Serializes the passed object {@code toSerialize} to the passed {@link Buffer}.
     *
     * @param toSerialize Object to serialize.
     * @param destination The {@link Buffer} to which the serialized representation of {@code toSerialize} is written.
     * @param <T> The data type to serialize.
     */
    default <T> void serialize(final T toSerialize, final Buffer destination) {
        getSerializer(toSerialize.getClass()).serialize(toSerialize, destination);
    }

    /**
     * Provide a {@link StreamingDeserializer} instance that can deserialize instances of {@link T}.
     *
     * @param classToDeSerialize The class for {@link T}, the object to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return An instance of {@link StreamingDeserializer} that can deserialize instances of {@link T}.
     */
    <T> StreamingDeserializer<T> getDeserializer(Class<T> classToDeSerialize);

    /**
     * Provide a {@link StreamingDeserializer} instance that can deserialize instances of {@link T}.
     *
     * @param typeToDeserialize {@link TypeHolder} holding the {@link ParameterizedType} to be deserialized.
     * @param <T> The data type to deserialize.
     *
     * @return An instance of {@link StreamingDeserializer} that can deserialize instances of {@link T}.
     */
    <T> StreamingDeserializer<T> getDeserializer(TypeHolder<T> typeToDeserialize);
}
