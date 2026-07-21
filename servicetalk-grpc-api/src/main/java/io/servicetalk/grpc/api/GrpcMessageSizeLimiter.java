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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static java.lang.Integer.getInteger;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Bounds the size of a single inbound gRPC message before it is buffered/deserialized. Created once per client/server
 * and shared across all of its deframers, so the warn-only throttle state below is naturally scoped per client/server.
 * <p>
 * The deframer invokes {@link #accept(long)} with the declared message length (read from the gRPC frame's length
 * prefix) before any bytes are buffered toward that length, and again with the decoded length of a compressed message.
 * Enforcing mode rejects oversized messages with a {@link GrpcStatusException} carrying
 * {@link GrpcStatusCode#RESOURCE_EXHAUSTED} (matching grpc-java). Decompression memory itself is bounded separately by
 * the codec's own decompressed-bytes cap, independent of this limit.
 */
final class GrpcMessageSizeLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcMessageSizeLimiter.class);
    private static final long WARN_INTERVAL_NANOS = MINUTES.toNanos(5);

    /**
     * A no-op limiter that never rejects or warns, regardless of message size.
     */
    static final GrpcMessageSizeLimiter NONE = new GrpcMessageSizeLimiter(Mode.DISABLED, 0, null);

    // maxInboundMessageSize value selecting warn-only mode (see forMaxInboundMessageSize), and the built-in default.
    private static final int WARN_ONLY = -1;
    // The 4 MiB warn-only threshold, matching grpc-java's io.grpc.internal.GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE.
    private static final int DEFAULT_MAX_MESSAGE_SIZE = 4 * 1024 * 1024;
    // FIXME: 0.43 - remove this temporary property
    private static final String DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.grpc.netty.temporaryDefaultMaxInboundMessageSize";
    static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE = resolveDefault();

    private enum Mode { DISABLED, ENFORCING, WARN_ONLY }

    private final Mode mode;
    private final int maxMessageSize;
    // Non-null if this is a warn-only limiter.
    @Nullable
    private final AtomicLong lastWarnNanos;
    // Non-null if this is a warn-only limiter.
    @Nullable
    private final AtomicLong maxObservedSize;
    // Identifies the owning client/server in the warning (client-side: the StreamingHttpClient, whose toString carries
    // the target address); null when not warn-only or when unavailable (server-side).
    @Nullable
    private final Object owner;
    @Nullable
    private final Throwable constructionSite;

    private GrpcMessageSizeLimiter(final Mode mode, final int maxMessageSize, @Nullable final Object owner) {
        this.mode = mode;
        this.maxMessageSize = maxMessageSize;
        // Seed in the past so the first time the limit is exceeded a warning is emitted immediately.
        this.lastWarnNanos = mode == Mode.WARN_ONLY ? new AtomicLong(nanoTime() - WARN_INTERVAL_NANOS) : null;
        this.maxObservedSize = mode == Mode.WARN_ONLY ? new AtomicLong() : null;
        this.owner = mode == Mode.WARN_ONLY ? owner : null;
        this.constructionSite = mode == Mode.WARN_ONLY ? new Throwable(
                "Client/server with a warn-only maxInboundMessageSize created here (not an error)") : null;
    }

    /**
     * Build a limiter from the {@code maxInboundMessageSize} value configured on the client/server:
     * {@code 0} disables the limit, {@code > 0} enforces (rejects) at that many bytes, and {@code -1} warns (without
     * rejecting) at the built-in default limit.
     *
     * @param maxInboundMessageSize the configured maximum inbound message size
     * @return a limiter, or {@link #NONE} when {@code maxInboundMessageSize == 0}
     * @throws IllegalArgumentException if {@code maxInboundMessageSize < -1}
     */
    static GrpcMessageSizeLimiter forMaxInboundMessageSize(final int maxInboundMessageSize) {
        return forMaxInboundMessageSize(maxInboundMessageSize, null);
    }

    /**
     * Variant of {@link #forMaxInboundMessageSize(int)} that records {@code owner} to identify the client/server in
     * the warn-only log. The client passes its {@link io.servicetalk.http.api.StreamingHttpClient} (whose
     * {@code toString()} carries the target address); the server has no equivalent handle and passes {@code null},
     * relying on the construction stack instead.
     */
    static GrpcMessageSizeLimiter forMaxInboundMessageSize(final int maxInboundMessageSize,
                                                           @Nullable final Object owner) {
        if (maxInboundMessageSize == 0) {
            return NONE;
        }
        if (maxInboundMessageSize > 0) {
            return new GrpcMessageSizeLimiter(Mode.ENFORCING, maxInboundMessageSize, null);
        }
        // A negative value reaches here only as the property-derived default; the builder/config API rejects
        // negatives up front, so the sole legal negative is the warn-only selector.
        if (maxInboundMessageSize != WARN_ONLY) {
            throw new IllegalArgumentException("maxInboundMessageSize: " + maxInboundMessageSize +
                    " (expected >= " + WARN_ONLY + ')');
        }
        return new GrpcMessageSizeLimiter(Mode.WARN_ONLY, DEFAULT_MAX_MESSAGE_SIZE, owner);
    }

    /**
     * Invoked with the declared length (in bytes) of an inbound gRPC message before it is buffered. In enforcing mode
     * throws when the declared length exceeds the limit; in warn-only mode emits a rate-limited warning and returns
     * normally so deserialization can continue.
     *
     * @param messageSize the declared length of the message about to be buffered
     */
    void accept(final long messageSize) {
        accept(messageSize, false);
    }

    /**
     * Invoked with the length (in bytes) of an inbound gRPC message. In enforcing mode throws when the length exceeds
     * the limit; in warn-only mode emits a rate-limited warning and returns normally so deserialization can continue.
     *
     * @param messageSize the length of the message
     * @param decompressed {@code true} when {@code messageSize} is the decoded length checked after decompression,
     * {@code false} when it is the declared (on-wire) length checked before buffering; only affects the rejection
     * message, distinguishing the two like grpc-java does
     */
    void accept(final long messageSize, final boolean decompressed) {
        if (mode == Mode.DISABLED || messageSize <= maxMessageSize) {
            return;
        }
        if (mode == Mode.ENFORCING) {
            throw new GrpcStatusException(new GrpcStatus(RESOURCE_EXHAUSTED,
                    (decompressed ? "Decompressed gRPC message size=" : "gRPC message size=") + messageSize +
                            " exceeds maximum inbound message size=" + maxMessageSize));
        }
        maybeWarn(messageSize);
    }

    private static int resolveDefault() {
        // Warn-only by default unless the temporary property overrides it. The property also supports the warn-only
        // selector (-1), which the builder/config API does not expose; only values below it are invalid. Fall back to
        // warn-only rather than failing at class-load.
        final int value = getInteger(DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, WARN_ONLY);
        if (value < WARN_ONLY) {
            LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: The value is invalid (expected >= {}). Falling back to " +
                            "warn-only mode at {} bytes.",
                    DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, value, WARN_ONLY, DEFAULT_MAX_MESSAGE_SIZE);
            return WARN_ONLY;
        }
        // getInteger can't distinguish "unset" (the warn-only default) from an explicit value; only warn about the
        // temporary property when it is actually set.
        if (System.getProperty(DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY) != null) {
            if (value == WARN_ONLY) {
                LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: Setting this property to -1 (warn-only mode) may be " +
                                "used temporarily to unblock deployment but exposes the service to the risk of " +
                                "aggregating unbounded amount of data on the heap. Configure appropriate value per " +
                                "client/server builder via maxInboundMessageSize(int) instead.",
                        DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, value);
            } else {
                LOGGER.warn("-D{}={} This property will be removed in the future releases. Configure this value per " +
                                "client/server builder via maxInboundMessageSize(int) instead.",
                        DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, value);
            }
        }
        return value;
    }

    private void maybeWarn(final long messageSize) {
        assert lastWarnNanos != null;
        assert maxObservedSize != null;
        assert constructionSite != null;
        final long maxObserved = maxObservedSize.accumulateAndGet(messageSize, Math::max);
        final long now = nanoTime();
        final long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            LOGGER.warn("gRPC message size={} exceeded the configured maximum inbound message size of {} bytes{}, " +
                    "but the limit is configured in warn-only mode so the message is allowed through. Largest " +
                    "message observed so far is {} bytes. Configure an enforcing maxInboundMessageSize(int) to " +
                    "reject oversized messages with RESOURCE_EXHAUSTED. This warning is rate-limited to once per 5 " +
                    "minutes per client/server.", messageSize, maxMessageSize, owner == null ? "" : " for " + owner,
                    maxObserved, constructionSite);
        }
    }
}
