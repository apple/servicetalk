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
package io.servicetalk.http.router.jersey.resources;

import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;

final class SerializerUtils {
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE =
            new TypeReference<Map<String, Object>>() { };
    private static final TypeReference<Map<String, String>> MAP_STRING_STRING_TYPE =
            new TypeReference<Map<String, String>>() { };
    static final SerializerDeserializer<Map<String, Object>> MAP_STRING_OBJECT_SERIALIZER =
            JACKSON.serializerDeserializer(STRING_OBJECT_MAP_TYPE);
    static final StreamingSerializerDeserializer<Map<String, Object>> MAP_STRING_OBJECT_STREAM_SERIALIZER =
            JACKSON.streamingSerializerDeserializer(STRING_OBJECT_MAP_TYPE);
    static final SerializerDeserializer<Map<String, String>> MAP_STRING_STRING_SERIALIZER =
            JACKSON.serializerDeserializer(MAP_STRING_STRING_TYPE);

    private SerializerUtils() {
    }
}
