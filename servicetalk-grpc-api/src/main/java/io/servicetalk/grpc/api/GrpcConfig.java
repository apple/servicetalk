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
         * Set the maximum size, in bytes, of a decoded inbound gRPC message. The sign selects the mode, the magnitude
         * the threshold: <em>positive</em> enforces &mdash; a message whose declared length exceeds it is rejected with
         * {@link GrpcStatusCode#RESOURCE_EXHAUSTED} before buffering (also applied to a compressed message's decoded
         * size); <em>negative</em> warns at {@code abs(value)} bytes (oversized messages still delivered, with a
         * rate-limited warning); {@code 0} disables it. Decompression memory is bounded separately by the codec's
         * decompressed-bytes cap. By default the client warns at 64 MiB and the server at 16 MiB; server enforcing is
         * planned to become the default in a future release.
         *
         * @param maxInboundMessageSize bytes: positive enforces, negative warns at its magnitude, {@code 0} disables
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
