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
package io.servicetalk.serializer.utils;

import io.servicetalk.serializer.api.MaxMessageSizeExceededException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import static java.lang.Integer.getInteger;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Enforces (or warns on) the maximum size of a length-prefixed message for the streaming serializers in this package,
 * and resolves the default limit that the no-arg serializer constructors apply. Created once per serializer and shared
 * across its per-subscribe deframers, so the warn-only rate-limiting state is naturally scoped per serializer. Mirrors
 * {@code maxAggregatedPayloadSize} / {@code AggregatedPayloadSizeLimiter} for HTTP client/server.
 */
final class MessageSizeLimiter {
    /**
     * Magic {@code maxMessageSize} value, and the built-in default: warn (rate-limited) at the default threshold when
     * a message exceeds it, but let it through rather than rejecting. Mirrors HTTP {@code maxAggregatedPayloadSize}
     * warn-only mode. Configure an enforcing (positive) {@code maxMessageSize} for steady-state protection.
     */
    static final int WARN_ONLY = -1;

    static final int DEFAULT_MAX_MESSAGE_SIZE_VALUE = 4 * 1024 * 1024;
    // FIXME: 0.43 - remove this temporary property
    static final String DEFAULT_MAX_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.serializer.utils.temporaryDefaultMaxMessageSize";

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSizeLimiter.class);
    private static final long WARN_INTERVAL_NANOS = MINUTES.toNanos(5);

    /**
     * A limiter that never rejects or warns, regardless of message size (the limit is disabled).
     */
    static final MessageSizeLimiter NONE = new MessageSizeLimiter(0, false);

    // The value the 2-arg (default) serializer constructors resolve to. A value of 0 disables the limit; -1 selects
    // warn-only mode at the default threshold (the built-in default); other negatives are invalid and fall back to
    // warn-only.
    static final int DEFAULT_MAX_MESSAGE_SIZE;

    static {
        // Warn-only by default unless the temporary property overrides it. Mirror the validation applied by
        // forMaxMessageSize: 0 disables, -1 is warn-only, other negatives are invalid. Don't throw from this static
        // initializer - fall back to warn-only so a bad property can't break serializer construction (e.g. the static
        // HttpSerializers instances).
        final int value = getInteger(DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, WARN_ONLY);
        if (value < WARN_ONLY) {
            LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: The value is invalid (expected >= {}). Falling back to " +
                            "warn-only mode at {} bytes.",
                    DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, value, WARN_ONLY, DEFAULT_MAX_MESSAGE_SIZE_VALUE);
            DEFAULT_MAX_MESSAGE_SIZE = WARN_ONLY;
        } else {
            DEFAULT_MAX_MESSAGE_SIZE = value;
            // getInteger can't distinguish "unset" (the warn-only default) from an explicit value; only warn about
            // the temporary property when it is actually set.
            if (System.getProperty(DEFAULT_MAX_MESSAGE_SIZE_PROPERTY) != null) {
                if (value == WARN_ONLY) {
                    LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: Setting this property to -1 (warn-only mode) may " +
                                    "be used temporarily to unblock deployment but exposes the service to the risk " +
                                    "of aggregating unbounded amount of data on the heap. Configure appropriate " +
                                    "value per serializer via the 3-arg FixedLengthStreamingSerializer / " +
                                    "VarIntLengthStreamingSerializer constructor instead.",
                            DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, value);
                } else {
                    LOGGER.warn("-D{}={} This property will be removed in the future release. Configure this value " +
                                    "per serializer via the 3-arg FixedLengthStreamingSerializer / " +
                                    "VarIntLengthStreamingSerializer constructor instead.",
                            DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, value);
                }
            }
        }
    }

    private final int maxMessageSize;
    // Non-null iff this is a warn-only limiter. Holds nanoTime() of the last emitted warning so warnings are
    // rate-limited to one per WARN_INTERVAL_NANOS, shared across all deframers of the owning serializer.
    @Nullable
    private final AtomicLong lastWarnNanos;

    private MessageSizeLimiter(final int maxMessageSize, final boolean warnOnly) {
        this.maxMessageSize = maxMessageSize;
        // Seed in the past so the first exceeded message warns immediately.
        this.lastWarnNanos = warnOnly ? new AtomicLong(nanoTime() - WARN_INTERVAL_NANOS) : null;
    }

    /**
     * Resolve a {@code maxMessageSize} config value into a limiter: {@code 0} disables it, {@code > 0} enforces
     * (rejects) at that size, and {@link #WARN_ONLY -1} warns (without rejecting) at the default threshold. Other
     * negative values are rejected. Warn-only falls back to {@link #DEFAULT_MAX_MESSAGE_SIZE_VALUE} when the resolved
     * default is not positive, so it never silently collapses to "disabled".
     */
    static MessageSizeLimiter forMaxMessageSize(final int maxMessageSize) {
        if (maxMessageSize == 0) {
            return NONE;
        }
        if (maxMessageSize == WARN_ONLY) {
            return new MessageSizeLimiter(
                    DEFAULT_MAX_MESSAGE_SIZE > 0 ? DEFAULT_MAX_MESSAGE_SIZE : DEFAULT_MAX_MESSAGE_SIZE_VALUE, true);
        }
        if (maxMessageSize < 0) {
            throw new IllegalArgumentException("maxMessageSize: " + maxMessageSize +
                    " (expected >= 0, or -1 for warn-only)");
        }
        return new MessageSizeLimiter(maxMessageSize, false);
    }

    /**
     * Check a message's declared length against the limit. In enforcing mode throws once the length exceeds the limit;
     * in warn-only mode emits a rate-limited warning and returns normally so deserialization can continue.
     *
     * @param length the declared message length in bytes
     */
    void checkMessageSize(final int length) {
        if (maxMessageSize <= 0 || length <= maxMessageSize) {
            return;
        }
        if (lastWarnNanos == null) {
            throw new MaxMessageSizeExceededException(
                    "Message-Length " + length + " exceeds maximum " + maxMessageSize);
        }
        maybeWarn(length);
    }

    private void maybeWarn(final int length) {
        assert lastWarnNanos != null;
        final long now = nanoTime();
        final long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            LOGGER.warn("Message-Length {} exceeded the configured maximum of {} bytes, but the limit is configured " +
                    "in warn-only mode so the message is allowed through. Configure an enforcing maxMessageSize to " +
                    "reject oversized messages. This warning is rate-limited to once per 5 minutes.", length,
                    maxMessageSize);
        }
    }
}
