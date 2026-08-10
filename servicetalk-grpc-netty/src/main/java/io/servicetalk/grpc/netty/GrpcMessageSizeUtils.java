/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.grpc.api.GrpcClientCallConfig;
import io.servicetalk.grpc.api.GrpcExecutionContext;
import io.servicetalk.grpc.api.GrpcExecutionStrategy;
import io.servicetalk.grpc.api.GrpcServiceConfig;
import io.servicetalk.transport.api.IoExecutor;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;

/**
 * Coordinates the gRPC {@code maxInboundMessageSize} with the underlying HTTP transport's aggregation limit. The
 * resolved per-role default is parsed once in {@code servicetalk-grpc-api} and read back via
 * {@link #DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE} / {@link #DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE}; an unset builder
 * limit falls back to it so oversized unary messages stay bounded even once the default becomes enforcing.
 */
final class GrpcMessageSizeUtils {

    // A unary gRPC message is a single frame: a 5-byte header (1 compression flag + 4-byte length) plus the message.
    static final int GRPC_FRAME_HEADER_BYTES = 5;
    // Read back from default-built configs (properties are parsed once, in servicetalk-grpc-api). The size is
    // independent of the execution context, so the server default is read via a placeholder context.
    static final int DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE =
            new GrpcClientCallConfig.Builder().build().maxInboundMessageSize();
    static final int DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE =
            new GrpcServiceConfig.Builder().executionContext(PlaceholderGrpcExecutionContext.INSTANCE).build()
                    .maxInboundMessageSize();

    private GrpcMessageSizeUtils() {
        // No instances.
    }

    /**
     * Compute the HTTP {@code maxAggregatedPayloadSize} to apply on the underlying transport so oversized
     * <em>unary</em> (aggregated) messages are rejected before the whole body is buffered. Streaming calls are deframed
     * incrementally and are unaffected by the HTTP aggregation bound.
     * <p>
     * Only enforced when the gRPC limit is enforcing ({@code maxInboundMessageSize > 0}); for disabled ({@code 0}) or
     * warn-only ({@code < 0}) the HTTP aggregation bound is left disabled ({@code 0}) so those modes don't turn into a
     * hard reject at the HTTP layer. A single-frame unary body is the {@link #GRPC_FRAME_HEADER_BYTES 5-byte frame
     * header} plus the message, so the header is added on top of the message-size limit (saturating at
     * {@link Integer#MAX_VALUE}) to let a maximum-size message through.
     *
     * @param maxInboundMessageSize the configured maximum inbound message size ({@code 0}/{@code < 0}/{@code > 0})
     * @return the HTTP {@code maxAggregatedPayloadSize} to apply, or {@code 0} to leave it disabled
     */
    static int httpAggregationLimitFor(final int maxInboundMessageSize) {
        return maxInboundMessageSize <= 0 ? 0 :
                addWithOverflowProtection(maxInboundMessageSize, GRPC_FRAME_HEADER_BYTES);
    }

    /**
     * A placeholder {@link GrpcExecutionContext} used only to build a default {@link GrpcServiceConfig} and read its
     * (context-independent) {@code maxInboundMessageSize}; its accessors are never invoked.
     */
    private static final class PlaceholderGrpcExecutionContext implements GrpcExecutionContext {
        static final GrpcExecutionContext INSTANCE = new PlaceholderGrpcExecutionContext();

        @Override
        public BufferAllocator bufferAllocator() {
            throw notUsed();
        }

        @Override
        public IoExecutor ioExecutor() {
            throw notUsed();
        }

        @Override
        public Executor executor() {
            throw notUsed();
        }

        @Override
        public GrpcExecutionStrategy executionStrategy() {
            throw notUsed();
        }

        private static UnsupportedOperationException notUsed() {
            return new UnsupportedOperationException("Placeholder: accessors must never be invoked");
        }
    }
}
