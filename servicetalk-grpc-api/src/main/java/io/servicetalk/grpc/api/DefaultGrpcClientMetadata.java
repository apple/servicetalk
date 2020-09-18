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

import static io.servicetalk.grpc.api.GrpcMessageEncodings.NONE;
/**
 * Default implementation for {@link DefaultGrpcClientMetadata}.
 */
public class DefaultGrpcClientMetadata extends DefaultGrpcMetadata implements GrpcClientMetadata {

    @Nullable
    private final GrpcExecutionStrategy strategy;

    private final GrpcMessageEncoding requestEncoding;

    /**
     * Creates a new instance.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    protected DefaultGrpcClientMetadata(final String path) {
        this(path, (GrpcExecutionStrategy) null);
    }

    protected DefaultGrpcClientMetadata(final String path, final GrpcMessageEncoding requestEncoding) {
        this(path, null, requestEncoding);
    }

    /**
     * Creates a new instance.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy) {
        this(path, strategy, NONE);
    }

    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final GrpcMessageEncoding requestEncoding) {
        super(path);
        this.strategy = strategy;
        this.requestEncoding = requestEncoding;
    }

    @Override
    public final GrpcExecutionStrategy strategy() {
        return strategy;
    }

    @Override
    public GrpcMessageEncoding requestEncoding() {
        return requestEncoding;
    }
}
