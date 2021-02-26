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

/**
 * Description of a parameter or return value related to a {@link MethodDescriptor}.
 * @param <T> The type of the parameter.
 */
public interface ParameterDescriptor<T> {
    /**
     * Determine if the parameter type is streaming or scalar.
     * @return {@code true} if the parameter is streaming. {@code false} if the parameter is scalar.
     */
    boolean isStreaming();

    /**
     * Determine if the parameter type is asynchronous.
     * @return {@code true} if the parameter type is asynchronous. {@code false} if the parameter type is synchronous.
     */
    boolean isAsync();

    /**
     * Get the java {@link Class} for the parameter type.
     * @return the java {@link Class} for the parameter type.
     */
    Class<T> parameterClass();

    /**
     * Get the {@link SerializerDescriptor} for this parameter.
     * @return the {@link SerializerDescriptor} for this parameter.
     */
    SerializerDescriptor<T> serializerDescriptor();
}
