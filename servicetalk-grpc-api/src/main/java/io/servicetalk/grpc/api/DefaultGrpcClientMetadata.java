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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation for {@link DefaultGrpcClientMetadata}.
 */
public class DefaultGrpcClientMetadata extends DefaultGrpcMetadata implements GrpcClientMetadata {

    @Nullable
    private GrpcExecutionStrategy strategy;

    /**
     * Creates a new instance.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    protected DefaultGrpcClientMetadata(final String path) {
        super(path);
    }

    @Override
    public GrpcExecutionStrategy strategy() {
        return strategy;
    }

    @Override
    public void strategy(final GrpcExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
    }
}
