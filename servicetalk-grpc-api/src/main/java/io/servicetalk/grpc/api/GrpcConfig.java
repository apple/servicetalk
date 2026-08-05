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
     * semantics of the sign and the special value {@code 0}.
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

        private int maxInboundMessageSize;

        Builder(final int defaultMaxInboundMessageSize) {
            // package private constructor to prevent extension. The subtype supplies its role's built-in default.
            this.maxInboundMessageSize = defaultMaxInboundMessageSize;
        }

        /**
         * Set the maximum size, in bytes, of a decoded inbound gRPC message. The sign selects the mode and the
         * magnitude the threshold: a <em>positive</em> value enforces the limit &mdash; a message whose declared length
         * exceeds it is rejected with {@link GrpcStatusCode#RESOURCE_EXHAUSTED} before its payload is buffered, and for
         * a compressed message the limit is also applied to the decoded size &mdash; a <em>negative</em> value enables
         * warn-only mode at {@code abs(value)} bytes (oversized messages are still delivered, but a rate-limited
         * warning is logged), and {@code 0} disables the limit. Memory used while decompressing is bounded separately
         * by the codec's own decompressed-bytes cap, not by this limit. By default the client warns at 64 MiB and the
         * server warns at 16 MiB; enforcing is planned to become the default for servers in a future release.
         *
         * @param maxInboundMessageSize the maximum inbound message size in bytes: positive enforces at that size,
         * negative warns at its magnitude, {@code 0} disables the limit
         * @return {@code this}.
         */
        public final B maxInboundMessageSize(final int maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
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
