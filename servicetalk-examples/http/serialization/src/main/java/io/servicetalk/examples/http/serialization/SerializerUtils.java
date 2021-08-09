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
package io.servicetalk.examples.http.serialization;

import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.http.api.HttpStreamingSerializerDeserializer;

import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;
import static io.servicetalk.http.api.HttpSerializers.jsonSerializer;
import static io.servicetalk.http.api.HttpSerializers.jsonStreamingSerializer;

/**
 * Utilities to cache POJO to JSON serializer instances.
 */
public final class SerializerUtils {
    public static final HttpSerializerDeserializer<CreatePojoRequest> REQ_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(CreatePojoRequest.class));

    public static final HttpStreamingSerializerDeserializer<CreatePojoRequest> REQ_STREAMING_SERIALIZER =
            jsonStreamingSerializer(JACKSON.streamingSerializerDeserializer(CreatePojoRequest.class));

    public static final HttpSerializerDeserializer<PojoResponse> RESP_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(PojoResponse.class));

    public static final HttpStreamingSerializerDeserializer<PojoResponse> RESP_STREAMING_SERIALIZER =
            jsonStreamingSerializer(JACKSON.streamingSerializerDeserializer(PojoResponse.class));

    private SerializerUtils() {
    }
}
