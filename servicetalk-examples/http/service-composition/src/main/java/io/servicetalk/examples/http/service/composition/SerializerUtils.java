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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.examples.http.service.composition.pojo.FullRecommendation;
import io.servicetalk.examples.http.service.composition.pojo.Metadata;
import io.servicetalk.examples.http.service.composition.pojo.Rating;
import io.servicetalk.examples.http.service.composition.pojo.Recommendation;
import io.servicetalk.examples.http.service.composition.pojo.User;
import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.http.api.HttpStreamingSerializerDeserializer;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;
import static io.servicetalk.http.api.HttpSerializers.jsonSerializer;
import static io.servicetalk.http.api.HttpSerializers.jsonStreamingSerializer;

public final class SerializerUtils {
    private static final TypeReference<List<Recommendation>> RECOMMEND_LIST_TYPE =
            new TypeReference<List<Recommendation>>() { };
    private static final TypeReference<List<FullRecommendation>> FULL_RECOMMEND_LIST_TYPE =
            new TypeReference<List<FullRecommendation>>() { };
    public static final HttpSerializerDeserializer<User> USER_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(User.class));
    public static final HttpSerializerDeserializer<Rating> RATING_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(Rating.class));
    public static final HttpSerializerDeserializer<Metadata> METADATA_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(Metadata.class));
    public static final HttpSerializerDeserializer<List<Recommendation>> RECOMMEND_LIST_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(RECOMMEND_LIST_TYPE));
    public static final HttpSerializerDeserializer<List<FullRecommendation>> FULL_RECOMMEND_LIST_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(FULL_RECOMMEND_LIST_TYPE));
    public static final HttpStreamingSerializerDeserializer<Recommendation> RECOMMEND_STREAM_SERIALIZER =
            jsonStreamingSerializer(JACKSON.streamingSerializerDeserializer(Recommendation.class));
    public static final HttpStreamingSerializerDeserializer<FullRecommendation> FULL_RECOMMEND_STREAM_SERIALIZER =
            jsonStreamingSerializer(JACKSON.streamingSerializerDeserializer(FullRecommendation.class));

    public static final String USER_ID_QP_NAME = "userId";
    public static final String ENTITY_ID_QP_NAME = "entityId";

    private SerializerUtils() {
    }
}
