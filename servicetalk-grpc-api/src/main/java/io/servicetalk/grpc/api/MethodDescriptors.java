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
 * Utility methods for {@link MethodDescriptor}.
 */
public final class MethodDescriptors {
    private MethodDescriptors() {
    }

    /**
     * Create a new {@link MethodDescriptor}.
     * @param httpPath See {@link MethodDescriptor#httpPath()}.
     * @param javaMethodName See {@link MethodDescriptor#javaMethodName()}.
     * @param reqIsStreaming {@link ParameterDescriptor#isStreaming()} for the request.
     * @param reqIsAsync {@link ParameterDescriptor#isAsync()} for the request.
     * @param reqClass {@link ParameterDescriptor#parameterClass()} for the request.
     * @param reqContentType {@link SerializerDescriptor#contentType()} for the request.
     * @param reqSerializer {@link SerializerDescriptor#serializer()} for the request.
     * @param reqBytesEstimator {@link SerializerDescriptor#bytesEstimator()} for the request.
     * @param respIsStreaming {@link ParameterDescriptor#isStreaming()} for the response.
     * @param respIsAsync {@link ParameterDescriptor#isAsync()} for the response.
     * @param respClass {@link ParameterDescriptor#parameterClass()} for the response.
     * @param respContentType {@link SerializerDescriptor#contentType()} for the response.
     * @param respSerializer {@link SerializerDescriptor#serializer()} for the response.
     * @param respBytesEstimator {@link SerializerDescriptor#bytesEstimator()} for the response.
     * @param <Req> The request type.
     * @param <Resp> The response type.
     * @return A {@link MethodDescriptor} as described by all parameters.
     */
    public static <Req, Resp> MethodDescriptor<Req, Resp> newMethodDescriptor(
            final String httpPath, final String javaMethodName, final boolean reqIsStreaming, final boolean reqIsAsync,
            final Class<Req> reqClass, final CharSequence reqContentType,
            final SerializerDeserializer<Req> reqSerializer, final ToIntFunction<Req> reqBytesEstimator,
            final boolean respIsStreaming, final boolean respIsAsync, final Class<Resp> respClass,
            final CharSequence respContentType, final SerializerDeserializer<Resp> respSerializer,
            final ToIntFunction<Resp> respBytesEstimator) {
        return new GrpcUtils.DefaultMethodDescriptor<>(httpPath, javaMethodName, reqIsStreaming, reqIsAsync, reqClass,
                reqContentType, reqSerializer, reqBytesEstimator, respIsStreaming, respIsAsync, respClass,
                respContentType, respSerializer, respBytesEstimator);
    }
}
