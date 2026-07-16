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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.lang.Integer.getInteger;

/**
 * Resolves and validates the {@code maxInboundMessageSize} configured on the gRPC client/server builders. The builder
 * API accepts {@code 0} (disables) or a positive value (enforces) and passes it as-is to {@code servicetalk-grpc-api};
 * the builders own these knobs (mirroring {@code HttpConfig}). The temporary default system property additionally
 * accepts {@code -1} to select warn-only mode globally, which the builder API does not expose.
 */
final class GrpcMessageSizeUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcMessageSizeUtils.class);

    // 4 MiB, matching grpc-java's io.grpc.internal.GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE.
    static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE_VALUE = 4 * 1024 * 1024;
    // Magic value accepted by maxInboundMessageSize(int): warn (rate-limited) when the limit is exceeded but let the
    // message through rather than rejecting it.
    static final int WARN_ONLY_MAX_INBOUND_MESSAGE_SIZE = -1;
    // A unary gRPC message is a single frame: a 5-byte header (1 compression flag + 4-byte length) plus the message.
    static final int GRPC_FRAME_HEADER_BYTES = 5;
    // FIXME: 0.43 - remove this temporary property
    static final String DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.grpc.netty.temporaryDefaultMaxInboundMessageSize";
    static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE;

    static {
        final int value = getInteger(DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, DEFAULT_MAX_INBOUND_MESSAGE_SIZE_VALUE);
        // The property additionally supports the warn-only selector (-1), which the builder API does not expose; only
        // values below it are invalid. Don't throw from this static initializer; fall back to the default instead.
        if (value < WARN_ONLY_MAX_INBOUND_MESSAGE_SIZE) {
            LOGGER.warn("-D{}: {} is invalid (expected >= {}). Falling back to the default of {} bytes.",
                    DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, value, WARN_ONLY_MAX_INBOUND_MESSAGE_SIZE,
                    DEFAULT_MAX_INBOUND_MESSAGE_SIZE_VALUE);
            DEFAULT_MAX_INBOUND_MESSAGE_SIZE = DEFAULT_MAX_INBOUND_MESSAGE_SIZE_VALUE;
        } else {
            DEFAULT_MAX_INBOUND_MESSAGE_SIZE = value;
            if (value != DEFAULT_MAX_INBOUND_MESSAGE_SIZE_VALUE) {
                LOGGER.warn("-D{}: {}. This property will be removed in the future releases. Configure this value " +
                                "per client/server builder via maxInboundMessageSize(int) instead.",
                        DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, value);
            }
        }
    }

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
