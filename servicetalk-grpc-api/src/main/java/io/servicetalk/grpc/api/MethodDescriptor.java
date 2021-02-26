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
 * Metadata that describes a specific RPC method.
 * @param <Req> The type of request.
 * @param <Resp> The type of response.
 */
public interface MethodDescriptor<Req, Resp> {
    /**
     * Get the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">HTTP Path</a> used by this
     * method.
     * @return The
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">HTTP Path</a> used by this
     * method.
     */
    String httpPath();

    /**
     * Get the java method name corresponding to this method.
     * @return the java method name corresponding to this method.
     */
    String javaMethodName();

    /**
     * Get the {@link ParameterDescriptor} for the request.
     * @return the {@link ParameterDescriptor} for the request.
     */
    ParameterDescriptor<Req> requestDescriptor();

    /**
     * Get the {@link ParameterDescriptor} for the response.
     * @return the {@link ParameterDescriptor} for the response.
     */
    ParameterDescriptor<Resp> responseDescriptor();
}
