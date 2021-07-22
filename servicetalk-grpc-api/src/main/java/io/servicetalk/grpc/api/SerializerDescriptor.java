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
package io.servicetalk.grpc.api;

import io.servicetalk.serializer.api.SerializerDeserializer;

import java.util.function.ToIntFunction;

/**
 * Description of the serialization used for individual elements related to a {@link ParameterDescriptor}.
 * @param <T> The type being serialized.
 */
public interface SerializerDescriptor<T> {
    /**
     * Get the suffix to <b>application/grpc</b> which described the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">Content-Type</a> for the
     * serialization. For example: <b>+proto</b>.
     * @return the suffix to <b>application/grpc</b> which described the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">Content-Type</a> for the
     * serialization. For example: <b>+proto</b>.
     */
    CharSequence contentType();

    /**
     * Get the {@link SerializerDeserializer} used to serialize and deserialize each object.
     * @return the {@link SerializerDeserializer} used to serialize and deserialize each object.
     */
    SerializerDeserializer<T> serializer();

    /**
     * Get a function used to estimate the serialized size (in bytes) of each object. This is used to provide an
     * estimate to pre-allocate memory to serialize into.
     * @return a function used to estimate the serialized size (in bytes) of each object.
     */
    ToIntFunction<T> bytesEstimator();
}
