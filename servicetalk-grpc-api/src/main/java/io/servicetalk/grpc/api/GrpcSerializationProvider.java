/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpSerializer;

import java.util.Set;

/**
 * A provider for <a href="https://www.grpc.io">gRPC</a> serialization/deserialization.
 */
public interface GrpcSerializationProvider {

    Set<GrpcMessageEncoding> supportedEncodings();

    /**
     * Get a {@link HttpSerializer} for a {@link Class} of type {@link T}.
     *
     * @param <T> The type of object to serialize.
     * @param encoding {@link GrpcMessageEncoding} for the serializer.
     * @param type The {@link Class} type that the returned {@link HttpSerializer} can serialize.
     * @return a {@link HttpSerializer} for a {@link Class} of type {@link T}.
     */
    <T> HttpSerializer<T> serializerFor(GrpcMessageEncoding encoding, Class<T> type);

    /**
     * Get a {@link HttpDeserializer} for a {@link Class} of type {@link T}.
     *
     * @param messageEncoding {@link GrpcMessageEncoding} for the deserializer.
     * @param type The {@link Class} type that the return value will deserialize.
     * @param <T> The type of object to deserialize.
     *
     * @return a {@link HttpDeserializer} for a {@link Class} of type {@link T}.
     */
    <T> HttpDeserializer<T> deserializerFor(GrpcMessageEncoding messageEncoding, Class<T> type);
}
