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

import static java.util.Objects.requireNonNull;

class DefaultGrpcMetadata implements GrpcMetadata {

    private final String path;

    /**
     * Construct a new instance.
     *
     * @param path The path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    DefaultGrpcMetadata(final String path) {
        this.path = requireNonNull(path, "path");
    }

    @Override
    public String path() {
        return path;
    }
}
