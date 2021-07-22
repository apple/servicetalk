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

/**
 * Metadata for a <a href="https://www.grpc.io">gRPC</a> call.
 */
public interface GrpcMetadata {

    /**
     * Returns the path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @deprecated Use {@link MethodDescriptor#httpPath()}.
     * @return The path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    @Deprecated
    String path();
}
