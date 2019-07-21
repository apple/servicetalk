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

import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Metadata for a <a href="https://www.grpc.io">gRPC</a> call.
 */
public interface GrpcMetadata {

    /**
     * Returns the path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     *
     * @return The path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    String path();

    /**
     * Returns {@code true} if the associated message is to be sent compressed.
     *
     * @return {@code true} if the associated message is to be sent compressed.
     */
    boolean isCompressed();

    /**
     * The associated message will be sent compressed if {@code compressed} is {@code true}.
     *
     * @param compressed {@code true} if the associated message is to be sent compressed.
     */
    void compressed(boolean compressed);

    /**
     * Returns {@code GrpcMessageEncoding} for any message that is to be sent {@link #isCompressed() compressed}.
     * {@code null} if none specified.
     *
     * @return {@code GrpcMessageEncoding} for any message that is to be sent {@link #isCompressed() compressed}.
     * {@code null} if none specified.
     */
    @Nullable
    GrpcMessageEncoding messageEncoding();

    /**
     * Sets {@code GrpcMessageEncoding} for any message that is to be sent {@link #isCompressed() compressed}.
     *
     * @param messageEncoding {@code GrpcMessageEncoding} for any message that is to be sent
     * {@link #isCompressed() compressed}.
     */
    void messageEncoding(GrpcMessageEncoding messageEncoding);

    /**
     * Returns a <a href="https://grpc.io/blog/deadlines/">gRPC deadline</a>, if set for the associated RPC method.
     *
     * @return {@link Duration} representing the <a href="https://grpc.io/blog/deadlines/">gRPC deadline</a> or
     * {@code null} if none exists.
     */
    @Nullable
    Duration deadline();
}
