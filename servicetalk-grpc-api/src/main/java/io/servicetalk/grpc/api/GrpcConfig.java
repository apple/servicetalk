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
package io.servicetalk.grpc.api;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;

/**
 * Base <a href="https://www.grpc.io">gRPC</a> configuration shared by the client
 * ({@link GrpcClientCallConfig}) and server ({@link GrpcServiceConfig}) binding entry points.
 */
public abstract class GrpcConfig {

    private final int maxInboundMessageSize;

    GrpcConfig(final int maxInboundMessageSize) {
        this.maxInboundMessageSize = maxInboundMessageSize;
    }

    /**
     * Returns the maximum inbound message size in bytes. See {@link Builder#maxInboundMessageSize(int)} for the
     * semantics of the special value {@code 0}.
     *
     * @return the maximum inbound message size in bytes.
     */
    public final int maxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    /**
     * Base builder for {@link GrpcConfig} subtypes.
     *
     * @param <B> the concrete builder type returned by fluent setters.
     */
    public abstract static class Builder<B extends Builder<B>> {

        private int maxInboundMessageSize = GrpcMessageSizeLimiter.DEFAULT_MAX_INBOUND_MESSAGE_SIZE;

        Builder() {
            // package private constructor to prevent extension.
        }

        /**
         * Set the maximum size, in bytes, of a decoded inbound gRPC message. A message whose declared length exceeds
         * the limit is rejected with {@link GrpcStatusCode#RESOURCE_EXHAUSTED} before its payload is buffered; for a
         * compressed message the limit is also applied to the decoded size. Memory used while decompressing is bounded
         * separately by the codec's own decompressed-bytes cap, not by this limit. Defaults to 4 MiB (matching
         * grpc-java), or to the value of the {@code io.servicetalk.grpc.netty.temporaryDefaultMaxInboundMessageSize}
         * system property when it is set.
         *
         * @param maxInboundMessageSize the maximum inbound message size in bytes: {@code 0} disables the limit and any
         * positive value enforces it. Must be non-negative.
         * @return {@code this}.
         * @throws IllegalArgumentException if {@code maxInboundMessageSize < 0}
         */
        public final B maxInboundMessageSize(final int maxInboundMessageSize) {
            this.maxInboundMessageSize = ensureNonNegative(maxInboundMessageSize, "maxInboundMessageSize");
            return thisBuilder();
        }

        /**
         * The configured maximum inbound message size, for use by a subclass {@code build()}.
         *
         * @return the configured maximum inbound message size in bytes.
         */
        protected final int maxInboundMessageSize() {
            return maxInboundMessageSize;
        }

        /**
         * Returns {@code this} typed as the concrete builder, so shared fluent setters return the right type.
         *
         * @return {@code this} as the concrete builder type.
         */
        protected abstract B thisBuilder();
    }
}
