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

import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;

import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Metadata for a <a href="https://www.grpc.io">gRPC</a> client call.
 */
public interface GrpcClientMetadata extends GrpcMetadata {

    /**
     * {@link GrpcExecutionStrategy} to use for the associated
     * <a href="https://www.grpc.io">gRPC</a> method.
     *
     * @return {@link GrpcExecutionStrategy} to use for the associated
     * <a href="https://www.grpc.io">gRPC</a> method.
     */
    @Nullable
    GrpcExecutionStrategy strategy();

    /**
     * {@link ContentCodec} to use for the associated
     * <a href="https://www.grpc.io">gRPC</a> method.
     * @deprecated Use {@link #requestCompressor()}.
     * @return {@link ContentCodec} to use for the associated
     * <a href="https://www.grpc.io">gRPC</a> method.
     */
    @Deprecated
    ContentCodec requestEncoding();

    /**
     * Get the {@link BufferEncoder} to use to compress the request associated with this object.
     * @return the {@link BufferEncoder} to use to compress the request associated with this object.
     */
    @Nullable
    BufferEncoder requestCompressor();

    /**
     * Returns timeout duration after which the response is no longer wanted.
     *
     * @return {@link Duration} of associated timeout or null for no timeout
     * @see <a href="https://grpc.io/blog/deadlines/">gRPC Deadlines</a>
     */
    @Nullable
    Duration timeout();
}
