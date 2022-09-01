/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;

/**
 * Metadata for a <a href="https://www.grpc.io">gRPC</a> call.
 */
public interface GrpcMetadata {

    /**
     * Returns the path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @return The path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @deprecated Use {@link MethodDescriptor#httpPath()}.
     */
    @Deprecated
    String path();

    /**
     * A request context associated with this {@link GrpcMetadata} that translates into
     * {@link HttpRequestMetaData#context()}.
     * <p>
     * Context can be used to associate a state with a request without serializing its state on the wire or transmit a
     * state between HTTP and gRPC layers.
     *
     * @return a request context associated with this {@link GrpcMetadata} that translates into
     * {@link HttpRequestMetaData#context()}.
     */
    default ContextMap requestContext() {
        throw new UnsupportedOperationException("GrpcMetadata#requestContext() is not supported by " + getClass());
    }

    /**
     * A response context associated with this {@link GrpcMetadata} that translates into
     * {@link HttpResponseMetaData#context()}.
     * <p>
     * Context can be used to associate a state with a response without serializing its state on the wire or transmit a
     * state between HTTP and gRPC layers.
     *
     * @return a response context associated with this {@link GrpcMetadata} that translates into
     * {@link HttpResponseMetaData#context()}.
     */
    default ContextMap responseContext() {
        throw new UnsupportedOperationException("GrpcMetadata#responseContext() is not supported by " + getClass());
    }
}
