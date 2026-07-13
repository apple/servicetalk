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
 * Configuration of <a href="https://www.grpc.io">gRPC</a> message handling, shared by the client
 * ({@link GrpcClientCallFactory}) and server ({@link GrpcServiceFactory}) binding entry points.
 *
 * @see Builder
 */
public final class GrpcMessageConfig {

    private final int maxInboundMessageSize;

    private GrpcMessageConfig(final int maxInboundMessageSize) {
        this.maxInboundMessageSize = maxInboundMessageSize;
    }

    /**
     * Returns the maximum inbound message size in bytes. See {@link Builder#maxInboundMessageSize(int)} for the
     * semantics of the special values {@code 0} and {@code -1}.
     *
     * @return the maximum inbound message size in bytes.
     */
    public int maxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{maxInboundMessageSize=" + maxInboundMessageSize + '}';
    }

    /**
     * Builder for {@link GrpcMessageConfig}.
     */
    public static final class Builder {

        private int maxInboundMessageSize = GrpcMessageSizeLimiter.DEFAULT_MAX_MESSAGE_SIZE;

        /**
         * Set the maximum size, in bytes, of a decoded inbound gRPC message. A message whose declared length exceeds
         * the limit is rejected with {@link GrpcStatusCode#RESOURCE_EXHAUSTED} before its payload is buffered. For
         * compressed messages the limit is also applied to the decompressed size, aborting decompression mid-inflate
         * for built-in codecs. Defaults to 4 MiB (matching grpc-java); the client/server builders may override this
         * default via a system property, but that override does not apply to a config built directly.
         *
         * @param maxInboundMessageSize the maximum inbound message size in bytes: {@code 0} disables the limit,
         * {@code > 0} enforces it, and {@code -1} enables warn-only mode (a rate-limited warning is logged instead of
         * rejecting).
         * @return {@code this}.
         * @throws IllegalArgumentException if {@code maxInboundMessageSize < -1}
         */
        public Builder maxInboundMessageSize(final int maxInboundMessageSize) {
            if (maxInboundMessageSize < -1) {
                throw new IllegalArgumentException(
                        "maxInboundMessageSize: " + maxInboundMessageSize + " (expected >= -1)");
            }
            this.maxInboundMessageSize = maxInboundMessageSize;
            return this;
        }

        /**
         * Builds a new {@link GrpcMessageConfig}.
         *
         * @return a new {@link GrpcMessageConfig}.
         */
        public GrpcMessageConfig build() {
            return new GrpcMessageConfig(maxInboundMessageSize);
        }
    }
}
