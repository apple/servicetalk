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

import io.servicetalk.encoding.api.ContentCodec;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.encoding.api.Identity.identity;

/**
 * Default implementation for {@link DefaultGrpcClientMetadata}.
 */
public class DefaultGrpcClientMetadata extends DefaultGrpcMetadata implements GrpcClientMetadata {

    @Nullable
    private final GrpcExecutionStrategy strategy;

    private final ContentCodec requestEncoding;

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    protected DefaultGrpcClientMetadata(final String path) {
        this(path, (GrpcExecutionStrategy) null, identity(), INFINITE_TIMEOUT);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    protected DefaultGrpcClientMetadata(final String path, final ContentCodec requestEncoding) {
        this(path, null, requestEncoding, INFINITE_TIMEOUT);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        final ContentCodec requestEncoding,
                                        final Duration timeout) {
        this(path, null, requestEncoding, timeout);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy) {
        this(path, strategy, identity(), INFINITE_TIMEOUT);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
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
        this(path, strategy, requestEncoding, INFINITE_TIMEOUT);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        final Duration timeout) {
        this(path, null, identity(), timeout);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final Duration timeout) {
        this(path, strategy, identity(), timeout);
    }

    /**
     * Creates a new instance which uses the provided path, execution strategy, content codec, and timeout.
     *
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final ContentCodec requestEncoding,
                                        final Duration timeout) {
        super(path, timeout);
        this.strategy = strategy;
        this.requestEncoding = requestEncoding;
    }

    @Override
    public final GrpcExecutionStrategy strategy() {
        return strategy;
    }

    @Override
    public ContentCodec requestEncoding() {
        return requestEncoding;
    }
}
