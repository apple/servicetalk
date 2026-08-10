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

import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.Role.CLIENT;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
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
    static final GrpcMessageSizeLimiter NONE = new GrpcMessageSizeLimiter(Mode.DISABLED, 0, null, null);

    // grpc-java enforces at 4 MiB by default (io.grpc.internal.GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE). ServiceTalk instead
    // warns (rate-limited) by default at larger, per-role thresholds and lets the message through, easing rollout of a
    // limit; a value set programmatically via the builder is always definitive.
    private static final int DEFAULT_CLIENT_MAX_MESSAGE_SIZE = 64 * 1024 * 1024;
    private static final int DEFAULT_SERVER_MAX_MESSAGE_SIZE = 16 * 1024 * 1024;
    private static final String DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.grpc.netty.defaultClientMaxInboundMessageSize";
    private static final String DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.grpc.netty.defaultServerMaxInboundMessageSize";
    // Deprecated legacy property, superseded by the client/server-specific properties above; kept for a release or
    // two in case a deployment already relies on it. A role-specific property, when set, takes precedence over it.
    // FIXME: 0.43 - remove this deprecated property
    private static final String DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.grpc.netty.temporaryDefaultMaxInboundMessageSize";
    // The built-in per-role default, overridable by the client/server default properties using the same sign
    // convention as the builder API (see forMaxInboundMessageSize). Seeds GrpcConfig.Builder, so the properties are
    // parsed in exactly one place.
    static final int DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE;
    static final int DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE;

    static {
        final Integer legacy = parseDefaultOverride(DEFAULT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, true);
        final Integer client = parseDefaultOverride(DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, false);
        final Integer server = parseDefaultOverride(DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE_PROPERTY, false);
        DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE = client != null ? client :
                legacy != null ? legacy : Role.CLIENT.defaultMaxInboundMessageSize;
        DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE = server != null ? server :
                legacy != null ? legacy : Role.SERVER.defaultMaxInboundMessageSize;
    }

    private enum Mode { DISABLED, ENFORCING, WARN_ONLY }

    /**
     * Whether a limiter belongs to a client or a server. Selects the built-in default limit and the wording of the
     * warn-only log message.
     */
    enum Role {
        CLIENT(DEFAULT_CLIENT_MAX_MESSAGE_SIZE, "client"),
        SERVER(DEFAULT_SERVER_MAX_MESSAGE_SIZE, "server");

        // Negative => warn-only (rate-limited) at this magnitude by default rather than rejecting.
        final int defaultMaxInboundMessageSize;
        final String description;

        Role(final int defaultWarnSize, final String description) {
            this.defaultMaxInboundMessageSize = -defaultWarnSize;
            this.description = description;
        }
    }

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
    // The role whose warn-only wording to emit; null when not warn-only.
    @Nullable
    private final Role role;
    @Nullable
    private final Throwable constructionSite;

    private GrpcMessageSizeLimiter(final Mode mode, final int maxMessageSize, @Nullable final Role role,
                                   @Nullable final Object owner) {
        this.mode = mode;
        this.maxMessageSize = maxMessageSize;
        final boolean warnOnly = mode == Mode.WARN_ONLY;
        // Seed in the past so the first time the limit is exceeded a warning is emitted immediately.
        this.lastWarnNanos = warnOnly ? new AtomicLong(nanoTime() - WARN_INTERVAL_NANOS) : null;
        this.maxObservedSize = warnOnly ? new AtomicLong() : null;
        this.owner = warnOnly ? owner : null;
        this.role = warnOnly ? role : null;
        this.constructionSite = warnOnly ? new Throwable(
                "This " + role.description + " with a warn-only maxInboundMessageSize created here (not an error)")
                : null;
    }

    /**
     * Build a limiter from the {@code maxInboundMessageSize} value configured on the client/server: {@code 0} disables
     * the limit, {@code > 0} enforces (rejects) at that many bytes, and {@code < 0} warns (without rejecting) at
     * {@code abs(value)} bytes. The sign selects the mode and the magnitude selects the threshold.
     *
     * @param maxInboundMessageSize the configured maximum inbound message size
     * @param role selects the built-in default and the warn-only wording
     * @return a limiter, or {@link #NONE} when {@code maxInboundMessageSize == 0}
     */
    static GrpcMessageSizeLimiter forMaxInboundMessageSize(final int maxInboundMessageSize, final Role role) {
        return forMaxInboundMessageSize(maxInboundMessageSize, role, null);
    }

    /**
     * Variant of {@link #forMaxInboundMessageSize(int, Role)} that records {@code owner} to identify the client/server
     * in the warn-only log. The client passes its {@link io.servicetalk.http.api.StreamingHttpClient} (whose
     * {@code toString()} carries the target address); the server has no equivalent handle and passes {@code null},
     * relying on the construction stack instead.
     */
    static GrpcMessageSizeLimiter forMaxInboundMessageSize(final int maxInboundMessageSize, final Role role,
                                                           @Nullable final Object owner) {
        if (maxInboundMessageSize == 0) {
            return NONE;
        }
        if (maxInboundMessageSize > 0) {
            return new GrpcMessageSizeLimiter(Mode.ENFORCING, maxInboundMessageSize, null, null);
        }
        // -Integer.MIN_VALUE overflows back to a negative value; clamp so it stays a warn-only limiter.
        final int warnThreshold = maxInboundMessageSize == Integer.MIN_VALUE ? Integer.MAX_VALUE :
                -maxInboundMessageSize;
        return new GrpcMessageSizeLimiter(Mode.WARN_ONLY, warnThreshold, role, owner);
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

    // Don't throw from the static initializer; ignore an invalid value and fall back to the per-role defaults.
    @Nullable
    private static Integer parseDefaultOverride(final String name, final boolean legacy) {
        final String raw = System.getProperty(name);
        if (raw == null) {
            return null;
        }
        try {
            final Integer value = Integer.valueOf(raw.trim());
            if (legacy) {
                LOGGER.warn("-D{}={} is a deprecated legacy property, superseded by -D{} and -D{}, and will be " +
                        "removed in a future release; use those or set maxInboundMessageSize(int) per " +
                        "client/server instead.",
                        name, value, DEFAULT_CLIENT_MAX_INBOUND_MESSAGE_SIZE_PROPERTY,
                        DEFAULT_SERVER_MAX_INBOUND_MESSAGE_SIZE_PROPERTY);
            } else {
                LOGGER.debug("-D{}={}", name, value);
            }
            return value;
        } catch (NumberFormatException e) {
            LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: not a valid integer; ignoring it and using the built-in " +
                    "per-client/server defaults.", name, raw);
            return null;
        }
    }

    private void maybeWarn(final long messageSize) {
        assert lastWarnNanos != null;
        assert maxObservedSize != null;
        assert constructionSite != null;
        assert role != null;
        final long maxObserved = maxObservedSize.accumulateAndGet(messageSize, Math::max);
        final long now = nanoTime();
        final long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            final String forOwner = owner == null ? "" : " for " + owner;
            if (role == CLIENT) {
                LOGGER.warn("gRPC message size={} exceeded the maximum inbound message size of {} bytes{} (largest " +
                        "observed {} bytes), allowed through in warn-only mode. Large messages can cause memory " +
                        "pressure; set maxInboundMessageSize(int) to enforce (reject with RESOURCE_EXHAUSTED) or " +
                        "raise the warn threshold. Rate-limited per client.",
                        messageSize, maxMessageSize, forOwner, maxObserved, constructionSite);
            } else {
                LOGGER.warn("gRPC message size={} exceeded the maximum inbound message size of {} bytes{} (largest " +
                        "observed {} bytes), allowed through in warn-only mode. Large messages can cause memory " +
                        "pressure; set an enforcing maxInboundMessageSize(int) to reject them with " +
                        "RESOURCE_EXHAUSTED (planned to become the default). Rate-limited per server.",
                        messageSize, maxMessageSize, forOwner, maxObserved, constructionSite);
            }
        }
    }
}
