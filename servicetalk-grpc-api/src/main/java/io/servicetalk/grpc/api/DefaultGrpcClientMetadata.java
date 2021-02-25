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

import io.servicetalk.encoding.api.ContentCodec;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.encoding.api.ContentCodings.identity;

/**
 * Default implementation for {@link DefaultGrpcClientMetadata}.
 */
public class DefaultGrpcClientMetadata extends DefaultGrpcMetadata implements GrpcClientMetadata {

    @Nullable
    private final GrpcExecutionStrategy strategy;

    private final ContentCodec requestEncoding;

    @Nullable
    private final Duration deadline;

    /**
     * Creates a new instance which uses the provided path and the default execution strategy, no content codec
     * (identity), or no deadline.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    protected DefaultGrpcClientMetadata(final String path) {
        this(path, (GrpcExecutionStrategy) null);
    }

    /**
     * Creates a new instance which uses the provided path and content codec, the default execution strategy,
     * and no deadline.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    protected DefaultGrpcClientMetadata(final String path, final ContentCodec requestEncoding) {
        this(path, null, requestEncoding);
    }

    /**
     * Creates a new instance which uses the provided path, content codec and deadline and the default execution
     * strategy.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param deadline A timeout deadline after which the response is no longer wanted.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        final ContentCodec requestEncoding,
                                        final Duration deadline) {
        this(path, null, requestEncoding, deadline);
    }

    /**
     * Creates a new instance which uses the provided path and execution strategy and the default content codec
     * (identity) and no deadline.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy) {
        this(path, strategy, identity(), null);
    }

    /**
     * Creates a new instance which uses the provided path, content codec and the default execution
     * strategy and no deadline.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final ContentCodec requestEncoding) {
        this(path, strategy, requestEncoding, null);
    }

    /**
     * Creates a new instance which uses the provided path, content codec and deadline and the default execution
     * strategy.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param deadline A timeout deadline after which the response is no longer wanted.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final ContentCodec requestEncoding,
                                        @Nullable final Duration deadline) {
        super(path);
        this.strategy = strategy;
        this.requestEncoding = requestEncoding;
        this.deadline = deadline;
    }

    @Override
    public final GrpcExecutionStrategy strategy() {
        return strategy;
    }

    @Override
    public ContentCodec requestEncoding() {
        return requestEncoding;
    }

    @Override
    @Nullable
    public Duration deadline() {
        return deadline;
    }
}
