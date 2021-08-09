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

import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.internal.ContentCodecToBufferEncoder;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_MAX_TIMEOUT;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation for {@link DefaultGrpcClientMetadata}.
 */
public class DefaultGrpcClientMetadata extends DefaultGrpcMetadata implements GrpcClientMetadata {
    private static final String UNKNOWN_PATH = "";
    public static final GrpcClientMetadata INSTANCE = new DefaultGrpcClientMetadata();
    @Nullable
    private final GrpcExecutionStrategy strategy;
    @Nullable
    private final BufferEncoder requestEncoder;
    @Deprecated
    private final ContentCodec requestEncoding;

    /**
     * Timeout for an individual request or null for no timeout.
     */
    @Nullable
    private final Duration timeout;

    private DefaultGrpcClientMetadata() {
        this((GrpcExecutionStrategy) null, null, null);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata()}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path) {
        this(path, null, identity(), null);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(BufferEncoder)}
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path, final ContentCodec requestEncoding) {
        this(path, null, requestEncoding, null);
    }

    /**
     * Creates a new instance.
     * @param requestEncoding Used to compress the request.
     */
    public DefaultGrpcClientMetadata(final BufferEncoder requestEncoding) {
        this(null, requestEncoding, null);
    }

    /**
     * Create a new instance.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    public DefaultGrpcClientMetadata(final Duration timeout) {
        this(null, (BufferEncoder) null, timeout);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(BufferEncoder, Duration)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        final ContentCodec requestEncoding,
                                        final Duration timeout) {
        this(path, null, requestEncoding, timeout);
    }

    /**
     * Creates a new instance.
     * @param requestEncoding Used to compress the request.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    public DefaultGrpcClientMetadata(final BufferEncoder requestEncoding, final Duration timeout) {
        this(null, requestEncoding, timeout);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(GrpcExecutionStrategy, BufferEncoder)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy) {
        this(path, strategy, identity(), null);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(GrpcExecutionStrategy, BufferEncoder)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final ContentCodec requestEncoding) {
        this(path, strategy, requestEncoding, null);
    }

    /**
     * Create a new instance.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * call.
     * @param requestEncoding Used to compress the request.
     */
    public DefaultGrpcClientMetadata(@Nullable final GrpcExecutionStrategy strategy,
                                     final BufferEncoder requestEncoding) {
        this(strategy, requestEncoding, null);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(BufferEncoder, Duration)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final Duration timeout) {
        this(path, null, identity(), timeout);
    }

    /**
     * Creates a new instance using provided parameters and defaults for
     * {@link #DefaultGrpcClientMetadata(String, GrpcExecutionStrategy, ContentCodec, Duration)}.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(GrpcExecutionStrategy, BufferEncoder, Duration)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        @Nullable final Duration timeout) {
        this(path, strategy, identity(), timeout);
    }

    /**
     * Creates a new instance which uses the provided path, execution strategy, content codec, and timeout.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(GrpcExecutionStrategy, BufferEncoder, Duration)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param requestEncoding {@link ContentCodec} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * method.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        @Nullable final GrpcExecutionStrategy strategy,
                                        final ContentCodec requestEncoding,
                                        @Nullable final Duration timeout) {
        super(path);
        this.strategy = strategy;
        this.requestEncoding = requireNonNull(requestEncoding);
        this.requestEncoder = requestEncoding == identity() ? null : new ContentCodecToBufferEncoder(requestEncoding);
        if (null != timeout) {
            ensurePositive(timeout, "timeout");
        }
        this.timeout = isInfinite(timeout, GRPC_MAX_TIMEOUT) ? null : timeout;
    }

    /**
     * Create a new instance.
     * @deprecated Use {@link #DefaultGrpcClientMetadata(DefaultGrpcClientMetadata)}.
     * @param path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param rhs Copy everything except the path from this object.
     */
    @Deprecated
    protected DefaultGrpcClientMetadata(final String path,
                                        final GrpcClientMetadata rhs) {
        super(path);
        this.strategy = rhs.strategy();
        this.requestEncoding = rhs.requestEncoding();
        this.requestEncoder = rhs.requestCompressor();
        this.timeout = rhs.timeout();
    }

    /**
     * Create a new instance, by copying an existing instance.
     * @param rhs Right hand side to copy from.
     */
    protected DefaultGrpcClientMetadata(final DefaultGrpcClientMetadata rhs) {
        super(UNKNOWN_PATH);
        this.strategy = rhs.strategy;
        this.requestEncoding = rhs.requestEncoding();
        this.requestEncoder = rhs.requestCompressor();
        this.timeout = rhs.timeout();
    }

    /**
     * Create a new instance.
     * @param strategy {@link GrpcExecutionStrategy} to use for the associated <a href="https://www.grpc.io">gRPC</a>
     * call.
     * @param requestEncoding Used to compress the request.
     * @param timeout A timeout after which the response is no longer wanted.
     */
    public DefaultGrpcClientMetadata(@Nullable final GrpcExecutionStrategy strategy,
                                     @Nullable final BufferEncoder requestEncoding,
                                     @Nullable final Duration timeout) {
        super(UNKNOWN_PATH);
        this.strategy = strategy;
        this.requestEncoding = identity();
        this.requestEncoder = requestEncoding;
        if (null != timeout) {
            ensurePositive(timeout, "timeout");
        }
        this.timeout = isInfinite(timeout, GRPC_MAX_TIMEOUT) ? null : timeout;
    }

    @Override
    public final GrpcExecutionStrategy strategy() {
        return strategy;
    }

    @Deprecated
    @Override
    public ContentCodec requestEncoding() {
        return requestEncoding;
    }

    @Nullable
    @Override
    public BufferEncoder requestCompressor() {
        return requestEncoder;
    }

    @Override
    @Nullable
    public Duration timeout() {
        return timeout;
    }
}
