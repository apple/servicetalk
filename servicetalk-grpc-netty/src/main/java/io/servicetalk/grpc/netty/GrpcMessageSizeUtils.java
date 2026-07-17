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

import io.servicetalk.grpc.api.GrpcClientCallConfig;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;

/**
 * Validates the {@code maxInboundMessageSize} configured on the gRPC client/server builders and coordinates it with the
 * underlying HTTP transport. The builder API accepts {@code 0} (disables) or a positive value (enforces); the default
 * (including the temporary-system-property override and its warn-only {@code -1} selector) is resolved by
 * {@code servicetalk-grpc-api} and read back through {@link #DEFAULT_MAX_INBOUND_MESSAGE_SIZE}, so the property is
 * parsed in exactly one place.
 */
final class GrpcMessageSizeUtils {

    // A unary gRPC message is a single frame: a 5-byte header (1 compression flag + 4-byte length) plus the message.
    static final int GRPC_FRAME_HEADER_BYTES = 5;
    // The default maxInboundMessageSize a builder applies when the user sets none. Read from a default-built config so
    // the temporary system property is resolved solely in servicetalk-grpc-api (GrpcConfig). May be -1 (warn-only).
    static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE =
            new GrpcClientCallConfig.Builder().build().maxInboundMessageSize();

    private GrpcMessageSizeUtils() {
        // No instances.
    }

    /**
     * Validate a user-supplied {@code maxInboundMessageSize} for a builder. The builder API does not expose the
     * warn-only selector ({@code -1}); that is reachable only via the default system property.
     *
     * @param maxInboundMessageSize the configured value
     * @return the validated value
     */
    static int validateMaxInboundMessageSize(final int maxInboundMessageSize) {
        return ensureNonNegative(maxInboundMessageSize, "maxInboundMessageSize");
    }

    /**
     * Compute the HTTP {@code maxAggregatedPayloadSize} to apply on the underlying transport so oversized
     * <em>unary</em> (aggregated) messages are rejected before the whole body is buffered. Streaming calls are deframed
     * incrementally and are unaffected by the HTTP aggregation bound.
     * <p>
     * Only enforced when the gRPC limit is enforcing ({@code maxInboundMessageSize > 0}); for disabled ({@code 0}) or
     * warn-only ({@code -1}) the HTTP aggregation bound is left disabled ({@code 0}) so those modes don't turn into a
     * hard reject at the HTTP layer. A single-frame unary body is the {@link #GRPC_FRAME_HEADER_BYTES 5-byte frame
     * header} plus the message, so the header is added on top of the message-size limit (saturating at
     * {@link Integer#MAX_VALUE}) to let a maximum-size message through.
     *
     * @param maxInboundMessageSize the configured maximum inbound message size ({@code 0}/{@code -1}/{@code > 0})
     * @return the HTTP {@code maxAggregatedPayloadSize} to apply, or {@code 0} to leave it disabled
     */
    static int httpAggregationLimitFor(final int maxInboundMessageSize) {
        return maxInboundMessageSize <= 0 ? 0 :
                addWithOverflowProtection(maxInboundMessageSize, GRPC_FRAME_HEADER_BYTES);
    }
}
